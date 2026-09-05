package egeo.com.browser

import android.app.Application
import android.os.Build
import android.webkit.WebView
import egeo.com.browser.profile.ProfileHolder
import egeo.com.browser.util.ProcessUtils

class EgeoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val processName = ProcessUtils.currentProcessName(this)
        val profileId = ProcessUtils.profileIdFromProcessName(processName, packageName)
        ProfileHolder.init(profileId)

        // Mỗi hồ sơ (profile) chạy trong 1 tiến trình riêng (khai báo android:process
        // trong Manifest). setDataDirectorySuffix phải gọi ĐÚNG 1 LẦN, TRƯỚC khi bất kỳ
        // WebView nào được tạo trong tiến trình đó, để cookie/localStorage/cache của
        // từng hồ sơ nằm ở thư mục dữ liệu tách biệt thật sự trên đĩa.
        //
        // API yêu cầu Android 9 (P, API 28) trở lên. Dưới mức đó, Android không hỗ trợ
        // dùng WebView an toàn ở tiến trình phụ trong cùng 1 app -> các hồ sơ phụ
        // (p1/p2/p3) sẽ không khả dụng, ProfileManager sẽ tự ẩn lựa chọn này.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && profileId != ProfileHolder.DEFAULT_PROFILE_ID) {
            try {
                WebView.setDataDirectorySuffix(profileId)
            } catch (e: IllegalStateException) {
                // WebView đã được khởi tạo trước đó trong tiến trình này (không nên xảy ra
                // nếu Application.onCreate() luôn chạy trước mọi Activity) -> bỏ qua an toàn.
            }
        }
    }
}
