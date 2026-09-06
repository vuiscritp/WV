package egeo.com.browser.api

import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Giữ danh sách WebSocket đang kết nối và phát (broadcast) sự kiện JSON tới
 * tất cả. Dùng chung cho mọi EgeoApiServer trong cùng tiến trình (mỗi hồ sơ 1
 * server, nên thực tế mỗi tiến trình chỉ có 1 EgeoApiServer + các socket của
 * nó tại 1 thời điểm).
 *
 * emit() có thể được gọi từ main thread (vd. từ WebViewClient khi trang chuyển
 * trang) - nhưng gửi qua socket là I/O mạng, Android CẤM làm trên main thread
 * (NetworkOnMainThreadException) dù là socket loopback. Vì vậy việc gửi thật
 * sự luôn được đẩy sang 1 thread nền riêng.
 */
object ApiEventBus {

    private val sockets: MutableSet<NanoWSD.WebSocket> =
        Collections.synchronizedSet(LinkedHashSet())

    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "egeo-api-eventbus").apply { isDaemon = true }
    }

    fun register(socket: NanoWSD.WebSocket) {
        sockets.add(socket)
    }

    fun unregister(socket: NanoWSD.WebSocket) {
        sockets.remove(socket)
    }

    fun emit(type: String, data: JSONObject = JSONObject()) {
        if (sockets.isEmpty()) return // tránh tốn 1 lượt chuyển thread khi không ai đang nghe
        val payload = JSONObject()
        payload.put("type", type)
        payload.put("timestamp", System.currentTimeMillis())
        payload.put("data", data)
        val message = payload.toString()

        ioExecutor.execute {
            val deadSockets = mutableListOf<NanoWSD.WebSocket>()
            synchronized(sockets) {
                sockets.forEach { socket ->
                    try {
                        socket.send(message)
                    } catch (e: Exception) {
                        deadSockets.add(socket)
                    }
                }
                sockets.removeAll(deadSockets.toSet())
            }
        }
    }
}
