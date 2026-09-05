package egeo.com.browser.util

/**
 * WebView mặc định chèn marker "; wv)" vào User-Agent để đánh dấu đây là
 * WebView (không phải trình duyệt Chrome đầy đủ), ví dụ:
 *   Mozilla/5.0 (Linux; Android 13; Pixel 6 Build/TQ3A...; wv) AppleWebKit/...
 * Hàm này loại bỏ marker đó, giữ nguyên phần còn lại của chuỗi UA thật
 * (không giả mạo thiết bị/OS, chỉ bỏ token "wv").
 */
object UserAgentUtil {

    private const val WV_MARKER = "; wv)"

    fun withoutWvMarker(defaultUserAgent: String): String {
        return if (defaultUserAgent.contains(WV_MARKER)) {
            defaultUserAgent.replace(WV_MARKER, ")")
        } else {
            defaultUserAgent
        }
    }
}
