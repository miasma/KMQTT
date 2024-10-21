package io.github.davidepianca98.kmqtt.mqtt.packets.mqtt


import io.github.davidepianca98.kmqtt.mqtt.Subscription
import io.github.davidepianca98.kmqtt.mqtt.packets.MQTTPacket

public abstract class MQTTSubscribe(
    public val packetIdentifier: UInt,
    public val subscriptions: List<Subscription>
) : MQTTPacket
