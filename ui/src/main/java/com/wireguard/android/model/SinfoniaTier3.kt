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

class SinfoniaTier3(
        url: String = "https://cmu.findcloudlet.org",
        applicationName: String = "helloworld",
        zeroconf: Boolean = false,
        app: List<String>? = null
) {
    var tier1Url: URL
        private set
    var _applicationName: String
        private set
    var uuid: UUID
        private set
    var _zeroconf: Boolean
        private set
    var application: List<String>? = app
        private set
    lateinit var deployments: List<CloudletDeployment>
        private set

    init {
        tier1Url = URL(url)
        _applicationName = applicationName
        uuid = ApplicationUUID.ALIASES[_applicationName]!!
        _zeroconf = zeroconf
    }

    fun deploy(): SinfoniaTier3 {
        deployments = sinfoniaDeploy()

        // Pick the best deployment (first returned for now...)
        val deployment = deployments[0]

        Log.v(TAG, "deploymentName: ${deployment.deploymentName}")
        Log.v(TAG, "deploymentInterface: ${deployment.tunnelConfig.`interface`}")
        Log.v(TAG, "deploymentPeer: ${deployment.tunnelConfig.peers[0]}")

        return this
    }

    private fun sinfoniaDeploy(): List<CloudletDeployment> {
        val deployBase = tier1Url.toString()    // Input type string or URL?
        if (_zeroconf) TODO("Zeroconf is not implemented")

        val deploymentKeys = KeyPair()  // TODO: Implement key caching
        val deploymentUrl = "$deployBase/api/v1/deploy/$uuid/${deploymentKeys.publicKey.toBase64()}"

        Log.v(TAG, "post deploymentUrl: $deploymentUrl")

        val client: HttpHandler = OkHttp()
        val request = Request(Method.POST, deploymentUrl)
        val response = client(request)

        val statusCode = response.status.code
        val responseBody = response.bodyString()
        Log.v(TAG, "statusCode: $statusCode")
        Log.v(TAG, "responseBody: $responseBody")

        val objectMapper = ObjectMapper()
        val typeRef: TypeReference<List<Map<String, Any>>> = object: TypeReference<List<Map<String, Any>>>() {}
        val resultMap: List<Map<String, Any>> = objectMapper.readValue(responseBody, typeRef)

        return resultMap.map { deployment: Map<String, Any> ->
            CloudletDeployment.fromMap(deploymentKeys, deployment)
        }
    }

    companion object {
        private const val TAG = "WireGuard/SinfoniaTier3"

        private val ALIASES = mapOf(
                "helloworld" to "00000000-0000-0000-0000-000000000000"
        )
    }
}