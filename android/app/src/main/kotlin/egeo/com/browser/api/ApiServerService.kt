package egeo.com.browser.api

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import egeo.com.browser.MainActivity
import egeo.com.browser.R

/**
 * Chạy EgeoApiServer trong 1 foreground service, để server tiếp tục hoạt động
 * cả khi màn hình tắt hoặc app xuống nền (đúng yêu cầu "chạy nền"). Không có
 * android:process riêng ở class này - từng hồ sơ (P1/P2/P3) có subclass +
 * khai báo <service> riêng với process khớp, xem ApiServerServiceP1/2/3.kt.
 */
open class ApiServerService : Service() {

    private var server: EgeoApiServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        startForeground(NOTIFICATION_ID, buildNotification(port))
        startServer(port)
        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun startServer(port: Int) {
        stopServer()
        // ApiServerService (và các biến thể P1/P2/P3) luôn chạy đúng tiến trình
        // của hồ sơ tương ứng (khai báo android:process trong Manifest), nên
        // ProfileHolder.currentProfileId ở ĐÂY là đáng tin cậy (khác với
        // SettingsActivity/DiagnosticsActivity luôn chạy ở tiến trình mặc định).
        val store = ApiKeyStore(this, egeo.com.browser.profile.ProfileHolder.currentProfileId)
        val newServer = EgeoApiServer("127.0.0.1", port, store)
        try {
            newServer.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = newServer
        } catch (e: Exception) {
            // Cổng có thể đã bị chiếm hoặc lỗi khác - dừng service, không giữ
            // 1 service "sống" mà không có server thật bên trong.
            stopSelf()
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun buildNotification(port: Int): Notification {
        ensureChannel()
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = androidx.core.app.TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(openAppIntent)
            .getPendingIntent(
                0,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.api_notification_title))
            .setContentText(getString(R.string.api_notification_text, port))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.api_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val EXTRA_PORT = "extra_port"
        const val DEFAULT_PORT = 8888
        private const val NOTIFICATION_ID = 4201
        private const val CHANNEL_ID = "egeo_api_server"
    }
}
