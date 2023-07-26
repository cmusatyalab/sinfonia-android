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

        override fun refreshTunnels() {
            Log.i(TAG, "refreshTunnels")
            val intent = Intent(ACTION_REFRESH_TUNNEL_STATES)
            localBroadcastManager.sendBroadcast(intent)
        }

        override fun createTunnel(tunnelName: String, parcelableConfig: ParcelableConfig): Boolean {
            Log.i(TAG, "createTunnel: $tunnelName")
            var success = false
            runBlocking {
                val config = parcelableConfig.resolve()
                try {
                    tunnelManager.create(tunnelName, config)
                } catch (_: IllegalArgumentException) {
                    return@runBlocking
                } catch (e: Throwable) {
                    Log.e(TAG, "createTunnel", e)
                    return@runBlocking
                }
                setTunnelUp(tunnelName)
                success = true
            }
            return success
        }

        override fun destroyTunnel(tunnelName: String): Boolean {
            Log.i(TAG, "destroyTunnel: $tunnelName")
            var success = false
            runBlocking {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@runBlocking
                try {
                    tunnelManager.delete(tunnel)
                } catch(e: Throwable) {
                    Log.e(TAG, "destroyTunnel", e)
                    return@runBlocking
                }
                success = true
            }
            return success
        }

        override fun setTunnelUp(tunnelName: String): Boolean {
            Log.i(TAG, "setTunnelUp: $tunnelName")
            var success = false
            runBlocking {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@runBlocking
                val newState: Tunnel.State
                try {
                    newState = tunnelManager.setTunnelState(tunnel, Tunnel.State.UP)
                } catch (e: Throwable) {
                    Log.e(TAG, "setTunnelUp", e)
                    return@runBlocking
                }
                if (newState == Tunnel.State.UP) success = true
            }
            return success
        }

        override fun setTunnelDown(tunnelName: String): Boolean {
            Log.i(TAG, "setTunnelDown: $tunnelName")
            var success = false
            runBlocking {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@runBlocking
                val newState: Tunnel.State?
                try {
                    newState = tunnelManager.setTunnelState(tunnel, Tunnel.State.DOWN)
                } catch (e: Throwable) {
                    Log.e(TAG, "setTunnelDown", e)
                    return@runBlocking
                }
                if (newState == Tunnel.State.DOWN) success = true
            }
            return success
        }

        override fun setTunnelToggle(tunnelName: String): Boolean {
            Log.i(TAG, "setTunnelToggle: $tunnelName")
            var success = false
            runBlocking {
                val tunnels = tunnelManager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@runBlocking
                val newState: Tunnel.State?
                try {
                    newState = tunnelManager.setTunnelState(tunnel, Tunnel.State.DOWN)
                } catch (e: Throwable) {
                    Log.e(TAG, "setTunnelDown", e)
                    return@runBlocking
                }
                if (newState == Tunnel.State.TOGGLE) success = true
            }
            return success
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