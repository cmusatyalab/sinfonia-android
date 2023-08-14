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

/**
 * WireGuardClient is a client that binds to the WireGuard service and implements several IPC
 * methods to control WireGuard tunnels.
 *
 * @property mService The stub to execute IPC methods
 * @property mTunnels Authorized list of WireGuard tunnels to control
 * @property serviceConnection The service connection for WireGuard service
 */
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

    /**
     * fetchMyTunnels fetches all existing WireGuard tunnel names that include the input
     * applications. These are the tunnels that this client is authorized to control. Need a better
     * access control mechanism for tunnel controls.
     *
     * @param applications the applications included in the WireGuard interface
     * @throws WireGuardException
     */
    fun fetchMyTunnels(applications: List<String>) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        mTunnels.addAll(mService?.fetchMyTunnels(applications.toTypedArray()) ?: arrayOf())
    }

    /**
     * refreshTunnels refreshes all tunnels in WireGuard
     *
     * @throws WireGuardException
     */
    fun refreshTunnels() {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        mService?.refreshTunnels()
    }

    /**
     * createTunnel creates a WireGuard tunnel with specified tunnel name and configuration.
     * [overwrite] option must be specified: if set to true, the WireGuard configuration of the
     * tunnel with [tunnelName], if exists, will be overwritten; if set to false,
     * [TunnelException.Reason.ALREADY_EXIST] exception will be thrown if tunnel with [tunnelName]
     * exists, and [mTunnels] will not be updated.
     *
     * @param tunnelName name of the tunnel to be created
     * @param config the configuration for the tunnel to be created
     * @param overwrite the boolean value to overwrite WireGuard configuration if the tunnel with
     * [tunnelName] exists (this value will have no effect if the tunnel does not exist prior to
     * calling this function)
     * @throws WireGuardException
     * @throws TunnelException
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun createTunnel(tunnelName: String, config: Config, overwrite: Boolean) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        val parcelableConfig = ParcelableConfig(config)
        val throwable: TunnelException? = mService?.createTunnel(tunnelName, parcelableConfig, overwrite)
        if (throwable != null) throw throwable
        mTunnels.add(tunnelName)
    }

    /**
     * destroyTunnel attempts to delete a WireGuard tunnel and remove it from [mTunnels].
     *
     * @param tunnelName the name of the WireGuard tunnel to be deleted
     * @throws WireGuardException
     * @throws TunnelException
     */
    fun destroyTunnel(tunnelName: String) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        if (!mTunnels.contains(tunnelName)) throw TunnelException(TunnelException.Reason.UNAUTHORIZED_ACCESS)
        val throwable: TunnelException? = mService?.destroyTunnel(tunnelName)
        if (throwable != null) throw throwable
        mTunnels.remove(tunnelName)
    }

    /**
     * setTunnelUp attempts to open an existing WireGuard tunnel.
     *
     * @param tunnelName the name of the WireGuard tunnel to be opened
     * @throws WireGuardException
     * @throws TunnelException
     */
    fun setTunnelUp(tunnelName: String) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        if (!mTunnels.contains(tunnelName)) throw TunnelException(TunnelException.Reason.UNAUTHORIZED_ACCESS)
        val throwable: TunnelException? = mService?.setTunnelUp(tunnelName)
        if (throwable != null) throw throwable
    }

    /**
     * setTunnelDown attempts to close an existing WireGuard tunnel.
     *
     * @param tunnelName the name of the WireGuard tunnel to be closed
     * @throws WireGuardException
     * @throws TunnelException
     */
    fun setTunnelDown(tunnelName: String) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        if (!mTunnels.contains(tunnelName)) throw TunnelException(TunnelException.Reason.UNAUTHORIZED_ACCESS)
        val throwable: TunnelException? = mService?.setTunnelDown(tunnelName)
        if (throwable != null) throw throwable
    }

    /**
     * setTunnelDownAll attempts to close all WireGuard tunnels.
     *
     * @throws WireGuardException
     */
    fun setTunnelDownAll() {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        for (tunnel in mTunnels) {
            try {
                setTunnelDown(tunnel)
            } catch (_: Throwable) {}
        }
    }

    /**
     * setTunnelToggle attempts to set an existing WireGuard tunnel to TOGGLE state.
     *
     * @param tunnelName the name of the WireGuard tunnel to be set to TOGGLE state
     * @throws WireGuardException
     * @throws TunnelException
     */
    fun setTunnelToggle(tunnelName: String) {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        if (!mTunnels.contains(tunnelName)) throw TunnelException(TunnelException.Reason.UNAUTHORIZED_ACCESS)
        val throwable: TunnelException? = mService?.setTunnelToggle(tunnelName)
        if (throwable != null) throw throwable
    }

    fun getTunnelConfig(tunnelName: String): Config? {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        if (!mTunnels.contains(tunnelName)) throw TunnelException(TunnelException.Reason.UNAUTHORIZED_ACCESS)
        val parcelableConfig: ParcelableConfig = mService?.getTunnelConfig(tunnelName) ?: return null
        return parcelableConfig.resolve()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun setTunnelConfig(tunnelName: String, config: Config): Config? {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        if (!mTunnels.contains(tunnelName)) throw TunnelException(TunnelException.Reason.UNAUTHORIZED_ACCESS)
        val parcelableConfig = ParcelableConfig(config)
        val newParcelableConfig: ParcelableConfig = mService?.setTunnelConfig(tunnelName, parcelableConfig) ?: return null
        return newParcelableConfig.resolve()
    }

    // For unit test purpose only
    fun saveTunnel(tunnelName: String) {
        mTunnels.add(tunnelName)
    }

    // For unit test purpose only
    fun removeTunnel(tunnelName: String) {
        mTunnels.remove(tunnelName)
    }

    /**
     * cleanup attempts to destroy all authorized WireGuard tunnels.
     *
     * @throws WireGuardException
     * @return a boolean value to indicate if any exception is caught in tunnel deletion
     */
    fun cleanup(): Boolean {
        if (mService == null) throw WireGuardException(WireGuardException.Reason.DISCONNECTED)
        var success = true
        for (tunnel in mTunnels) {
            try {
                destroyTunnel(tunnel)
            } catch (e: Throwable) {
                Log.e(TAG, "cleanup", e)
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