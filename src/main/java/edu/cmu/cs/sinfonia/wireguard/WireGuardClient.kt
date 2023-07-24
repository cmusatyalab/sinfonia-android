package edu.cmu.cs.sinfonia.wireguard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.wireguard.android.service.IWireGuardService
import edu.cmu.cs.sinfonia.SinfoniaService.Companion.WIREGUARD_PACKAGE


class WireGuardClient(private val context: Context) {
    private var mService: IWireGuardService? = null

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

    fun createTunnel(tunnelName: String, parcelableConfig: ParcelableConfig) {
        mService?.createTunnel(tunnelName, parcelableConfig)
    }

    fun destroyTunnel(tunnelName: String) {
        mService?.destroyTunnel(tunnelName)
    }

    fun setTunnelUp(tunnelName: String): Boolean {
        return mService?.setTunnelUp(tunnelName)!!
    }

    fun setTunnelDown(tunnelName: String): Boolean {
        return mService?.setTunnelDown(tunnelName)!!
    }

    fun getTunnnelConfig(tunnelName: String): ParcelableConfig? {
        return mService?.getTunnelConfig(tunnelName)
    }

    companion object {
        private const val TAG = "Sinfonia/WireGuardClient"
        private const val ACTION_BIND_WIREGUARD_SERVICE = "com.wireguard.android.service.IWireGuardService"
    }
}