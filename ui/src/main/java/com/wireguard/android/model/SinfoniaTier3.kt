package com.wireguard.android.model

import android.util.Log
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.wireguard.android.Application
import com.wireguard.android.widget.KeyCache
import okhttp3.OkHttpClient
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import java.net.URL
import java.util.UUID as _UUID

class SinfoniaTier3(
        url: String = "https://cmu.findcloudlet.org",
        applicationName: String = "helloworld",
        zeroconf: Boolean = false,
        application: List<String> = listOf("com.android.chrome")
) {
    private val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .build()
    var tier1Url: URL
        private set
    var applicationName: String
        private set
    var uuid: _UUID
        private set
    var zeroconf: Boolean
        private set
    var application: List<String>
        private set
    var deployments: List<CloudletDeployment>
        private set
    var deployment: CloudletDeployment? = null
        private set

    init {
        this.tier1Url = URL(url)
        this.applicationName = applicationName
        this.uuid = UUID[applicationName]!!
        this.zeroconf = zeroconf
        this.application = application
        this.deployments = listOf()
    }

    fun deploy(): SinfoniaTier3 {
        deployments = sinfoniaDeploy()

        // Pick the best deployment (first returned for now...)
        deployment = if (deployments.isEmpty()) null else deployments[0]

        Log.d(TAG, "deploymentName: ${deployment?.deploymentName}")
        Log.d(TAG, "deploymentInterface: ${deployment?.tunnelConfig?.`interface`}")
        Log.d(TAG, "deploymentPeer: ${deployment?.tunnelConfig?.peers?.get(0)}")

        return this
    }

    private fun sinfoniaDeploy(): List<CloudletDeployment> {
        val deployBase = tier1Url.toString()    // Input type string or URL?
        if (zeroconf) TODO("Zeroconf is not implemented")

        val keyCache = KeyCache(Application.get())
        val deploymentKeys = keyCache.getKeys(uuid)
        val deploymentUrl = "$deployBase/api/v1/deploy/$uuid/${deploymentKeys.publicKey.toBase64()}"

        Log.d(TAG, "post deploymentUrl: $deploymentUrl")

        val client: HttpHandler = OkHttp(okHttpClient)
        val request = Request(Method.POST, deploymentUrl)
        val response = client(request)

        val statusCode = response.status.code
        val responseBody = response.bodyString()

        if (statusCode in 200..299) {
            Log.i(TAG, "Response: $statusCode, $responseBody")
            val objectMapper = ObjectMapper()
            val typeRef: TypeReference<List<Map<String, Any>>> = object : TypeReference<List<Map<String, Any>>>() {}
            val resultMap: List<Map<String, Any>> = objectMapper.readValue(responseBody, typeRef)

            return resultMap.map { deployment: Map<String, Any> ->
                CloudletDeployment(application, deploymentKeys, deployment)
            }
        }
        Log.e(TAG, "Response: $statusCode, $responseBody")

        return listOf()
    }

    companion object {
        private const val TAG = "WireGuard/SinfoniaTier3"

        private val UUID = mapOf(
                "helloworld" to _UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "openrtist" to _UUID.fromString("00000000-0000-0000-0000-000000000001")
        )
    }
}