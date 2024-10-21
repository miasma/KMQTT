package io.github.davidepianca98.kmqtt.mqtt.packets


import io.github.davidepianca98.kmqtt.mqtt.packets.mqttv5.MQTTSerializer

public interface MQTTPacket : MQTTSerializer {

    public fun resizeIfTooBig(maximumPacketSize: UInt): Boolean = true
}
