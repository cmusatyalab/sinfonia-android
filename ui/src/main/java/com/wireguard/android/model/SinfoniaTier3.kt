package com.wireguard.android.model

import android.util.Log
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.wireguard.crypto.KeyPair
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import java.net.URL
import java.util.UUID

class SinfoniaTier3 {
    private var tier1Url: URL = URL("https://cmu.findcloudlet.org")
    private var applicationUuid: UUID = appUuid("helloworld")
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
            zeroconf: Boolean = false
    ): List<CloudletDeployment> {
        val deployBase = tier1Url.toString()    // Input type string or URL?
        if (zeroconf) TODO("Zeroconf is not implemented")

        val deploymentKeys = KeyPair()  // TODO: Implement key caching
        val deploymentUrl = "$deployBase/api/v1/deploy/$applicationUuid/${deploymentKeys.publicKey.toBase64()}"

        Log.d(TAG, "post deploymentUrl: $deploymentUrl")

        val client: HttpHandler = OkHttp()
        val response = client(Request(Method.POST, deploymentUrl))

        val statusCode = response.status.code
        val responseBody = response.bodyString()
        Log.d(TAG, "statusCode: $statusCode")
        Log.d(TAG, "responseBody: $responseBody")

        val objectMapper = ObjectMapper()
        val typeref: TypeReference<List<Map<String, Any>>> = object: TypeReference<List<Map<String, Any>>>() {}
        val resultMap: List<Map<String, Any>> = objectMapper.readValue(responseBody, typeref)

        // TODO: What does response look like?

        return resultMap.map { deployment: Map<String, Any> ->
            CloudletDeployment.fromMap(deploymentKeys.privateKey, deployment)
        }
    }

    companion object {
        private const val TAG = "WireGuard/SinfoniaTier3"

        private val ALIASES = mapOf(
                "helloworld" to "00000000-0000-0000-0000-000000000000"
        )
    }
}