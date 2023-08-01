package edu.cmu.cs.sinfonia.wireguard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.wireguard.android.service.IWireGuardService
import com.wireguard.config.Config
import edu.cmu.cs.sinfonia.SinfoniaService.Companion.WIREGUARD_PACKAGE


class WireGuardClient(private val context: Context) {
    private var mService: IWireGuardService? = null
    var mTunnels: Map<String, Long> = mutableMapOf()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mService = IWireGuardService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mService = null
        }
    }

    fun bind(): Boolean {
        val intent = Intent(ACTION_BIND_WIREGUARD_SERVICE).setPackage(WIREGUARD_PACKAGE)
        try {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Throwable) {
            Log.e(TAG, "bind fail", e)
            return false
        }
        return true
    }

    fun unbind() {
        context.unbindService(serviceConnection)
    }

    fun refreshTunnels() {
        mService?.refreshTunnels()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun createTunnel(tunnelName: String, config: Config): Boolean {
        val parcelableConfig = ParcelableConfig(config)
        if (mService?.createTunnel(tunnelName, parcelableConfig)!!) {
            mTunnels += tunnelName to System.currentTimeMillis()
            return true
        }
        return false
    }

    fun destroyTunnel(tunnelName: String): Boolean {
        return mService?.destroyTunnel(tunnelName)!!
    }

    fun setTunnelUp(tunnelName: String): Boolean {
        return mService?.setTunnelUp(tunnelName)!!
    }

    fun setTunnelDown(tunnelName: String): Boolean {
        return mService?.setTunnelDown(tunnelName)!!
    }

    fun setTunnelToggle(tunnelName: String): Boolean {
        return mService?.setTunnelToggle(tunnelName)!!
    }

    fun getTunnelConfig(tunnelName: String): Config? {
        val parcelableConfig: ParcelableConfig = mService?.getTunnelConfig(tunnelName) ?: return null
        return parcelableConfig.resolve()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun setTunnelConfig(tunnelName: String, config: Config): Config? {
        val parcelableConfig = ParcelableConfig(config)
        val newParcelableConfig: ParcelableConfig = mService?.setTunnelConfig(tunnelName, parcelableConfig) ?: return null
        return newParcelableConfig.resolve()
    }

    fun cleanup(): Boolean {
        var success = true
        mTunnels.forEach { tunnel ->
            success = success && mService?.destroyTunnel(tunnel.key)!!
        }
        return success
    }

    companion object {
        private const val TAG = "Sinfonia/WireGuardClient"
        private const val ACTION_BIND_WIREGUARD_SERVICE = "com.wireguard.android.service.IWireGuardService"
    }
}