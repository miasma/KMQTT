package io.github.davidepianca98.kmqtt.socket.tls


internal expect class TLSServerContext(tlsSettings: TLSSettings) {
    fun close()
}