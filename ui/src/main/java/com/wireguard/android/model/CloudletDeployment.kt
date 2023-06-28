package com.wireguard.android.model

import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import java.util.ArrayList
import java.util.UUID

class CloudletDeployment {
    var uuid: UUID private set
    var applicationKey: Key private set
    var status: String private set
    var tunnelConfig: Config private set
    var deploymentName: String? = null
        private set
    var created: String? = null
        private set


    var state: State

    constructor(
            uuid: UUID,
            applicationKey: Key,
            status: String,
            tunnelConfig: Config,
            deploymentName: String?,
            created: String?
    ) {
        this.uuid = uuid
        this.applicationKey = applicationKey
        this.status = status
        this.state = if (status.equals("deployed", ignoreCase = true)) State.DEPLOYED else State.NULL
        this.tunnelConfig = tunnelConfig
        this.deploymentName = deploymentName
        this.created = created
    }

    constructor(application: List<String>, privateKey: KeyPair, resp: Map<String, Any>) {
        this.uuid = UUID.fromString(resp["UUID"] as String)
        this.applicationKey = Key.fromBase64(resp["ApplicationKey"] as String)
        this.status = resp["Status"] as String
        this.state = if (status.equals("deployed", ignoreCase = true)) State.DEPLOYED else State.NULL

        val configBuilder = Config.Builder()
        val interfaceBuilder = Interface.Builder()
        val peerBuilder = Peer.Builder()

        val tunnelConfig = resp["TunnelConfig"] as Map<String, *>
        interfaceBuilder.setKeyPair(privateKey)
                .parseAddresses(tunnelConfig["address"] as ArrayList<String>)
                .parseDnsServers(tunnelConfig["dns"] as ArrayList<String>)
                .includeApplications(application)

        peerBuilder.parsePublicKey(tunnelConfig["publicKey"] as String)
                .parseEndpoint(tunnelConfig["endpoint"] as String)
                .parseAllowedIPs(tunnelConfig["allowedIPs"] as ArrayList<String>)
                .setPersistentKeepalive(30)

        val config = configBuilder
                .setInterface(interfaceBuilder.build())
                .addPeer(peerBuilder.build())
                .build()

        this.tunnelConfig = config
        this.deploymentName = resp["DeploymentName"] as String?
        this.created = resp["Created"] as String?
    }

    enum class State {
        DEPLOYED,
        LAUNCHED,
        DESTROYED,
        NULL
    }

    companion object {
        private const val TAG = "WireGuard/CloudletDeployment"
    }
}