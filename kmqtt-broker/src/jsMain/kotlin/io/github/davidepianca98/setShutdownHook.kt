package io.github.davidepianca98
import node.process.process

internal actual fun setShutdownHook(hook: () -> Unit) {
    process.on(node.process.ProcessEvent.BEFOREEXIT) {
        hook()
    }
}
