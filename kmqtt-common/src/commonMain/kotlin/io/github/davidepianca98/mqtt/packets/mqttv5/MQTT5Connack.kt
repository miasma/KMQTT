package io.github.davidepianca98.mqtt.packets.mqttv5


import io.github.davidepianca98.mqtt.MQTTException
import io.github.davidepianca98.mqtt.packets.ConnectAcknowledgeFlags
import io.github.davidepianca98.mqtt.packets.MQTTControlPacketType
import io.github.davidepianca98.mqtt.packets.MQTTDeserializer
import io.github.davidepianca98.mqtt.packets.mqtt.MQTTConnack
import io.github.davidepianca98.socket.streams.ByteArrayInputStream
import io.github.davidepianca98.socket.streams.ByteArrayOutputStream

public class MQTT5Connack(
    connectAcknowledgeFlags: ConnectAcknowledgeFlags,
    public val connectReasonCode: ReasonCode,
    public val properties: MQTT5Properties = MQTT5Properties()
) : MQTTConnack(connectAcknowledgeFlags), MQTT5Packet {

    public companion object : MQTTDeserializer {

        private val validProperties = listOf(
            Property.SESSION_EXPIRY_INTERVAL,
            Property.ASSIGNED_CLIENT_IDENTIFIER,
            Property.SERVER_KEEP_ALIVE,
            Property.AUTHENTICATION_METHOD,
            Property.AUTHENTICATION_DATA,
            Property.RESPONSE_INFORMATION,
            Property.SERVER_REFERENCE,
            Property.REASON_STRING,
            Property.RECEIVE_MAXIMUM,
            Property.TOPIC_ALIAS_MAXIMUM,
            Property.MAXIMUM_QOS,
            Property.RETAIN_AVAILABLE,
            Property.USER_PROPERTY,
            Property.MAXIMUM_PACKET_SIZE,
            Property.WILDCARD_SUBSCRIPTION_AVAILABLE,
            Property.SUBSCRIPTION_IDENTIFIER_AVAILABLE,
            Property.SHARED_SUBSCRIPTION_AVAILABLE
        )

        private val validReasonCodes = listOf(
            ReasonCode.SUCCESS,
            ReasonCode.UNSPECIFIED_ERROR,
            ReasonCode.MALFORMED_PACKET,
            ReasonCode.PROTOCOL_ERROR,
            ReasonCode.IMPLEMENTATION_SPECIFIC_ERROR,
            ReasonCode.UNSUPPORTED_PROTOCOL_VERSION,
            ReasonCode.CLIENT_IDENTIFIER_NOT_VALID,
            ReasonCode.BAD_USER_NAME_OR_PASSWORD,
            ReasonCode.NOT_AUTHORIZED,
            ReasonCode.SERVER_UNAVAILABLE,
            ReasonCode.SERVER_BUSY,
            ReasonCode.BANNED,
            ReasonCode.BAD_AUTHENTICATION_METHOD,
            ReasonCode.TOPIC_NAME_INVALID,
            ReasonCode.PACKET_TOO_LARGE,
            ReasonCode.QUOTA_EXCEEDED,
            ReasonCode.PAYLOAD_FORMAT_INVALID,
            ReasonCode.RETAIN_NOT_SUPPORTED,
            ReasonCode.QOS_NOT_SUPPORTED,
            ReasonCode.USE_ANOTHER_SERVER,
            ReasonCode.SERVER_MOVED,
            ReasonCode.CONNECTION_RATE_EXCEEDED
        )

        override fun fromByteArray(flags: Int, data: UByteArray): MQTT5Connack {
            checkFlags(flags)
            val inStream = ByteArrayInputStream(data)

            val connectAcknowledgeFlags = when (inStream.readByte().toInt()) {
                0 -> ConnectAcknowledgeFlags(false)
                1 -> ConnectAcknowledgeFlags(true)
                else -> throw MQTTException(ReasonCode.MALFORMED_PACKET)
            }
            val connectReasonCode =
                ReasonCode.valueOf(inStream.readByte().toInt()) ?: throw MQTTException(
                    ReasonCode.PROTOCOL_ERROR
                )
            val properties = inStream.deserializeProperties(validProperties)

            return MQTT5Connack(
                connectAcknowledgeFlags,
                connectReasonCode,
                properties
            )
        }
    }

    override fun resizeIfTooBig(maximumPacketSize: UInt): Boolean {
        if (size() > maximumPacketSize) {
            properties.reasonString = null
        }
        if (size() > maximumPacketSize) {
            properties.userProperty.clear()
        }
        return size() <= maximumPacketSize
    }

    override fun toByteArray(): UByteArray {
        if (connectReasonCode !in validReasonCodes)
            throw IllegalArgumentException("Invalid reason code")

        val outStream = ByteArrayOutputStream()

        val connectFlags =
            if (connectAcknowledgeFlags.sessionPresentFlag && connectReasonCode == ReasonCode.SUCCESS) 1u else 0u
        outStream.write(connectFlags.toUByte())
        outStream.write(connectReasonCode.value.toUByte())
        outStream.write(properties.serializeProperties(validProperties))

        return outStream.wrapWithFixedHeader(MQTTControlPacketType.CONNACK, 0)
    }
}
