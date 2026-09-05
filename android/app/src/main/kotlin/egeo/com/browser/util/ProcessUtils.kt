package egeo.com.browser.util

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Lấy tên tiến trình Android hiện tại. Từ API 28 có Application.getProcessName()
 * dùng thẳng; dưới đó phải dò qua ActivityManager theo pid.
 */
object ProcessUtils {

    fun currentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName() ?: context.packageName
        }
        val pid = Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val match = am?.runningAppProcesses?.firstOrNull { it.pid == pid }
        return match?.processName ?: context.packageName
    }

    /**
     * Suy ra id hồ sơ (profile) từ tên tiến trình.
     * "egeo.com.browser" -> "default"
     * "egeo.com.browser:p1" -> "p1"
     */
    fun profileIdFromProcessName(processName: String, packageName: String): String {
        if (processName == packageName) return "default"
        val suffix = processName.substringAfter(':', missingDelimiterValue = "")
        return suffix.ifBlank { "default" }
    }
}
