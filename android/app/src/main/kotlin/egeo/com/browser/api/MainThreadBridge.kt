package egeo.com.browser.api

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * EgeoApiServer (NanoHTTPD) xử lý mỗi request trên 1 thread riêng của nó,
 * KHÔNG phải main thread. Nhưng TabManager/WebView chỉ được đụng vào từ main
 * thread. Hàm này post khối lệnh lên main thread và chặn (block) thread gọi
 * cho tới khi xong hoặc hết thời gian chờ, để trả kết quả về ngay trong cùng
 * 1 request HTTP.
 */
object MainThreadBridge {

    private val handler = Handler(Looper.getMainLooper())

    fun <T> runBlocking(timeoutMs: Long = 3000L, block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }
        val latch = CountDownLatch(1)
        var result: T? = null
        handler.post {
            try {
                result = block()
            } finally {
                latch.countDown()
            }
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return result
    }
}
