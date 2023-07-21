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
import com.wireguard.android.util.applicationScope
import edu.cmu.cs.sinfonia.wireguard.ParcelableConfig
import kotlinx.coroutines.launch

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

        override fun refreshTunnels() {
            Log.i(TAG, "refreshTunnels")
            val intent = Intent(ACTION_REFRESH_TUNNEL_STATES)
            localBroadcastManager.sendBroadcast(intent)
        }

        override fun createTunnel(tunnelName: String, parcelableConfig: ParcelableConfig) {
            Log.i(TAG, "createTunnel: $tunnelName")
            applicationScope.launch {
                val config = parcelableConfig.resolve()
                try {
                    tunnelManager.create(tunnelName, config)
                } catch (_: IllegalArgumentException) {
                } catch (e: Throwable) {
                    Log.e(TAG, "createTunnel", e)
                    return@launch
                }
                setTunnelUp(tunnelName)
            }
        }

        override fun destroyTunnel(tunnelName: String) {
            Log.i(TAG, "destroyTunnel: $tunnelName")
            applicationScope.launch {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@launch
                try {
                    tunnelManager.delete(tunnel)
                } catch(e: Throwable) {
                    Log.e(TAG, "destroyTunnel", e)
                }
            }
        }

        override fun setTunnelUp(tunnelName: String) {
            Log.i(TAG, "setTunnelUp: $tunnelName")
            applicationScope.launch {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@launch
                var newState: Tunnel.State = Tunnel.State.TOGGLE
                try {
                    newState = tunnelManager.setTunnelState(tunnel, Tunnel.State.UP)
                } catch (e: Throwable) {
                    Log.e(TAG, "setTunnelUp", e)
                }
                if (newState != Tunnel.State.UP) Log.d(TAG, "setTunnelUp: $newState")
            }
        }

        override fun setTunnelDown(tunnelName: String) {
            Log.i(TAG, "setTunnelDown: $tunnelName")
            applicationScope.launch {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@launch
                var newState: Tunnel.State = Tunnel.State.TOGGLE
                try {
                    newState = tunnelManager.setTunnelState(tunnel, Tunnel.State.DOWN)
                } catch (e: Throwable) {
                    Log.e(TAG, "setTunnelDown", e)
                }
                if (newState != Tunnel.State.DOWN) Log.d(TAG, "setTunnelDown: $newState")
            }
        }

        @RequiresApi(Build.VERSION_CODES.N)
        override fun getTunnelConfig(tunnelName: String, parcelableConfig: ParcelableConfig) {
            Log.i(TAG, "getTunnelConfig: $tunnelName")
            applicationScope.launch {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@launch
                val config = tunnelManager.getTunnelConfig(tunnel)
                parcelableConfig.overwrite(config)
            }
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
        const val ACTION_CREATE_TUNNEL = "com.wireguard.android.action.CREATE_TUNNEL"
        const val ACTION_SET_TUNNEL_UP = "com.wireguard.android.action.SET_TUNNEL_UP"
    }
}