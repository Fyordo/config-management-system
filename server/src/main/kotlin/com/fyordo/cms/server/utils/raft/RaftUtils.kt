package com.fyordo.cms.server.utils.raft

import com.fyordo.cms.server.dto.raft.PeerConfig
import mu.KotlinLogging
import org.apache.ratis.protocol.RaftPeer
import org.apache.ratis.protocol.RaftPeerId
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger {}
private const val PEERS_PARTS_DELIMITER = ':'

fun parsePeers(
    peerConfig: String,
    currentNodeId: String,
    currentNodeHost: String,
    currentNodePort: Int,
): RaftPeer? {
    if (peerConfig.isBlank()) {
        return null
    }

    val parts = peerConfig.split(PEERS_PARTS_DELIMITER)
    if (parts.size != 4) {
        logger.warn { "Invalid peer config size, must be 3 parts, but was ${parts.size}" }
        return null
    }

    val peerIdRaw = parts[0]
    val peerHost = parts[1]
    val peerPort = parts[2].toIntOrNull()

    if (peerPort == null) {
        logger.warn { "Invalid RAFT peer port in config entry: '$peerConfig'" }
        return null
    }

    if (peerIdRaw == currentNodeId) {
        logger.info { "Skipping self RAFT peer entry from configuration: '$peerConfig'" }
        return null
    }

    val peerId = RaftPeerId.valueOf(peerIdRaw)
    val peerAddress = InetSocketAddress(peerHost, peerPort)
    return RaftPeer.newBuilder()
        .setId(peerId)
        .setAddress(peerAddress)
        .build()
}

fun parsePeerHosts(
    peers: List<String>,
    currentNodeId: String,
    currentNodeHost: String,
    currentNodeApiPort: Int,
): List<PeerConfig> {
    val result = mutableListOf<PeerConfig>()
    val seen = mutableSetOf<String>()

    result.add(PeerConfig(
        currentNodeId,
        currentNodeHost,
        currentNodeApiPort
    ))
    seen.add(currentNodeId)

    peers.forEach { peerConfig ->
        if (peerConfig.isBlank()) return@forEach
        val parts = peerConfig.split(PEERS_PARTS_DELIMITER)
        if (parts.size != 4) return@forEach
        val nodeId = parts[0]
        val host = parts[1]
        val apiPort = parts[3].toInt()
        if (nodeId !in seen) {
            seen.add(nodeId)
            result.add(PeerConfig(
                nodeId,
                host,
                apiPort
            ))
        }
    }
    return result
}