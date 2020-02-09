package socket

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.*

actual open class Socket(
    private val socket: Int,
    private val writeRequest: MutableList<Int>,
    private val buffer: ByteArray
) : SocketInterface {

    private val pendingSendData = mutableListOf<UByteArray>()

    actual override fun send(data: UByteArray) {
        data.toByteArray().usePinned { pinned ->
            val length = send(socket, pinned.addressOf(0), data.size.toUInt(), 0)
            if (length < 0) {
                val error = errno
                if (error == EAGAIN || error == EWOULDBLOCK) {
                    pendingSendData.add(data)
                    writeRequest.add(socket)
                } else {
                    close()
                    throw IOException("Error in send $error")
                }
            } else if (length < data.size) {
                pendingSendData.add(data.copyOfRange(length, data.size))
                writeRequest.add(socket)
            }
            pinned
        }
    }

    actual override fun sendRemaining() {
        pendingSendData.forEach {
            send(it)
        }
    }

    actual override fun read(): UByteArray? {
        buffer.usePinned { pinned ->
            val length = recv(socket.convert(), pinned.addressOf(0), buffer.size.convert(), 0)
            when {
                length == 0 -> {
                    close()
                    throw SocketClosedException()
                }
                length > 0 -> {
                    return pinned.get().toUByteArray().copyOfRange(0, length)
                }
                else -> {
                    if (errno != EAGAIN && errno != EWOULDBLOCK) {
                        close()
                        throw IOException()
                    } else {
                        return null
                    }
                }
            }
        }
    }

    open fun close() {
        shutdown(socket, SHUT_WR)
        close(socket)
    }

}
