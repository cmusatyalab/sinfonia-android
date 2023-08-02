package edu.cmu.cs.sinfonia.wireguard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import androidx.annotation.RequiresApi
import com.wireguard.android.service.IWireGuardService
import com.wireguard.config.Config
import edu.cmu.cs.sinfonia.SinfoniaService.Companion.WIREGUARD_PACKAGE
import edu.cmu.cs.sinfonia.model.SinfoniaTier3
import edu.cmu.cs.sinfonia.util.TunnelException


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

    fun rebind() {
        unbind()
        bind()
    }

    fun unbind() {
        context.unbindService(serviceConnection)
    }

    fun refreshTunnels() {
        if (mService == null) rebind()
        mService?.refreshTunnels()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun createTunnel(tunnelName: String, config: Config) {
        if (mService == null) rebind()
        val parcelableConfig = ParcelableConfig(config)
        val throwable: TunnelException? = mService?.createTunnel(tunnelName, parcelableConfig)
        if (throwable != null) throw throwable
        mTunnels += tunnelName to System.currentTimeMillis()
    }

    fun destroyTunnel(tunnelName: String) {
        if (mService == null) rebind()
        val throwable: TunnelException? = mService?.destroyTunnel(tunnelName)
        if (throwable != null) throw throwable
    }

    fun setTunnelUp(tunnelName: String) {
        if (mService == null) rebind()
        val throwable: TunnelException? = mService?.setTunnelUp(tunnelName)
        if (throwable != null) throw throwable
    }

    fun setTunnelDown(tunnelName: String) {
        if (mService == null) rebind()
        val throwable: TunnelException? = mService?.setTunnelDown(tunnelName)
        if (throwable != null) throw throwable
    }

    fun setTunnelToggle(tunnelName: String) {
        if (mService == null) rebind()
        val throwable: TunnelException? = mService?.setTunnelToggle(tunnelName)
        if (throwable != null) throw throwable
    }

    fun getTunnelConfig(tunnelName: String): Config? {
        if (mService == null) rebind()
        val parcelableConfig: ParcelableConfig = mService?.getTunnelConfig(tunnelName) ?: return null
        return parcelableConfig.resolve()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun setTunnelConfig(tunnelName: String, config: Config): Config? {
        if (mService == null) rebind()
        val parcelableConfig = ParcelableConfig(config)
        val newParcelableConfig: ParcelableConfig = mService?.setTunnelConfig(tunnelName, parcelableConfig) ?: return null
        return newParcelableConfig.resolve()
    }

    fun cleanup(): Boolean {
        if (mService == null) rebind()
        var success = true
        mTunnels.forEach { tunnel ->
            val throwable: TunnelException? = mService?.destroyTunnel(tunnel.key)
            if (throwable != null) success = false
        }
        return success
    }

    companion object {
        private const val TAG = "Sinfonia/WireGuardClient"
        private const val ACTION_BIND_WIREGUARD_SERVICE = "com.wireguard.android.service.IWireGuardService"
    }
}