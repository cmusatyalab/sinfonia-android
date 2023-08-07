/*
 * Copyright 2023 Carnegie Mellon University
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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

/**
 * WireGuard service enables other applications to control the tunnels through IPC.
 *
 * @property tunnelManager The tunnel controller used in the original WireGuard app
 * @property localBroadcastManager The broadcaster used send intents to the WireGuard app receiver
 * @property binder The WireGuard service stub that defines a set of supported IPC methods
 */
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
        ) { TODO("Not implemented") }

        /**
         * fetchMyTunnels fetches all WireGuard tunnels that include the list of applications.
         *
         * @param applications A string array of applications (e.g. "com.android.chrome")
         * @return A array of names of tunnels that include all applications from the input
         */
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

        /**
         * refreshTunnels refreshes all existing tunnels in WireGuard app. It sends an internal
         * intent to a local receiver to refresh the tunnels.
         */
        override fun refreshTunnels() {
            Log.i(TAG, "refreshTunnels")
            val intent = Intent(ACTION_REFRESH_TUNNEL_STATES)
            localBroadcastManager.sendBroadcast(intent)
        }

        /**
         * createTunnel creates a tunnel with specified name and configuration. It returns Tunnel
         * Exception if an error occur.
         *
         * @param tunnelName The tunnel name to be created
         * @param parcelableConfig The configuration of the tunnel
         * @param overwrite Overwrites tunnel configuration if it exists
         * @return The tunnel exception when an error occurred while creating a tunnel, or null.
         */
        override fun createTunnel(
            tunnelName: String,
            parcelableConfig: ParcelableConfig,
            overwrite: Boolean
        ): TunnelException? {
            Log.i(TAG, "createTunnel: $tunnelName")
            var throwable: TunnelException? = null
            runBlocking {
                val config = parcelableConfig.resolve()
                try {
                    tunnelManager.create(tunnelName, config)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "createTunnel", e)
                    if (e.message.equals(getString(R.string.tunnel_error_already_exists, tunnelName)) && overwrite) {
                        val tunnels = tunnelManager.getTunnels()
                        val tunnel = tunnels[tunnelName]
                        if (tunnel == null) {
                            throwable = TunnelException(Reason.NOT_FOUND, tunnelName)
                            return@runBlocking
                        }
                        try {
                            tunnelManager.setTunnelConfig(tunnel, config)
                        } catch (e: Throwable) {
                            Log.e(TAG, "createTunnel", e)
                            throwable = TunnelException(Reason.UNKNOWN)
                        }
                        return@runBlocking
                    }
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

        /**
         * destroyTunnel deletes a tunnel from WireGuard app by its name.
         *
         * @param tunnelName The name of the tunnel
         * @return The tunnel exception when an error occurred while destroying a tunnel, or null.
         */
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

        /**
         * setTunnelUp opens a tunnel.
         *
         * @param tunnelName The name of the tunnel to be set up
         * @return The tunnel exception when an error occurred while setting a tunnel up, or null.
         */
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

        /**
         * setTunnelDown closes a tunnel.
         *
         * @param tunnelName The name of the tunnel to be closed
         * @return The tunnel exception when an error occurred while closing a tunnel, or null.
         */
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

        /**
         * setTunnelToggle sets a tunnel to toggle state, not sure if this is useful.
         *
         * @param tunnelName The name of the tunnel to be set to toggle state
         * @return The tunnel exception when an error occurred while setting a tunnel to toggle
         * state, or null.
         */
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

        /**
         * getTunnelConfig returns the configuration of a tunnel. This may be useful when trying to
         * compare or resolve the conflict between configuration returned from tier-1 and existing
         * configuration in WireGuard app.
         *
         * @param tunnelName The name of the tunnel
         * @return The configuration of the tunnel
         */
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

        /**
         * setTunnelConfig overwrites the configuration of a tunnel with a new configuration and
         * returns the new configuration.
         *
         * @param tunnelName The name of the tunnel
         * @param parcelableConfig The new configuration used to overwrite the old
         */
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