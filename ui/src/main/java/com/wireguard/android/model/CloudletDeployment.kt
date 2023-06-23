package com.wireguard.android.model

import android.util.Log
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import java.util.ArrayList
import java.util.UUID

class CloudletDeployment(
        val uuid: UUID,
        val applicationKey: Key,
        val status: String,
        val tunnelConfig: Config,
        val deploymentName: String?,
        val created: String?
) {

    companion object {
        private const val TAG = "WireGuard/CloudletDeployment"

        fun fromMap(privateKey: KeyPair, resp: Map<String, Any>) : CloudletDeployment {
            val configBuilder = Config.Builder()
            val interfaceBuilder = Interface.Builder()
            val peerBuilder = Peer.Builder()

            val tunnelConfig = resp["TunnelConfig"] as Map<String, *>
            interfaceBuilder.setKeyPair(privateKey)
                    .parseAddresses(tunnelConfig["address"] as ArrayList<String>)
                    .parseDnsServers(tunnelConfig["dns"] as ArrayList<String>)

            peerBuilder.parsePublicKey(tunnelConfig["publicKey"] as String)
                    .parseEndpoint(tunnelConfig["endpoint"] as String)
                    .parseAllowedIPs(tunnelConfig["allowedIPs"] as ArrayList<String>)
                    .setPersistentKeepalive(30)

            val config = configBuilder
                    .setInterface(interfaceBuilder.build())
                    .addPeer(peerBuilder.build())
                    .build()

            Log.d(TAG, "TunnelConfig: $tunnelConfig")

            return CloudletDeployment(
                    UUID.fromString(resp["UUID"] as String),
                    Key.fromBase64(resp["ApplicationKey"] as String),
                    resp["Status"] as String,
                    config as Config,
                    resp["DeploymentName"] as String?,
                    resp["created"] as String?
            )
        }
    }
}