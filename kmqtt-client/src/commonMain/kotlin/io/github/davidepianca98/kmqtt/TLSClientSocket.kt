package io.github.davidepianca98.kmqtt
import io.github.davidepianca98.kmqtt.socket.tls.TLSClientSettings
import io.github.davidepianca98.kmqtt.socket.tls.TLSSocket

public expect class TLSClientSocket(
    address: String,
    port: Int,
    maximumPacketSize: Int,
    readTimeOut: Int,
    connectTimeOut: Int,
    tlsSettings: TLSClientSettings,
    checkCallback: () -> Unit
) : TLSSocket {

    public val handshakeComplete: Boolean
}