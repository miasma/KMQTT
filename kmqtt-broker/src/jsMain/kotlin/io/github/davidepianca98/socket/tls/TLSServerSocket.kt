package io.github.davidepianca98.socket.tls


import io.github.davidepianca98.mqtt.broker.Broker
import io.github.davidepianca98.mqtt.broker.ClientConnection
import node.fs.ReadFileSyncBufferOptions
import node.net.Socket
import io.github.davidepianca98.socket.ServerSocket
import io.github.davidepianca98.socket.SocketState
import io.github.davidepianca98.socket.tcp.WebSocket
import io.github.davidepianca98.socket.tls.createServer

internal actual class TLSServerSocket actual constructor(
    private val broker: Broker,
    private val selectCallback: (attachment: Any?, state: SocketState) -> Boolean
) : ServerSocket(broker, selectCallback) {

    private fun TlsOptions(): TlsOptions = js("{}") as TlsOptions
    private fun ReadFileOptions(): ReadFileSyncBufferOptions = js("{}") as ReadFileSyncBufferOptions

    private val tlsOptions = TlsOptions().apply {
        pfx = node.fs.readFileSync(broker.tlsSettings!!.keyStoreFilePath, ReadFileOptions())
        passphrase = broker.tlsSettings.keyStorePassword
        requestCert = broker.tlsSettings.requireClientCertificate
    }

    override val mqttSocket = createServer(tlsOptions) { socket: Socket ->
        val localSocket = createSocket(socket)
        val connection = ClientConnection(localSocket, broker)
        clients[socket.socketId()] = Pair(socket, connection)
        localSocket.setAttachment(connection)

        onConnect(socket)
    }
    override val mqttWebSocket = createServer(tlsOptions) { socket: Socket ->
        val localSocket = createSocket(socket)
        val connection = ClientConnection(WebSocket(localSocket), broker)
        clients[socket.socketId()] = Pair(socket, connection)
        localSocket.setAttachment(connection)

        onConnect(socket)
    }

    init {
        mqttSocket.listen(broker.port, broker.host) {
            doLater()
        }

        if (broker.enableUdp) {
            TODO("UDP in JS not yet implemented")
        }

        if (broker.webSocketPort != null) {
            mqttWebSocket.listen(broker.webSocketPort, broker.host)
        }

        if (broker.cluster != null) {
            TODO("Cluster in JS not yet implemented")
        }
    }

    private fun node.net.Socket.socketId(): String = "$remoteAddress:$remotePort"

    override fun createSocket(socket: node.net.Socket): TLSSocket {
        return TLSSocket(socket, selectCallback)
    }
}
