package edu.cmu.cs.sinfonia

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
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import edu.cmu.cs.sinfonia.model.SinfoniaMethods
import edu.cmu.cs.sinfonia.model.SinfoniaTier3
import edu.cmu.cs.sinfonia.model.SinfoniaTier3.DeployException
import edu.cmu.cs.sinfonia.model.SinfoniaTier3.DeployException.Reason
import edu.cmu.cs.sinfonia.util.ErrorMessages
import edu.cmu.cs.sinfonia.util.TunnelException
import edu.cmu.cs.sinfonia.wireguard.WireGuardClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class SinfoniaService : Service(), SinfoniaMethods {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private var sinfonia: SinfoniaTier3? = null
    private var binder: IBinder? = MyBinder()
    private val sinfoniaCallback: SinfoniaCallbacks = SinfoniaCallbacks()
    private val wireGuardClient = WireGuardClient(this)


    override fun onCreate() {
        super.onCreate()
        wireGuardClient.bind()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: $intent")
        if (intent != null) {
            createNotificationChannel()
            val notification: Notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            when (intent.action) {
                ACTION_START -> deploy(intent)
                ACTION_STOP -> onDestroy()
            }
        }
        registerComponentCallbacks(sinfoniaCallback)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.i(TAG, "onBind: $intent")
        return when (intent?.action) {
            ACTION_BIND -> binder
            else -> null
        }
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
        wireGuardClient.unbind()
        super.onDestroy()
        job.cancel()
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
        val notificationIntent = Intent(WIREGUARD_PACKAGE)
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Sinfonia Foreground Service")
            .setContentText("Service is running in the foreground")
//            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        return notificationBuilder.build()
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

        fun onFetch(applicationName: String, throwable: Throwable?) {
            val ctx = get()
            scope.launch(Dispatchers.Main.immediate) {
                if (throwable == null) {
                    val message = ctx.getString(R.string.fetch_success, applicationName)
                    Log.d(TAG, message)
                } else {
                    val error = ErrorMessages[throwable]
                    val message = ctx.getString(R.string.fetch_error, applicationName, error)
                    Log.e(TAG, message, throwable)
                }
            }
        }

        fun onDeploy(applicationName: String, throwable: Throwable?) {
            val ctx = get()
            scope.launch(Dispatchers.Main.immediate) {
                if (throwable == null) {
                    val message = ctx.getString(R.string.deploy_success, applicationName)
                    Log.d(TAG, message)
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                } else {
                    val error = ErrorMessages[throwable]
                    val message = ctx.getString(R.string.deploy_error, applicationName, error)
                    Log.e(TAG, message, throwable)
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun onTunnelCreated(tunnelName: String, throwable: Throwable?) {
            val ctx = get()
            scope.launch(Dispatchers.Main.immediate) {
                if (throwable == null) {
                    val message = ctx.getString(R.string.tunnel_create_success, tunnelName)
                    Log.d(TAG, message)
                    wireGuardClient.saveTunnel(tunnelName)
                } else {
                    val error = ErrorMessages[throwable]
                    val message = ctx.getString(R.string.tunnel_create_error, error)
                    Log.e(TAG, message, throwable)
                    if (throwable is TunnelException) {
                        when (throwable.getReason()) {
                            TunnelException.Reason.UNKNOWN -> return@launch
                            TunnelException.Reason.INVALID_NAME -> return@launch
                            TunnelException.Reason.ALREADY_EXIST -> scope.launch inner@{
                                try {
                                    wireGuardClient.setTunnelUp(tunnelName)
                                } catch (e: Throwable) {
                                    sinfoniaCallback.onTunnelSet(tunnelName, Tunnel.State.UP, e)
                                    return@inner
                                }
                                sinfoniaCallback.onTunnelSet(tunnelName, Tunnel.State.UP, null)
                            }
                            else -> {}
                        }
                        wireGuardClient.saveTunnel(tunnelName)
                    }
                }
            }
        }

        fun onTunnelDestroyed(tunnelName: String, throwable: Throwable?) {
            val ctx = get()
            scope.launch(Dispatchers.Main.immediate) {
                if (throwable == null) {
                    val message = ctx.getString(R.string.tunnel_destroy_success, tunnelName)
                    Log.d(TAG, message)
                    wireGuardClient.removeTunnel(tunnelName)
                } else {
                    val error = ErrorMessages[throwable]
                    val message = ctx.getString(R.string.tunnel_destroy_error, error)
                    Log.e(TAG, message, throwable)
                    if (throwable is TunnelException) {
                        when (throwable.getReason()) {
                            TunnelException.Reason.UNKNOWN -> return@launch
                            TunnelException.Reason.INVALID_NAME -> return@launch
                            TunnelException.Reason.UNAUTHORIZED_ACCESS -> return@launch
                            else -> {}
                        }
                        wireGuardClient.removeTunnel(tunnelName)
                    }
                }
            }
        }

        fun onTunnelSet(tunnelName: String, state: Tunnel.State, throwable: Throwable?) {
            val ctx = get()
            scope.launch(Dispatchers.Main.immediate) {
                if (throwable == null) {
                    val message = ctx.getString(R.string.tunnel_set_success, tunnelName, state.toString().lowercase())
                    Log.d(TAG, message)
                } else {
                    val error = ErrorMessages[throwable]
                    val message = ctx.getString(R.string.tunnel_set_error, tunnelName, state.toString().lowercase(), error)
                    Log.e(TAG, message, throwable)
                }
            }
        }
    }

    override fun fetch(intent: Intent) {
        Log.i(TAG, "fetch: $intent")
        scope.launch {
            val applicationName = intent.getStringExtra("applicationName") ?: ""
            val application = intent.getStringArrayListExtra("application") ?: listOf()
            wireGuardClient.fetchMyTunnels(application)
            wireGuardClient.setTunnelDownAll()
            try {
                sinfonia = SinfoniaTier3(
                    ctx = get(),
                    url = intent.getStringExtra("url") ?: "https://cmu.findcloudlet.org",
                    applicationName = applicationName,
                    uuid = intent.getStringExtra("uuid"),
                    zeroconf = intent.getBooleanExtra("zeroconf", false),
                    application = application
                ).fetch()
            } catch (e: Throwable) {
                sinfoniaCallback.onFetch(applicationName, e)
                return@launch
            }
            sinfoniaCallback.onFetch(applicationName, null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun deploy(intent: Intent) {
        Log.i(TAG, "deploy: $intent")
        scope.launch {
            val applicationName = intent.getStringExtra("applicationName") ?: ""
            val tunnelName = intent.getStringExtra("tunnelName") ?: applicationName
            val application = intent.getStringArrayListExtra("application") ?: listOf()
            wireGuardClient.fetchMyTunnels(application)
            wireGuardClient.setTunnelDownAll()
            try {
                sinfonia = SinfoniaTier3(
                    ctx = get(),
                    url = intent.getStringExtra("url") ?: "https://cmu.findcloudlet.org",
                    applicationName = applicationName,
                    uuid = intent.getStringExtra("uuid"),
                    zeroconf = intent.getBooleanExtra("zeroconf", false),
                    application = application
                ).deploy()
            } catch (e: Throwable) {
                sinfoniaCallback.onDeploy(applicationName, e)
                return@launch
            }
            sinfoniaCallback.onDeploy(applicationName, null)

            try {
                createTunnel(tunnelName)
            } catch (e: Throwable) {
                sinfoniaCallback.onTunnelCreated(tunnelName, e)
                return@launch
            }
            sinfoniaCallback.onTunnelCreated(tunnelName, null)

            try {
                wireGuardClient.setTunnelUp(tunnelName)
            } catch (e: Throwable) {
                sinfoniaCallback.onTunnelSet(tunnelName, Tunnel.State.UP, e)
                return@launch
            }
            sinfoniaCallback.onTunnelSet(tunnelName, Tunnel.State.UP, null)
        }
    }

    override fun cleanup(): Boolean {
        return wireGuardClient.cleanup()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun createTunnel(tunnelName: String) {
        val deployment = sinfonia?.deployment ?: throw DeployException(Reason.DEPLOYMENT_NOT_FOUND)
        val newConfig = deployment.tunnelConfig
        Log.d(TAG, "createTunnel: $newConfig")
        wireGuardClient.createTunnel(tunnelName, newConfig)
    }

//    private fun hasSameConfigTunnel(
//        config: Config?,
//        tunnels: ObservableSortedKeyedArrayList<String, ObservableTunnel>
//    ): Boolean {
//        Log.i(TAG, "hasSameConfigTunnel")
//        if (config == null) return false
//        logConfig(config)
//        for (tunnel in tunnels) {
//            logConfig(tunnel.config!!)
//            if (config == tunnel.config) {
//                this.tunnel = tunnel
//                return true
//            }
//        }
//        return false
//    }

    class IntentReceiver : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.N)
        override fun onReceive(context: Context, intent: Intent?) {
            Log.i(TAG, "Service received broadcast: $intent")
            when (intent?.action) {
                ACTION_STOP -> TODO("Not yet implemented")
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
        const val PACKAGE_NAME = "edu.cmu.cs.sinfonia"
        const val WIREGUARD_PACKAGE = "com.wireguard.android.debug"
        private lateinit var weakSelf: WeakReference<SinfoniaService>
        private const val TAG = "Sinfonia/SinfoniaService"
        private const val NOTIFICATION_CHANNEL_ID = "SinfoniaForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_CODE = 0
        const val ACTION_BIND = "edu.cmu.cs.sinfonia.action.BIND"
        const val ACTION_START = "edu.cmu.cs.sinfonia.action.START"
        const val ACTION_STOP = "edu.cmu.cs.sinfonia.action.STOP"
        fun get(): SinfoniaService {
            return weakSelf.get()!!
        }
    }

    init {
        weakSelf = WeakReference(this)
    }
}