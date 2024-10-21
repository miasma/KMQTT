package io.github.davidepianca98.mqtt.packets.mqttv4


import io.github.davidepianca98.currentTimeMillis
import io.github.davidepianca98.mqtt.MQTTException
import io.github.davidepianca98.mqtt.containsWildcard
import io.github.davidepianca98.mqtt.packets.MQTTControlPacketType
import io.github.davidepianca98.mqtt.packets.MQTTDeserializer
import io.github.davidepianca98.mqtt.packets.Qos
import io.github.davidepianca98.mqtt.packets.mqtt.MQTTPublish
import io.github.davidepianca98.mqtt.packets.mqttv5.ReasonCode
import io.github.davidepianca98.socket.streams.ByteArrayInputStream
import io.github.davidepianca98.socket.streams.ByteArrayOutputStream

public class MQTT4Publish(
    retain: Boolean,
    qos: Qos = Qos.AT_MOST_ONCE,
    dup: Boolean = false,
    topicName: String,
    packetId: UInt?,
    payload: UByteArray? = null,
    timestamp: Long = currentTimeMillis()
) : MQTTPublish(retain, qos, dup, topicName, packetId, payload, timestamp), MQTT4Packet {

    public companion object : MQTTDeserializer {

        override fun fromByteArray(flags: Int, data: UByteArray): MQTT4Publish {
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

            val payload = inStream.readRemaining()

            return MQTT4Publish(
                retain,
                qos,
                dup,
                topicName,
                packetIdentifier,
                payload
            )
        }

        private fun getQos(flags: Int): Qos? =
            Qos.valueOf(flags.flagsBit(1) or (flags.flagsBit(2) shl 1))

        override fun checkFlags(flags: Int) {

        }
    }

    override fun setDuplicate(): MQTTPublish {
        return MQTT4Publish(
            retain,
            qos,
            true,
            topicName,
            packetId,
            payload,
            timestamp
        )
    }

    override fun toByteArray(): UByteArray {
        val outStream = ByteArrayOutputStream()

        outStream.writeUTF8String(topicName)
        if (qos == Qos.AT_LEAST_ONCE || qos == Qos.EXACTLY_ONCE) {
            outStream.write2BytesInt(packetId!!)
        }
        payload?.let { outStream.write(it) }

        val flags = (((if (dup) 1 else 0) shl 3) and 0x8) or
                ((qos.value shl 1) and 0x6) or
                ((if (retain) 1 else 0) and 0x1)
        return outStream.wrapWithFixedHeader(MQTTControlPacketType.PUBLISH, flags)
    }
}
