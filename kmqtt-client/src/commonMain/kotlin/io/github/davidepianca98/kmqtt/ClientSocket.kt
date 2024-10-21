package io.github.davidepianca98.kmqtt
import io.github.davidepianca98.kmqtt.socket.tcp.Socket

public expect class ClientSocket(
    address: String,
    port: Int,
    maximumPacketSize: Int,
    readTimeOut: Int,
    connectTimeOut: Int,
    checkCallback: () -> Unit
) : Socket
