package io.github.davidepianca98.kmqtt.mqtt.packets.mqttv5


import io.github.davidepianca98.kmqtt.mqtt.MQTTException
import io.github.davidepianca98.kmqtt.mqtt.packets.MQTTControlPacketType
import io.github.davidepianca98.kmqtt.mqtt.packets.MQTTDeserializer
import io.github.davidepianca98.kmqtt.mqtt.packets.mqtt.MQTTPingresp
import io.github.davidepianca98.kmqtt.socket.streams.ByteArrayOutputStream

public class MQTT5Pingresp : MQTTPingresp(), MQTT5Packet {

    override fun toByteArray(): UByteArray {
        return ByteArrayOutputStream().wrapWithFixedHeader(MQTTControlPacketType.PINGRESP, 0)
    }

    public companion object : MQTTDeserializer {

        override fun fromByteArray(flags: Int, data: UByteArray): MQTT5Pingresp {
            checkFlags(flags)
            if (data.isNotEmpty())
                throw MQTTException(ReasonCode.MALFORMED_PACKET)
            return MQTT5Pingresp()
        }
    }
}
