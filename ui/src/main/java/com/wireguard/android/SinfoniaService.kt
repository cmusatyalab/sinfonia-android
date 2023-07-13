package com.wireguard.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.wireguard.android.activity.MainActivity
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.databinding.ObservableSortedKeyedArrayList
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.util.ErrorMessages
import com.wireguard.config.Config
import edu.cmu.cs.sinfonia.model.SinfoniaTier3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SinfoniaService: Service()  {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private var sinfonia: SinfoniaTier3? = null
    private var tunnel: ObservableTunnel? = null
    private var binder: IBinder? = MyBinder()
    private val sinfoniaCallback: SinfoniaCallbacks = SinfoniaCallbacks()

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: $intent")
        if (intent != null) {
            createNotificationChannel()
            val notification: Notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            when (intent.action) {
                ACTION_START -> handleActionSinfonia(intent)
                ACTION_STOP -> onDestroy()
            }
        }
        registerComponentCallbacks(sinfoniaCallback)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.i(TAG, "onBind: $intent")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind: $intent")
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        Log.i(TAG, "onRebind: $intent")
        super.onRebind(intent)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        unregisterComponentCallbacks(sinfoniaCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Sinfonia Foreground Service Channel",
                    NotificationManager.IMPORTANCE_MIN
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_CODE,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Sinfonia Foreground Service")
                .setContentText("Service is running in the foreground")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        return notificationBuilder.build()
    }

    private fun onTunnelCreated(newTunnel: ObservableTunnel?, throwable: Throwable?) {
        Log.i(TAG, "onTunnelCreated")
        val ctx = Application.get()
        if (throwable == null) {
            tunnel = newTunnel
            val message = ctx.getString(R.string.tunnel_create_success, tunnel!!.name)
            Log.d(TAG, message)
        } else {
            val error = ErrorMessages[throwable]
            val message = ctx.getString(R.string.tunnel_create_error, error)
            Log.e(TAG, message, throwable)
        }
    }

    private fun setTunnelState(checked: Boolean) {
        scope.launch {
            if (Application.getBackend() is GoBackend) {
                try {
                    val intent = GoBackend.VpnService.prepare(Application.get())
                    if (intent != null) {
                        setTunnelStateWithPermissionsResult(tunnel!!, checked)
                        startActivity(intent)
                        return@launch
                    }
                } catch (e: Throwable) {
                    val error = ErrorMessages[e]
                    Log.e(TAG, error, e)
                }
            }
            setTunnelStateWithPermissionsResult(tunnel!!, checked)
        }
    }

    private fun setTunnelStateWithPermissionsResult(tunnel: ObservableTunnel, checked: Boolean) {
        scope.launch {
            try {
                tunnel.setStateAsync(Tunnel.State.of(checked))
            } catch (e: Throwable) {
                val error = ErrorMessages[e]
                Log.e(TAG, error, e)
            }
        }
    }

    inner class MyBinder: Binder() {
        fun getService(): SinfoniaService {
            return this@SinfoniaService
        }
    }

    inner class SinfoniaCallbacks : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            TODO("Not yet implemented")
        }

        override fun onLowMemory() {
            TODO("Not yet implemented")
        }

        fun onTunnelUp() {

        }

        fun onTunnelError(e: Throwable) {

        }
    }

    private fun handleActionSinfonia(intent: Intent) {
        Log.i(TAG, "handleActionSinfonia")

        scope.launch {
            sinfonia = SinfoniaTier3(
                    ctx = Application.get(),
                    url = intent.getStringExtra("url") ?: "https://cmu.findcloudlet.org",
                    applicationName = intent.getStringExtra("applicationName") ?: "helloworld",
                    uuid = intent.getStringExtra("uuid") ?: "00000000-0000-0000-0000-000000000000",
                    zeroconf = intent.getBooleanExtra("zeroconf", false),
                    application = intent.getStringArrayListExtra("application") ?: listOf("com.android.chrome")
            ).deploy()

            Log.d(TAG, "handleActionSinfonia deployed: $sinfonia")

            val e = createTunnel()
            if (e != null) {
                sinfoniaCallback.onTunnelError(e)
                return@launch
            }

            setTunnelState(true)
            sinfoniaCallback.onTunnelUp()
        }
    }

    private suspend fun createTunnel(): Throwable? {
        val newConfig = try {
            sinfonia?.deployment?.tunnelConfig
        } catch (e: Throwable) {
            val error = ErrorMessages[e]
            val tunnelName = if (tunnel == null) sinfonia?.applicationName else tunnel!!.name
            val message = getString(R.string.config_save_error, tunnelName, error)
            Log.e(TAG, message, e)
            return e
        }

        Log.d(TAG, "handleActionSinfonia newConfig: $newConfig")

        val manager = Application.getTunnelManager()
        if (!hasSameConfigTunnel(newConfig, manager.getTunnels())) {
            try {
                onTunnelCreated(manager.create(sinfonia?.applicationName!!, newConfig), null)
            } catch (e: Throwable) {
                onTunnelCreated(null, e)
                return e
            }
        }
        return null
    }

    private fun hasSameConfigTunnel(
            config: Config?,
            tunnels: ObservableSortedKeyedArrayList<String, ObservableTunnel>
    ): Boolean {
        Log.i(TAG, "hasSameConfigTunnel")
        if (config == null) return false
        logConfig(config)
        for (tunnel in tunnels) {
            logConfig(tunnel.config!!)
            if (config == tunnel.config) {
                this.tunnel = tunnel
                return true
            }
        }
        return false
    }

    private fun launchApplication(application: String) {
        val packageManager = Application.get().packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(application)
        if (launchIntent != null) startActivity(launchIntent) else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(application)))
    }

    inner class IntentReceiver : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.N)
        override fun onReceive(context: Context, intent: Intent?) {
            Log.i(TAG, "Service received broadcast: $intent")
            when (intent?.action) {
                ACTION_STOP -> onDestroy()
            }
        }
    }

    private fun logConfig(config: Config) {
        val `interface` = config.`interface`
        val peers = config.peers
        Log.d(TAG, "interface/addresses: ${`interface`.addresses}")
        Log.d(TAG, "interface/dnsServers: ${`interface`.dnsServers}")
        Log.d(TAG, "interface/excludedApplications: ${`interface`.excludedApplications}")
        Log.d(TAG, "interface/includedApplications: ${`interface`.includedApplications}")
        Log.d(TAG, "interface/keyPair: ${`interface`.keyPair}")
        Log.d(TAG, "interface/keyPair/publicKey: ${`interface`.keyPair.publicKey}")
        Log.d(TAG, "interface/keyPair/privateKey: ${`interface`.keyPair.privateKey}")
        Log.d(TAG, "interface/listenPort: ${`interface`.listenPort}")
        Log.d(TAG, "interface/mtu: ${`interface`.mtu}")

        for (peer in peers) {
            Log.d(TAG, "peer/allowedIps: ${peer.allowedIps}")
            Log.d(TAG, "peer/endpoint: ${peer.endpoint}")
            Log.d(TAG, "peer/persistentKeepalive: ${peer.persistentKeepalive}")
            Log.d(TAG, "peer/preSharedKey: ${peer.preSharedKey}")
            Log.d(TAG, "peer/publicKey: ${peer.publicKey}")
        }
    }

    companion object {
        private const val TAG = "WireGuard/SinfoniaService"
        private const val NOTIFICATION_CHANNEL_ID = "SinfoniaForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_CODE = 0
        private const val ACTION_START = "edu.cmu.cs.sinfonia.action.START"
        private const val ACTION_STOP = "edu.cmu.cs.sinfonia.action.STOP"
    }
}