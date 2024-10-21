package io.github.davidepianca98.kmqtt.mqtt


import io.github.davidepianca98.kmqtt.mqtt.packets.mqttv5.ReasonCode

public class MQTTException(public val reasonCode: ReasonCode) : Exception(reasonCode.toString())
