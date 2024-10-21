package io.github.davidepianca98.kmqtt
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import io.github.davidepianca98.kmqtt.socket.tls.TLSClientSettings
import io.github.davidepianca98.kmqtt.socket.tls.TLSEngine

internal expect class TLSClientEngine(tlsSettings: TLSClientSettings) : TLSEngine {

    override val isInitFinished: Boolean
    override val bioShouldRetry: Boolean

    override fun write(buffer: CPointer<ByteVar>, length: Int): Int

    override fun read(buffer: CPointer<ByteVar>, length: Int): Int

    override fun bioRead(buffer: CPointer<ByteVar>, length: Int): Int

    override fun bioWrite(buffer: CPointer<ByteVar>, length: Int): Int

    override fun getError(result: Int): Int

    override fun close()
}