package edu.cmu.cs.sinfonia.model

import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import java.lang.StringBuilder
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

    constructor(application: List<String>, keyPair: KeyPair, resp: Map<String, Any>) {
        this.uuid = UUID.fromString(resp["UUID"] as String)
        this.applicationKey = Key.fromBase64(resp["ApplicationKey"] as String)
        this.status = resp["Status"] as String
        this.state = if (status.equals("deployed", ignoreCase = true)) State.DEPLOYED else State.NULL
        this.tunnelConfig = buildTunnelConfig(resp["TunnelConfig"] as Map<String, *>, keyPair, application)
        this.deploymentName = resp["DeploymentName"] as String?
        this.created = resp["Created"] as String?
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CloudletDeployment) return false
        return uuid == other.uuid && tunnelConfig.peers == other.tunnelConfig.peers
    }

    private fun buildTunnelConfig(
            tunnelConfig: Map<String, *>,
            keyPair: KeyPair,
            application: List<String>
    ): Config {
        val configBuilder = Config.Builder()
        val interfaceBuilder = Interface.Builder()
        val peerBuilder = Peer.Builder()

        interfaceBuilder.setKeyPair(keyPair)
                .parseAddresses(toCharSequence(tunnelConfig["address"] as ArrayList<String>))
                .parseDnsServers(toCharSequence(tunnelConfig["dns"] as ArrayList<String>))
                .includeApplications(application)

        peerBuilder.parsePublicKey(tunnelConfig["publicKey"] as String)
                .parseEndpoint(tunnelConfig["endpoint"] as String)
                .parseAllowedIPs(toCharSequence(tunnelConfig["allowedIPs"] as ArrayList<String>))
                .setPersistentKeepalive(30)

        return configBuilder
                .setInterface(interfaceBuilder.build())
                .addPeer(peerBuilder.build())
                .build()
    }

    private fun toCharSequence(arrayList: ArrayList<String>): CharSequence {
        return arrayList.joinTo(StringBuilder(), ", ").toString()
    }

    private fun onDestroy() {
        state = State.DESTROYED
    }

    enum class State {
        DEPLOYED,
        DESTROYED,
        NULL
    }

    companion object {
        private const val TAG = "Sinfonia/CloudletDeployment"
    }
}