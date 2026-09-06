package egeo.com.browser.profile

import android.content.Context
import android.content.Intent
import android.os.Build
import egeo.com.browser.MainActivity
import egeo.com.browser.MainActivityP1
import egeo.com.browser.MainActivityP2
import egeo.com.browser.MainActivityP3
import egeo.com.browser.api.ApiServerService
import egeo.com.browser.api.ApiServerServiceP1
import egeo.com.browser.api.ApiServerServiceP2
import egeo.com.browser.api.ApiServerServiceP3

object ProfileManager {

    /** Hồ sơ phụ (p1/p2/p3) cần Android 9 (API 28) trở lên vì lý do kỹ thuật của WebView. */
    fun secondaryProfilesSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun availableProfileIds(): List<String> {
        return if (secondaryProfilesSupported()) {
            ProfileHolder.ALL_PROFILE_IDS
        } else {
            listOf(ProfileHolder.DEFAULT_PROFILE_ID)
        }
    }

    /** Mở app ở hồ sơ chỉ định (tiến trình riêng), đóng activity hiện tại. */
    fun switchTo(context: Context, profileId: String) {
        val targetClass = when (profileId) {
            "p1" -> MainActivityP1::class.java
            "p2" -> MainActivityP2::class.java
            "p3" -> MainActivityP3::class.java
            else -> MainActivity::class.java
        }
        val intent = Intent(context, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }

    /** Class Service Local-API tương ứng với hồ sơ - mỗi hồ sơ 1 tiến trình riêng
     * nên cần đúng Service khai báo android:process khớp mới điều khiển được
     * đúng tab đang mở ở hồ sơ đó. */
    fun apiServiceClassFor(profileId: String): Class<out ApiServerService> = when (profileId) {
        "p1" -> ApiServerServiceP1::class.java
        "p2" -> ApiServerServiceP2::class.java
        "p3" -> ApiServerServiceP3::class.java
        else -> ApiServerService::class.java
    }
}
