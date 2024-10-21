package io.github.davidepianca98.mqtt.packets.mqttv5


import io.github.davidepianca98.mqtt.packets.mqttv5.MQTT5Pubrel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MQTTPubrelTest {

    private val array = ubyteArrayOf(0x62u, 0x04u, 0x00u, 0x41u, 0x00u, 0x00u)
    private val packet = MQTT5Pubrel(65u)

    @Test
    fun testToByteArray() {
        assertTrue(array.contentEquals(packet.toByteArray()))
    }

    @Test
    fun testFromByteArray() {
        val result = MQTT5Pubrel.fromByteArray(2, array.copyOfRange(2, array.size))
        assertEquals(packet.packetId, result.packetId)
    }
}
