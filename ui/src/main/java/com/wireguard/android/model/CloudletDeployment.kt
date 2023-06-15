package com.wireguard.android.model

import android.util.Log
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import java.io.InputStream
import java.net.URL
import java.util.UUID

class CloudletDeployment(
        val applicationUuid: UUID,
        val applicationKey: KeyPair,
        val status: String,
        val tunnelConfig: Config,
        val created: String
) {
    private var tier1Url: URL = URL("https://cmu.findcloudlet.org")
    private var applicationUuid: UUID = appUuid("helloworld")
    private var deploymentName: String = ""

    private fun appUuid(appName: String): UUID {
        val uuid = ALIASES?.get(appName)
        return UUID.fromString(uuid)
    }

    private fun sinfoniaTier3(
            application: Sequence<String>,
            configDebug: Boolean = false,https://www.geeksforgeeks.org/kotlin-abstract-class/
            debug: Boolean = false,
            zeroconf: Boolean = false
    ) {
        TODO("")
    }

    private fun sinfoniaDeploy(
            debug: Boolean = false,
            zerconf: Boolean = false
    ) {
        TODO("Request 1 or more backend deployments to tier 1")
    }

    companion object {
        private const val TAG = "WireGuard/CloudletDeployment"

        fun fromMap(privateKey: KeyPair, resp: Map<String, Any>) : CloudletDeployment {
            val config = Config.parse(resp["TunnelConfig"] as InputStream)  // PrivateKey?

            return CloudletDeployment(
                    UUID.fromString(resp["UUID"] as String),
                    resp["ApplicationKey"] as KeyPair,
                    resp["Status"] as String,
                    config as WireguardConfig,
                    resp["DeploymentName"] as String,
                    resp["created"] as String
            )
        }
    }
}