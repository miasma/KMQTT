package io.github.davidepianca98.kmqtt.socket.tcp


import io.github.davidepianca98.kmqtt.socket.SocketInterface

public expect open class Socket : SocketInterface {

    override fun send(data: UByteArray)

    override fun sendRemaining()

    override fun read(): UByteArray?

    override fun close()
}