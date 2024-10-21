package io.github.davidepianca98.socket.udp


import io.github.davidepianca98.toUByteArray
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey

internal actual class UDPSocket(private val key: SelectionKey) {

    private val buffer = ByteBuffer.allocate(2048)

    actual fun send(data: UByteArray, address: String, port: Int) {
        val socket = key.channel() as DatagramChannel
        buffer.clear()
        buffer.put(data.toByteArray())
        buffer.flip()
        socket.send(buffer, InetSocketAddress(address, port))
    }

    actual fun read(): UDPReadData? {
        val socket = key.channel() as DatagramChannel
        buffer.clear()
        val sourceAddress = socket.receive(buffer)
        return if (sourceAddress != null) {
            buffer.flip()
            val address = sourceAddress as InetSocketAddress
            UDPReadData(buffer.toUByteArray(), address.address.hostAddress, address.port)
        } else {
            null
        }
    }

}
