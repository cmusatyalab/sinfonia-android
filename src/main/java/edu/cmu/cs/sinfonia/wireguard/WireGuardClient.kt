package edu.cmu.cs.sinfonia.wireguard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import androidx.annotation.RequiresApi
import com.wireguard.android.service.IWireGuardService
import com.wireguard.config.Config
import edu.cmu.cs.sinfonia.SinfoniaService.Companion.WIREGUARD_PACKAGE
import edu.cmu.cs.sinfonia.util.TunnelException


class WireGuardClient(private val context: Context) {
    private var mService: IWireGuardService? = null
    private var mTunnels: MutableList<String> = mutableListOf()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mService = IWireGuardService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mService = null
        }
    }

    fun bind() {
        val intent = Intent(ACTION_BIND_WIREGUARD_SERVICE).setPackage(WIREGUARD_PACKAGE)
        try {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            throw WireGuardException(WireGuardException.Reason.PERMISSION_DENIED)
        } catch (e: Exception) {
            throw WireGuardException(WireGuardException.Reason.UNKNOWN)
        }
    }

    fun unbind() {
        context.unbindService(serviceConnection)
    }

    fun fetchMyTunnels(application: List<String>) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        mTunnels.addAll(mService?.fetchMyTunnels(application.toTypedArray()) ?: arrayOf())
    }

    fun refreshTunnels() {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        mService?.refreshTunnels()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun createTunnel(tunnelName: String, config: Config, overwrite: Boolean) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        val parcelableConfig = ParcelableConfig(config)
        val throwable: TunnelException? = mService?.createTunnel(tunnelName, parcelableConfig, overwrite)
        if (throwable != null) throw throwable
    }

    fun destroyTunnel(tunnelName: String) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        if (!mTunnels.contains(tunnelName)) throw TunnelException(TunnelException.Reason.UNAUTHORIZED_ACCESS)
        val throwable: TunnelException? = mService?.destroyTunnel(tunnelName)
        if (throwable != null) throw throwable
    }

    fun setTunnelUp(tunnelName: String) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        val throwable: TunnelException? = mService?.setTunnelUp(tunnelName)
        if (throwable != null) throw throwable
    }

    fun setTunnelDown(tunnelName: String) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        val throwable: TunnelException? = mService?.setTunnelDown(tunnelName)
        if (throwable != null) throw throwable
    }

    fun setTunnelDownAll() {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        for (tunnel in mTunnels) {
            try {
                setTunnelDown(tunnel)
            } catch (_: Throwable) {}
        }
    }

    fun setTunnelToggle(tunnelName: String) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        val throwable: TunnelException? = mService?.setTunnelToggle(tunnelName)
        if (throwable != null) throw throwable
    }

    fun getTunnelConfig(tunnelName: String): Config? {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        val parcelableConfig: ParcelableConfig = mService?.getTunnelConfig(tunnelName) ?: return null
        return parcelableConfig.resolve()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun setTunnelConfig(tunnelName: String, config: Config): Config? {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        val parcelableConfig = ParcelableConfig(config)
        val newParcelableConfig: ParcelableConfig = mService?.setTunnelConfig(tunnelName, parcelableConfig) ?: return null
        return newParcelableConfig.resolve()
    }

    fun saveTunnel(tunnelName: String) {
        mTunnels.add(tunnelName)
    }

    fun removeTunnel(tunnelName: String) {
        mTunnels.remove(tunnelName)
    }

    fun cleanup(): Boolean {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        var success = true
        for (tunnel in mTunnels) {
            try {
                destroyTunnel(tunnel)
            } catch (_: Throwable) {
                success = false
            }
        }
        if (success) mTunnels.clear()
        return success
    }

    class WireGuardException(private val reason: Reason, vararg format: Any?): Exception() {
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
            DISCONNECTED,
            PERMISSION_DENIED
        }
    }

    companion object {
        private const val TAG = "Sinfonia/WireGuardClient"
        private const val ACTION_BIND_WIREGUARD_SERVICE = "com.wireguard.android.service.IWireGuardService"
    }
}