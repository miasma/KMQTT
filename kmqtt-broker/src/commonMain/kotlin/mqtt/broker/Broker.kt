package mqtt.broker

import currentTimeMillis
import datastructures.Trie
import mqtt.MQTTException
import mqtt.Subscription
import mqtt.broker.cluster.*
import mqtt.broker.interfaces.*
import mqtt.matchesWildcard
import mqtt.packets.Qos
import mqtt.packets.mqttv5.MQTT5Properties
import mqtt.packets.mqttv5.MQTT5Publish
import mqtt.packets.mqttv5.ReasonCode
import removeIf
import socket.ServerSocketLoop
import socket.tls.TLSSettings

class Broker(
    val port: Int = 1883,
    val host: String = "0.0.0.0",
    val backlog: Int = 100000,
    val tlsSettings: TLSSettings? = null,
    val authentication: Authentication? = null,
    val enhancedAuthenticationProviders: Map<String, EnhancedAuthenticationProvider> = mapOf(),
    val authorization: Authorization? = null,
    val connectionCallbacks: ConnectionCallbacks? = null,
    val savePassword: Boolean = false,
    val maximumSessionExpiryInterval: UInt = 0xFFFFFFFFu,
    val receiveMaximum: UShort? = 1024u,
    val maximumQos: Qos? = null,
    val retainedAvailable: Boolean = true,
    val maximumPacketSize: UInt = 32768u,
    val maximumTopicAlias: Int? = null,
    val wildcardSubscriptionAvailable: Boolean = true,
    val subscriptionIdentifiersAvailable: Boolean = true,
    val sharedSubscriptionsAvailable: Boolean = true,
    val serverKeepAlive: Int? = null,
    val responseInformation: String? = null,
    val packetInterceptor: PacketInterceptor? = null,
    val bytesMetrics: BytesMetrics? = null,
    val persistence: Persistence? = null,
    val cluster: ClusterSettings? = null,
    val enableUdp: Boolean = false,
    val webSocketPort: Int? = null
) {

    private val server = ServerSocketLoop(this)
    private val sessions = (persistence?.getAllSessions() as Map<String, ISession>?)?.toMutableMap() ?: mutableMapOf()
    private val subscriptions = Trie(persistence?.getAllSubscriptions())
    private val retainedList = persistence?.getAllRetainedMessages()?.toMutableMap() ?: mutableMapOf()

    private val clusterConnections = mutableMapOf<String, ClusterConnection>()

    init {
        if (enableUdp && maximumPacketSize > 65535u) {
            throw IllegalArgumentException("When UDP is enabled the maximum packet size can't be bigger than the datagram maximum size")
        }
    }

    /**
     * Starts the broker (blocking run)
     */
    fun listen() {
        server.run()
    }

    /**
     * Run a single iteration of the broker (non blocking run)
     */
    fun step() {
        server.step()
    }

    internal fun sendWill(session: Session?) {
        val will = session?.will ?: return
        val properties = MQTT5Properties()
        properties.payloadFormatIndicator = will.payloadFormatIndicator
        properties.messageExpiryInterval = will.messageExpiryInterval
        properties.contentType = will.contentType
        properties.responseTopic = will.responseTopic
        properties.correlationData = will.correlationData
        properties.userProperty += will.userProperty
        publish(session.clientId, will.retain, will.topic, will.qos, properties, will.payload)
        // The will must be removed after sending
        session.will = null
    }

    private fun publishNormal(
        publisherClientId: String,
        retain: Boolean,
        topicName: String,
        qos: Qos,
        dup: Boolean,
        properties: MQTT5Properties?,
        payload: UByteArray?,
        session: ISession,
        subscription: Subscription,
        matchingSubscriptions: List<Subscription>
    ) {
        if (subscription.options.noLocal && publisherClientId == session.clientId) {
            return
        }

        properties?.subscriptionIdentifier?.clear()
        matchingSubscriptions.forEach { sub ->
            sub.subscriptionIdentifier?.let {
                properties?.subscriptionIdentifier?.add(it)
            }
        }

        session.publish(
            retain,
            topicName,
            Qos.min(subscription.options.qos, qos),
            dup,
            properties,
            payload
        )
    }

    internal fun publish(
        publisherClientId: String,
        retain: Boolean,
        topicName: String,
        qos: Qos,
        dup: Boolean,
        properties: MQTT5Properties?,
        payload: UByteArray?,
        remote: Boolean = false
    ) {
        if (!retainedAvailable && retain) {
            throw MQTTException(ReasonCode.RETAIN_NOT_SUPPORTED)
        }
        maximumQos?.let {
            if (qos > it) {
                throw MQTTException(ReasonCode.QOS_NOT_SUPPORTED)
            }
        }

        val matchedSubscriptions = subscriptions.match(topicName)

        if (sharedSubscriptionsAvailable) {
            matchedSubscriptions.filter { it.value.isShared() }.groupBy { it.value.shareName }.forEach {
                val shareName = it.key
                if (shareName != null) {
                    val subscriptionEntry =
                        it.value.minByOrNull { subscription -> subscription.value.timestampShareSent }!!
                    val clientId = subscriptionEntry.key
                    val subscription = subscriptionEntry.value
                    val session = sessions[clientId]

                    if (session != null) {
                        publishNormal(
                            publisherClientId,
                            retain,
                            topicName,
                            qos,
                            dup,
                            properties,
                            payload,
                            session,
                            subscription,
                            matchedSubscriptions.filter { sub -> sub.key == clientId && sub.value.isShared() }
                                .map { sub -> sub.value }
                        )
                        subscription.timestampShareSent = currentTimeMillis()
                    }
                }
            }
        }

        val doneNormal = mutableListOf<String>()
        matchedSubscriptions.filter { !it.value.isShared() }.forEach { subscriptionEntry ->
            val clientId = subscriptionEntry.key
            val subscription = subscriptionEntry.value

            val session = sessions[clientId]
            if (!remote || session !is RemoteSession) {
                if (clientId !in doneNormal && session != null) {
                    publishNormal(
                        publisherClientId,
                        retain,
                        topicName,
                        qos,
                        dup,
                        properties,
                        payload,
                        session,
                        subscription,
                        matchedSubscriptions.filter { it.key == clientId && !it.value.isShared() }.map { it.value }
                    )
                    doneNormal += clientId
                }
            }
        }
    }

    private fun publish(
        publisherClientId: String,
        retain: Boolean,
        topicName: String,
        qos: Qos,
        properties: MQTT5Properties?,
        payload: UByteArray?
    ): Boolean {
        if (maximumQos != null && qos > maximumQos) {
            return false
        }
        if (retain) {
            if (!retainedAvailable) {
                return false
            }
            val packet = MQTT5Publish(
                retain,
                qos,
                false,
                topicName,
                null,
                properties ?: MQTT5Properties(),
                payload
            )
            setRetained(topicName, packet, publisherClientId)
        }
        publish(publisherClientId, retain, topicName, qos, false, properties, payload)
        return true
    }

    /**
     * Publish a message directly inside the broker, to be sent to subscribed clients
     * @param retain enable retained flag for the message
     * @param topicName topic name for the message
     * @param qos QoS level for the message
     * @param properties MQTT 5 properties, used only on MQTT 5 clients
     * @param payload content of the message
     * @return true if the publish operation was successful, false otherwise
     */
    fun publish(
        retain: Boolean,
        topicName: String,
        qos: Qos,
        properties: MQTT5Properties?,
        payload: UByteArray?
    ): Boolean {
        return publish("", retain, topicName, qos, properties, payload)
    }

    internal fun publishFromRemote(packet: mqtt.packets.mqtt.MQTTPublish) {
        publish(
            "",
            packet.retain,
            packet.topicName,
            packet.qos,
            packet.dup,
            if (packet is MQTT5Publish) packet.properties else null,
            packet.payload,
            true
        )
    }

    internal fun addSession(clientId: String, session: ISession) {
        sessions[clientId] = session
        if (session is Session) {
            clusterConnections.addSession(session)
        }
    }

    internal fun getSession(clientId: String?): ISession? {
        return sessions[clientId]
    }

    internal fun propagateSession(session: Session) {
        clusterConnections.updateSession(session)
    }

    /**
     * Checks if a specific client is currently connected to the Broker
     * @param clientId clientId of the client to check
     * @return true if the client is connected, false otherwise
     */
    fun isClientConnected(clientId: String): Boolean {
        return sessions[clientId]?.connected ?: false
    }

    internal fun addSubscription(clientId: String, subscription: Subscription, remote: Boolean = false): Boolean {
        val replaced = subscriptions.insert(subscription, clientId)
        persistence?.persistSubscription(clientId, subscription)
        if (!remote) {
            clusterConnections.addSubscription(clientId, subscription)
        }
        return replaced
    }

    internal fun removeSubscription(clientId: String, topicFilter: String, remote: Boolean = false): Boolean {
        return if (subscriptions.delete(topicFilter, clientId)) {
            persistence?.removeSubscription(clientId, topicFilter)
            if (!remote) {
                clusterConnections.removeSubscription(clientId, topicFilter)
            }
            true
        } else {
            false
        }
    }

    internal fun setRetained(topicName: String, message: mqtt.packets.mqtt.MQTTPublish, clientId: String, remote: Boolean = false) {
        if (retainedAvailable) {
            if (message.payload?.isNotEmpty() == true) {
                val retained = Pair(message, clientId)
                retainedList[topicName] = retained
                if (!remote) {
                    clusterConnections.setRetained(retained)
                }
                persistence?.persistRetained(message, clientId)
            } else {
                retainedList.remove(topicName)
                if (!remote) {
                    clusterConnections.setRetained(Pair(message, clientId))
                }
                persistence?.removeRetained(topicName)
            }
        } else {
            throw MQTTException(ReasonCode.RETAIN_NOT_SUPPORTED)
        }
    }

    private fun removeExpiredRetainedMessages() {
        val iterator = retainedList.iterator()
        while (iterator.hasNext()) {
            val retained = iterator.next()
            val message = retained.value.first
            if (message.messageExpiryIntervalExpired()) {
                persistence?.removeRetained(retained.key)
                iterator.remove()
            }
        }
    }

    internal fun getRetained(topicFilter: String): List<Pair<mqtt.packets.mqtt.MQTTPublish, String>> {
        removeExpiredRetainedMessages()
        return retainedList.filter { it.key.matchesWildcard(topicFilter) }.map { it.value }
    }

    internal fun cleanUpOperations() {
        val iterator = sessions.iterator()
        while (iterator.hasNext()) {
            val iSession = iterator.next()
            val session = iSession.value
            if (session.connected) { // Check the keep alive timer is being respected
                session.checkKeepAliveExpired()
            } else {
                if (session.isExpired()) {
                    session.will?.let {
                        sendWill(session as Session)
                    }
                    persistence?.removeSession(iSession.key)
                    subscriptions.delete(iSession.key)
                    persistence?.removeSubscriptions(iSession.key)
                    iterator.remove()
                } else {
                    session.will?.let {
                        val currentTime = currentTimeMillis()
                        val expirationTime =
                            session.sessionDisconnectedTimestamp!! + (it.willDelayInterval.toLong() * 1000L)
                        // Check if the will delay interval has expired, if yes send the will
                        if (expirationTime <= currentTime || it.willDelayInterval == 0u) {
                            sendWill(session as Session)
                        }
                    }
                }
            }
        }
    }

    /**
     * Stops the broker thread
     * @param serverReference new server address available for client connection
     * @param temporarilyMoved true if the current server is going to be restarted soon
     */
    fun stop(serverReference: String? = null, temporarilyMoved: Boolean = false) {
        // TODO close cluster connections and send use another server from that list
        val reasonCode = if (serverReference != null) {
            if (temporarilyMoved) {
                ReasonCode.USE_ANOTHER_SERVER
            } else {
                ReasonCode.SERVER_MOVED
            }
        } else {
            ReasonCode.SERVER_SHUTTING_DOWN
        }
        sessions.filter { it.value.connected && it.value is Session }.forEach {
            (it.value as Session).clientConnection?.disconnect(reasonCode, serverReference)
        }
        server.stop()
    }

    internal fun addClusterConnection(address: String) {
        if (clusterConnections[address] == null) {
            server.addClusterConnection(address)?.let {
                addClusterConnection(address, it)
            }
        }
    }

    internal fun addClusterConnection(address: String, clusterConnection: ClusterConnection) {
        clusterConnections[address] = clusterConnection
    }

    internal fun removeClusterConnection(clusterConnection: ClusterConnection) {
        clusterConnections.removeIf { it.value == clusterConnection }
    }
}
