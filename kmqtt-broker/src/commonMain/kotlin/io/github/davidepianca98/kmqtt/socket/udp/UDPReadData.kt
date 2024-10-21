package io.github.davidepianca98.kmqtt.socket.udp


internal data class UDPReadData(val data: UByteArray, val sourceAddress: String, val sourcePort: Int)
