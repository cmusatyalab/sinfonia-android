package com.wireguard.android

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.wireguard.android.Application.Companion.getTunnelManager
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.model.TunnelManager
import com.wireguard.android.service.IWireGuardService
import edu.cmu.cs.sinfonia.util.TunnelException
import edu.cmu.cs.sinfonia.util.TunnelException.Reason
import edu.cmu.cs.sinfonia.wireguard.ParcelableConfig
import kotlinx.coroutines.runBlocking

class WireGuardService : Service() {
    private lateinit var tunnelManager: TunnelManager
    private lateinit var localBroadcastManager: LocalBroadcastManager

    private val binder = object: IWireGuardService.Stub() {
        override fun basicTypes(
            anInt: Int,
            aLong: Long,
            aBoolean: Boolean,
            aFloat: Float,
            aDouble: Double,
            aString: String?
        ) {
            TODO("Not yet implemented")
        }

        override fun fetchMyTunnels(applications: Array<String>): Array<String> {
            val mTunnels: MutableList<String> = mutableListOf()
            runBlocking {
                try {
                    val tunnels = tunnelManager.getTunnels()
                    for (tunnel in tunnels) {
                        val includedApplications =
                            tunnel.config?.`interface`?.includedApplications ?: continue
                        var pass = false
                        for (application in applications) {
                            if (!includedApplications.contains(application)) {
                                pass = true
                                break
                            }
                        }
                        if (pass) continue
                        mTunnels.add(tunnel.key)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "fetchMyTunnels", e)
                }
            }
            return mTunnels.toTypedArray()
        }

        override fun refreshTunnels() {
            Log.i(TAG, "refreshTunnels")
            val intent = Intent(ACTION_REFRESH_TUNNEL_STATES)
            localBroadcastManager.sendBroadcast(intent)
        }

        override fun createTunnel(tunnelName: String, parcelableConfig: ParcelableConfig): TunnelException? {
            Log.i(TAG, "createTunnel: $tunnelName")
            var throwable: TunnelException? = null
            runBlocking {
                val config = parcelableConfig.resolve()
                try {
                    tunnelManager.create(tunnelName, config)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "createTunnel", e)
                    throwable = when {
                        e.message.equals(getString(R.string.tunnel_error_invalid_name)) -> TunnelException(Reason.INVALID_NAME)
                        e.message.equals(getString(R.string.tunnel_error_already_exists, tunnelName)) -> TunnelException(Reason.ALREADY_EXIST, tunnelName)
                        else -> TunnelException(Reason.UNKNOWN)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "createTunnel", e)
                    throwable = TunnelException(Reason.UNKNOWN)
                }
            }
            return throwable
        }

        override fun destroyTunnel(tunnelName: String): TunnelException? {
            Log.i(TAG, "destroyTunnel: $tunnelName")
            var throwable: TunnelException? = null
            runBlocking {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName]
                if (tunnel == null) {
                    throwable = TunnelException(Reason.NOT_FOUND, tunnelName)
                    return@runBlocking
                }
                try {
                    tunnelManager.delete(tunnel)
                } catch(e: Throwable) {
                    Log.e(TAG, "destroyTunnel", e)
                    throwable = TunnelException(Reason.UNKNOWN)
                }
            }
            return throwable
        }

        override fun setTunnelUp(tunnelName: String): TunnelException? {
            Log.i(TAG, "setTunnelUp: $tunnelName")
            var throwable: TunnelException? = null
            runBlocking {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName]
                if (tunnel == null) {
                    throwable = TunnelException(Reason.NOT_FOUND, tunnelName)
                    return@runBlocking
                }
                if (tunnel.state == Tunnel.State.UP) {
                    throwable = TunnelException(Reason.ALREADY_UP, tunnelName)
                    return@runBlocking
                }
                try {
                    tunnelManager.setTunnelState(tunnel, Tunnel.State.UP)
                } catch (e: Throwable) {
                    Log.e(TAG, "setTunnelUp", e)
                    throwable = TunnelException(Reason.UNKNOWN)
                }
            }
            return throwable
        }

        override fun setTunnelDown(tunnelName: String): TunnelException? {
            Log.i(TAG, "setTunnelDown: $tunnelName")
            var throwable: TunnelException? = null
            runBlocking {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName]
                if (tunnel == null) {
                    throwable = TunnelException(Reason.NOT_FOUND, tunnelName)
                    return@runBlocking
                }
                if (tunnel.state == Tunnel.State.DOWN) {
                    throwable = TunnelException(Reason.ALREADY_DOWN, tunnelName)
                    return@runBlocking
                }
                try {
                    tunnelManager.setTunnelState(tunnel, Tunnel.State.DOWN)
                } catch (e: Throwable) {
                    Log.e(TAG, "setTunnelDown", e)
                    throwable = TunnelException(Reason.UNKNOWN)
                }
            }
            return throwable
        }

        override fun setTunnelToggle(tunnelName: String): TunnelException? {
            Log.i(TAG, "setTunnelToggle: $tunnelName")
            var throwable: TunnelException? = null
            runBlocking {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName]
                if (tunnel == null) {
                    throwable = TunnelException(Reason.NOT_FOUND, tunnelName)
                    return@runBlocking
                }
                if (tunnel.state == Tunnel.State.TOGGLE) {
                    throwable = TunnelException(Reason.ALREADY_TOGGLE, tunnelName)
                    return@runBlocking
                }
                try {
                    tunnelManager.setTunnelState(tunnel, Tunnel.State.DOWN)
                } catch (e: Throwable) {
                    Log.e(TAG, "setTunnelDown", e)
                    throwable = TunnelException(Reason.UNKNOWN)
                }
            }
            return throwable
        }

        @RequiresApi(Build.VERSION_CODES.N)
        override fun getTunnelConfig(tunnelName: String): ParcelableConfig? {
            Log.i(TAG, "getTunnelConfig: $tunnelName")
            var parcelableConfig: ParcelableConfig? = null
            runBlocking {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@runBlocking
                val config = tunnelManager.getTunnelConfig(tunnel)
                parcelableConfig = ParcelableConfig(config)
            }
            return parcelableConfig
        }

        @RequiresApi(Build.VERSION_CODES.N)
        override fun setTunnelConfig(tunnelName: String, parcelableConfig: ParcelableConfig): ParcelableConfig? {
            Log.i(TAG, "setTunnelConfig: $tunnelName, $parcelableConfig")
            var newParcelableConfig: ParcelableConfig? = null
            runBlocking {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@runBlocking
                val config = parcelableConfig.resolve()
                val newConfig = tunnelManager.setTunnelConfig(tunnel, config)
                newParcelableConfig = ParcelableConfig(newConfig)
            }
            return newParcelableConfig
        }
    }

    override fun onCreate() {
        super.onCreate()
        tunnelManager = getTunnelManager()
        localBroadcastManager = LocalBroadcastManager.getInstance(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "onBind: $intent")
        return binder
    }

    override fun onRebind(intent: Intent?) {
        Log.i(TAG, "onRebind: $intent")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind: $intent")
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "WireGuard/WireGuardService"
        const val ACTION_REFRESH_TUNNEL_STATES = "com.wireguard.android.action.REFRESH_TUNNEL_STATES"
    }
}