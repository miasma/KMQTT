package io.github.davidepianca98.kmqtt.socket.tls


import io.github.davidepianca98.kmqtt.mqtt.broker.Broker
import io.github.davidepianca98.kmqtt.socket.ServerSocket
import io.github.davidepianca98.kmqtt.socket.ServerSocketLoop
import io.github.davidepianca98.kmqtt.socket.SocketState

internal expect class TLSServerSocket(
    broker: Broker,
    selectCallback: (attachment: Any?, state: SocketState) -> Boolean
) : ServerSocket
