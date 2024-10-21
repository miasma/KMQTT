package io.github.davidepianca98.socket.tcp


internal interface TCPEventHandler {

    fun read(): UByteArray?

    fun dataReceived(data: UByteArray)

    fun sendRemaining()

    fun closedGracefully()

    fun closedWithException()
}
