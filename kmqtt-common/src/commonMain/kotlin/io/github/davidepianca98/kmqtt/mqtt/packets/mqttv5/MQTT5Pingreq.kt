package io.github.davidepianca98.kmqtt.mqtt.packets.mqttv5


import io.github.davidepianca98.kmqtt.mqtt.MQTTException
import io.github.davidepianca98.kmqtt.mqtt.packets.MQTTControlPacketType
import io.github.davidepianca98.kmqtt.mqtt.packets.MQTTDeserializer
import io.github.davidepianca98.kmqtt.mqtt.packets.mqtt.MQTTPingreq
import io.github.davidepianca98.kmqtt.socket.streams.ByteArrayOutputStream

public class MQTT5Pingreq : MQTTPingreq(), MQTT5Packet {

    override fun toByteArray(): UByteArray {
        return ByteArrayOutputStream().wrapWithFixedHeader(MQTTControlPacketType.PINGREQ, 0)
    }

    public companion object : MQTTDeserializer {

        override fun fromByteArray(flags: Int, data: UByteArray): MQTT5Pingreq {
            checkFlags(flags)
            if (data.isNotEmpty())
                throw MQTTException(ReasonCode.MALFORMED_PACKET)
            return MQTT5Pingreq()
        }
    }
}
