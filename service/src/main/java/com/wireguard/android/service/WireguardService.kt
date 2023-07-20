package com.wireguard.android.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.wireguard.android.service.IWireguardService

class WireguardService : Service() {

    private val binder = object: IWireguardService.Stub() {
        override fun refreshTunnels() {}

        override fun createTunnel(tunnelName: String) {}

        override fun destroyTunnel(tunnelName: String) {}

        override fun setTunnelUp(tunnelName: String) {}

        override fun setTunnelDown(tunnelName: String) {}
    }
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}