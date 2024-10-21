package io.github.davidepianca98.kmqtt.mqtt.packets.mqtt


import io.github.davidepianca98.kmqtt.mqtt.packets.MQTTPacket

public abstract class MQTTPubrec(public val packetId: UInt) : MQTTPacket
