package edu.cmu.cs.sinfonia.model

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import edu.cmu.cs.sinfonia.R
import edu.cmu.cs.sinfonia.util.ErrorMessages
import edu.cmu.cs.sinfonia.util.KeyCache
import okhttp3.OkHttpClient
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import java.net.MalformedURLException
import java.net.URL
import java.util.UUID

class SinfoniaTier3(
        ctx: Context,
        url: String = "https://cmu.findcloudlet.org",
        applicationName: String?,
        uuid: String?,
        zeroconf: Boolean = false,
        application: List<String> = listOf()
) {
    private val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .build()
    private var ctx: Context
    var tier1Url: URL
        private set
    var applicationName: String?
        private set
    var uuid: UUID?
        private set
    var zeroconf: Boolean
        private set
    var application: List<String>
        private set
    var deployments: List<CloudletDeployment>
        private set
    // The actual deployment adopted
    var deployment: CloudletDeployment? = null
        private set

    init {
        this.ctx = ctx
        this.tier1Url = try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw DeployException(DeployException.Reason.INVALID_TIER_ONE_URL)
        }
        this.applicationName = applicationName
        this.uuid = try {
            UUID.fromString(uuid)
        } catch (e: IllegalArgumentException) {
            throw DeployException(DeployException.Reason.INVALID_UUID)
        }
        this.zeroconf = zeroconf
        this.application = application
        this.deployments = listOf()
    }

    fun fetch(): SinfoniaTier3 {
        Log.i(TAG, "fetch")
        deployments = sinfoniaFetch()
        return this
    }

    private fun sinfoniaFetch(): List<CloudletDeployment> {
        Log.i(TAG, "sinfoniaFetch")
        if (uuid == null) return listOf()

        if (zeroconf) TODO("Zeroconf is not implemented")

        val keyCache = KeyCache(ctx)
        val deploymentKeys = keyCache.getKeys(uuid!!)
        val deploymentUrl = "$tier1Url/api/v1/deploy/$uuid/${deploymentKeys.publicKey.toBase64()}"

        Log.d(TAG, "post deploymentUrl: $deploymentUrl")

        val client: HttpHandler = OkHttp(okHttpClient)
        val request = Request(Method.GET, deploymentUrl)
        val response = client(request)

        val statusCode = response.status.code
        val responseBody = response.bodyString()

        if (statusCode in 200..299) {
            Log.d(TAG, "Response: $statusCode, $responseBody")
            val result = castResponse(responseBody)
            return result.map { deployment: Map<String, Any> ->
                CloudletDeployment(application, deploymentKeys, deployment)
            }
        }
        Log.d(TAG, "Response: $statusCode, $responseBody")

        when (statusCode) {
            404 -> throw DeployException(DeployException.Reason.URL_NOT_FOUND)
            500 -> throw DeployException(DeployException.Reason.DEPLOYMENT_NOT_FOUND)
            503 -> throw DeployException(DeployException.Reason.UNAVAILABLE)
        }

        return listOf()
    }

    fun deploy(): SinfoniaTier3 {
        Log.i(TAG, "deploy")
        deployments = sinfoniaDeploy()

        // Pick the best deployment (first returned for now...)
        deployment = if (deployments.isEmpty())
            throw DeployException(DeployException.Reason.DEPLOYMENT_NOT_FOUND) else deployments[0]

        Log.d(TAG, "deploymentName: ${deployment?.deploymentName}")
        Log.d(TAG, "deploymentInterface: ${deployment?.tunnelConfig?.`interface`}")
        Log.d(TAG, "deploymentPeer: ${deployment?.tunnelConfig?.peers?.get(0)}")

        return this
    }

    private fun sinfoniaDeploy(): List<CloudletDeployment> {
        Log.i(TAG, "sinfoniaDeploy")
        if (uuid == null) return listOf()

        if (zeroconf) TODO("Zeroconf is not implemented")

        val keyCache = KeyCache(ctx)
        val deploymentKeys = keyCache.getKeys(uuid!!)
        val deploymentUrl = "$tier1Url/api/v1/deploy/$uuid/${deploymentKeys.publicKey.toBase64()}"

        Log.d(TAG, "post deploymentUrl: $deploymentUrl")

        val client: HttpHandler = OkHttp(okHttpClient)
        val request = Request(Method.POST, deploymentUrl)
        val response = client(request)

        val statusCode = response.status.code
        val responseBody = response.bodyString()

        if (statusCode in 200..299) {
            Log.d(TAG, "Response: $statusCode, $responseBody")
            val result = castResponse(responseBody)
            return result.map { deployment: Map<String, Any> ->
                CloudletDeployment(application, deploymentKeys, deployment)
            }
        }
        Log.d(TAG, "Response: $statusCode, $responseBody")

        when (statusCode) {
            404 -> throw DeployException(DeployException.Reason.URL_NOT_FOUND)
            500 -> throw DeployException(DeployException.Reason.DEPLOYMENT_NOT_FOUND)
            503 -> throw DeployException(DeployException.Reason.UNAVAILABLE)
        }

        return listOf()
    }

    private fun castResponse(responseBody: String): List<Map<String, Any>> {
        val objectMapper = ObjectMapper()
        return objectMapper.readValue(responseBody, TYPE_REFERENCE)
    }

    class DeployException(private val reason: Reason, vararg format: Any?) : Exception(), Parcelable {
        private val formatArray: Array<out Any?> = format

        constructor(parcel: Parcel) : this(
            Reason.valueOf(parcel.readString() ?: Reason.UNKNOWN.name),
            *parcel.readArray(ClassLoader.getSystemClassLoader()) as Array<out Any?>
        )

        fun getFormat(): Array<out Any?> {
            return formatArray
        }

        fun getReason(): Reason {
            return reason
        }

        enum class Reason {
            UNKNOWN,
            UNAVAILABLE,
            URL_NOT_FOUND,
            INVALID_TIER_ONE_URL,
            INVALID_UUID,
            CANNOT_CAST_RESPONSE,
            DEPLOYMENT_NOT_FOUND
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(reason.name)
            parcel.writeArray(formatArray)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<DeployException> {
            override fun createFromParcel(parcel: Parcel): DeployException {
                return DeployException(parcel)
            }

            override fun newArray(size: Int): Array<DeployException?> {
                return arrayOfNulls(size)
            }
        }
    }

    companion object {
        private const val TAG = "Sinfonia/SinfoniaTier3"
        private val TYPE_REFERENCE: TypeReference<List<Map<String, Any>>> = object : TypeReference<List<Map<String, Any>>>() {}
    }
}