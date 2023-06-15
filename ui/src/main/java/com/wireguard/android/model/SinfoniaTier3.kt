package com.wireguard.android.model

import org.http4k.routing.webJars
import java.net.URL
import java.util.UUID

class SinfoniaTier3 {
    private var tier1Url: URL = URL("https://cmu.findcloudlet.org")
    private var applicationUuid: UUID = appUuid("helloworld")
    private val debug = false
    private val configDebug = false
    private val zeroconf = false
    private lateinit var application: Sequence<String>

    private fun appUuid(appName: String): UUID {
        val uuid = ALIASES?.get(appName)
        return UUID.fromString(uuid)
    }

    private fun sinfoniaDeploy(
            tier1Url: URL,
            applicationUuid: UUID,
            debug: Boolean = false,
            zeroconf: Boolean = false
    ): List<CloudletDeployment> {
        val deployBase = tier1Url.toString()
        if (zeroconf) TODO("Zeroconf is not implemented")

        val deploymentKeys = ""
        val deploymentUrl = URL(
                deployBase + "/api/v1/deploy/" + applicationUuid.toString() + deploymentKeys
        )
    }

    companion object {
        private const val TAG = "WireGuard/SinfoniaTier3"

        private val ALIASES = mapOf(
                "helloworld" to "00000000-0000-0000-0000-000000000000"
        )
    }
}