package io.github.davidepianca98.kmqtt.socket


import io.github.davidepianca98.kmqtt.mqtt.broker.cluster.ClusterConnection

internal interface ServerSocketInterface {

    fun addClusterConnection(address: String): ClusterConnection?
}
