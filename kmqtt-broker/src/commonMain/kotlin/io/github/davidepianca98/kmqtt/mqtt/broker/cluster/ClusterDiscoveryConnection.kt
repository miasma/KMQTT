package io.github.davidepianca98.kmqtt.mqtt.broker.cluster


import kotlinx.serialization.protobuf.ProtoBuf
import io.github.davidepianca98.kmqtt.mqtt.broker.Broker
import io.github.davidepianca98.kmqtt.socket.udp.UDPEventHandler
import io.github.davidepianca98.kmqtt.socket.udp.UDPSocket

internal class ClusterDiscoveryConnection(private val socket: UDPSocket, private val broker: Broker) :
    UDPEventHandler {

    override fun dataReceived() {
        socket.read()?.let { data ->
            val packet = ProtoBuf.decodeFromByteArray(DiscoveryPacket.serializer(), data.data.toByteArray())
            if (packet.name != broker.cluster!!.name) {
                broker.addClusterConnection(data.sourceAddress)
            }
        }
    }

    fun sendDiscovery(port: Int) {
        val packet = ProtoBuf.encodeToByteArray(DiscoveryPacket.serializer(), DiscoveryPacket(broker.cluster!!.name))
            .toUByteArray()
        socket.send(packet, "255.255.255.255", port)
    }
}
