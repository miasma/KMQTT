package io.github.davidepianca98.kmqtt.mqtt.packets.mqttv5


import io.github.davidepianca98.kmqtt.currentTimeMillis
import io.github.davidepianca98.kmqtt.mqtt.MQTTException
import io.github.davidepianca98.kmqtt.mqtt.containsWildcard
import io.github.davidepianca98.kmqtt.mqtt.packets.MQTTControlPacketType
import io.github.davidepianca98.kmqtt.mqtt.packets.MQTTDeserializer
import io.github.davidepianca98.kmqtt.mqtt.packets.Qos
import io.github.davidepianca98.kmqtt.mqtt.packets.mqtt.MQTTPublish
import io.github.davidepianca98.kmqtt.socket.streams.ByteArrayInputStream
import io.github.davidepianca98.kmqtt.socket.streams.ByteArrayOutputStream
import io.github.davidepianca98.kmqtt.validatePayloadFormat


public class MQTT5Publish(
    retain: Boolean,
    qos: Qos = Qos.AT_MOST_ONCE,
    dup: Boolean = false,
    topicName: String,
    packetId: UInt?,
    public val properties: MQTT5Properties = MQTT5Properties(),
    payload: UByteArray? = null,
    timestamp: Long = currentTimeMillis()
) : MQTTPublish(retain, qos, dup, topicName, packetId, payload, timestamp), MQTT5Packet {

    override fun resizeIfTooBig(maximumPacketSize: UInt): Boolean {
        return size() <= maximumPacketSize
    }

    override fun toByteArray(): UByteArray {
        val outStream = ByteArrayOutputStream()

        outStream.writeUTF8String(topicName)
        if (qos == Qos.AT_LEAST_ONCE || qos == Qos.EXACTLY_ONCE) {
            outStream.write2BytesInt(packetId!!)
        }
        outStream.write(properties.serializeProperties(validProperties))
        payload?.let { outStream.write(it) }

        val flags = (((if (dup) 1 else 0) shl 3) and 0x8) or
                ((qos.value shl 1) and 0x6) or
                ((if (retain) 1 else 0) and 0x1)
        return outStream.wrapWithFixedHeader(MQTTControlPacketType.PUBLISH, flags)
    }

    public fun validatePayloadFormat(): Boolean {
        properties.payloadFormatIndicator?.let {
            return payload?.validatePayloadFormat(it) ?: true
        }
        return true
    }

    public fun setTopicFromAlias(topicName: String): MQTT5Publish {
        return MQTT5Publish(
            retain,
            qos,
            dup,
            topicName,
            packetId,
            properties.apply { topicAlias = null },
            payload,
            timestamp
        )
    }

    override fun setDuplicate(): MQTT5Publish {
        return MQTT5Publish(
            retain,
            qos,
            true,
            topicName,
            packetId,
            properties,
            payload,
            timestamp
        )
    }

    override fun messageExpiryIntervalExpired(): Boolean {
        val expiry = properties.messageExpiryInterval?.toLong() ?: ((Long.MAX_VALUE / 1000) - timestamp)
        return ((expiry * 1000) + timestamp) < currentTimeMillis()
    }

    override fun updateMessageExpiryInterval() {
        properties.messageExpiryInterval?.let {
            properties.messageExpiryInterval =
                it - ((currentTimeMillis() - timestamp) / 1000).toUInt()
        }
    }

    public companion object : MQTTDeserializer {

        private val validProperties = listOf(
            Property.PAYLOAD_FORMAT_INDICATOR,
            Property.MESSAGE_EXPIRY_INTERVAL,
            Property.CONTENT_TYPE,
            Property.RESPONSE_TOPIC,
            Property.CORRELATION_DATA,
            Property.SUBSCRIPTION_IDENTIFIER,
            Property.TOPIC_ALIAS,
            Property.USER_PROPERTY
        )

        override fun fromByteArray(flags: Int, data: UByteArray): MQTT5Publish {
            checkFlags(flags)
            val retain = flags.flagsBit(0) == 1
            val qos = getQos(flags) ?: throw MQTTException(ReasonCode.MALFORMED_PACKET)
            val dup = flags.flagsBit(3) == 1

            if (qos == Qos.AT_MOST_ONCE && dup)
                throw MQTTException(ReasonCode.MALFORMED_PACKET)

            val inStream = ByteArrayInputStream(data)
            val topicName = inStream.readUTF8String()
            if (topicName.containsWildcard())
                throw MQTTException(ReasonCode.TOPIC_NAME_INVALID)

            val packetIdentifier = if (qos > Qos.AT_MOST_ONCE) inStream.read2BytesInt() else null

            val properties = inStream.deserializeProperties(validProperties)

            val payload = inStream.readRemaining()

            return MQTT5Publish(
                retain,
                qos,
                dup,
                topicName,
                packetIdentifier,
                properties,
                payload
            )
        }

        private fun getQos(flags: Int): Qos? =
            Qos.valueOf(flags.flagsBit(1) or (flags.flagsBit(2) shl 1))

        override fun checkFlags(flags: Int) {

        }
    }
}
