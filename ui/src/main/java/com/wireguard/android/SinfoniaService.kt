package com.wireguard.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.util.ErrorMessages
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

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: $intent")
        if (intent != null) {
            createNotificationChannel()
            val notification: Notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            when (intent.action) {
                "edu.cmu.cs.sinfonia.action.START" -> handleActionSinfonia(intent)
                "edu.cmu.cs.sinfonia.action.STOP" -> onDestroy()
            }
        }
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
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
                    Log.e(TAG, e.toString())
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

    private fun handleActionSinfonia(intent: Intent) {
        Log.i(TAG, "handleActionSinfonia")

        scope.launch {
            sinfonia = SinfoniaTier3(
                    ctx = Application.get(),
                    url = intent.getStringExtra("url") ?: "https://cmu.findcloudlet.org",
                    applicationName = intent.getStringExtra("applicationName") ?: "helloworld",
                    zeroconf = intent.getBooleanExtra("zeroconf", false),
                    application = intent.getStringArrayListExtra("application") ?: listOf("com.android.chrome")
            ).deploy()

            Log.d(TAG, "handleActionSinfonia deployed: $sinfonia")

            if (createTunnel()) setTunnelState(true) else return@launch

            try {
                sinfonia?.application?.forEach { application: String -> launchApplication(application) }
            } catch (e: Throwable) {
                Log.e(TAG, "Cannot launch application: ${sinfonia?.applicationName}", e)
            }
        }
    }

    private suspend fun createTunnel(): Boolean {
        val newConfig = try {
            sinfonia?.deployment?.tunnelConfig
        } catch (e: Throwable) {
            val error = ErrorMessages[e]
            val tunnelName = if (tunnel == null) sinfonia?.applicationName else tunnel!!.name
            val message = getString(R.string.config_save_error, tunnelName, error)
            Log.e(TAG, message, e)
            return false
        }

        Log.d(TAG, "handleActionSinfonia newConfig: $newConfig")

        try {
            val manager = Application.getTunnelManager()
            onTunnelCreated(manager.create(sinfonia?.applicationName!!, newConfig), null)
        } catch (e: Throwable) {
            onTunnelCreated(null, e)
            return false
        }
        return true
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
                "edu.cmu.cs.sinfonia.action.STOP" -> onDestroy()
            }
        }
    }

    companion object {
        private const val TAG = "WireGuard/SinfoniaService"
        private const val NOTIFICATION_CHANNEL_ID = "SinfoniaForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_CODE = 0
    }
}