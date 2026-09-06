package egeo.com.browser.api

import fi.iki.elonen.NanoWSD
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Server local cho automation/điều khiển browser từ bên ngoài (Postman, script,
 * Node...). CHỈ nghe ở 127.0.0.1 (loopback) - đây là lớp bảo vệ chính chống
 * truy cập từ LAN, được thực hiện ở cấp socket (constructor NanoHTTPD nhận
 * hostname), không phải chỉ kiểm tra logic.
 *
 * Mọi endpoint đều yêu cầu header: Authorization: Bearer <api_key>
 * (trừ việc nâng cấp WebSocket, dùng query param ?key=... vì handshake
 * WebSocket khó gắn header tùy ý từ một số client).
 */
class EgeoApiServer(
    hostname: String,
    port: Int,
    private val apiKeyStore: ApiKeyStore
) : NanoWSD(hostname, port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (isWebSocketUpgradeRequest(session)) {
                if (!isAuthorizedForWebSocket(session)) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Unauthorized")
                }
                // Việc bắt tay (handshake) WebSocket thật sự do NanoWSD.serve()
                // (lớp cha) xử lý, nó sẽ tự gọi openWebSocket() bên dưới.
                super.serve(session)
            } else {
                routeRest(session)
            }
        } catch (e: Exception) {
            jsonError(Response.Status.INTERNAL_ERROR, "internal_error", e.message ?: "unknown")
        }
    }

    /**
     * Tự kiểm tra header chuẩn RFC 6455 (Connection: Upgrade + Upgrade: websocket)
     * thay vì gọi thẳng 1 API cụ thể của NanoWSD - tránh phụ thuộc vào đúng tên
     * hàm/API nội bộ của thư viện (rủi ro sai tên đã từng gặp). Việc bắt tay
     * WebSocket thật sự vẫn do super.serve() của NanoWSD đảm nhiệm.
     */
    private fun isWebSocketUpgradeRequest(session: IHTTPSession): Boolean {
        val connection = session.headers.entries
            .firstOrNull { it.key.equals("connection", ignoreCase = true) }?.value
        val upgrade = session.headers.entries
            .firstOrNull { it.key.equals("upgrade", ignoreCase = true) }?.value
        return connection?.contains("upgrade", ignoreCase = true) == true &&
            upgrade?.contains("websocket", ignoreCase = true) == true
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        return EgeoWebSocket(handshake)
    }

    private inner class EgeoWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            ApiEventBus.register(this)
        }

        override fun onClose(
            code: WebSocketFrame.CloseCode?,
            reason: String?,
            initiatedByRemote: Boolean
        ) {
            ApiEventBus.unregister(this)
        }

        override fun onMessage(message: WebSocketFrame) {
            // Phase 3 chỉ phát sự kiện 1 chiều (server -> client), không xử lý
            // lệnh gửi lên qua WebSocket - việc điều khiển dùng REST ở trên.
        }

        override fun onPong(pong: WebSocketFrame) {
            // no-op
        }

        override fun onException(exception: IOException) {
            ApiEventBus.unregister(this)
        }
    }

    // -----------------------------------------------------------------
    // REST
    // -----------------------------------------------------------------

    private fun routeRest(session: IHTTPSession): Response {
        if (!isAuthorized(session)) {
            return jsonError(Response.Status.UNAUTHORIZED, "unauthorized", "Thiếu hoặc sai API key")
        }

        val uri = session.uri.trimEnd('/')
        val method = session.method

        return when {
            uri == "/api/v1/status" && method == Method.GET ->
                jsonOk(BrowserApiBridge.status(listeningPort))

            uri == "/api/v1/tabs" && method == Method.GET -> {
                val tabs = BrowserApiBridge.listTabs()
                if (tabs == null) browserNotOpen() else jsonOk(wrap("tabs", tabs))
            }

            uri == "/api/v1/tabs" && method == Method.POST -> {
                val tab = BrowserApiBridge.createTab()
                if (tab == null) browserNotOpen() else jsonOk(tab)
            }

            uri == "/api/v1/tabs" && method == Method.DELETE -> {
                val id = session.parms["id"]?.toLongOrNull()
                    ?: return jsonError(Response.Status.BAD_REQUEST, "bad_request", "Thiếu tham số id")
                val ok = BrowserApiBridge.closeTab(id)
                if (!BrowserApiBridge.isBrowserOpen()) browserNotOpen()
                else if (!ok) jsonError(Response.Status.NOT_FOUND, "not_found", "Không tìm thấy tab")
                else jsonOk(JSONObject().put("closed", true))
            }

            uri == "/api/v1/tabs/switch" && method == Method.POST -> {
                val body = readJsonBody(session)
                val id = body.optLong("id", -1L)
                if (id < 0) return jsonError(Response.Status.BAD_REQUEST, "bad_request", "Thiếu id")
                val ok = BrowserApiBridge.switchTab(id)
                if (!BrowserApiBridge.isBrowserOpen()) browserNotOpen()
                else if (!ok) jsonError(Response.Status.NOT_FOUND, "not_found", "Không tìm thấy tab")
                else jsonOk(JSONObject().put("switched", true))
            }

            uri == "/api/v1/tabs/navigate" && method == Method.POST -> {
                val body = readJsonBody(session)
                val id = body.optLong("id", -1L)
                val url = body.optString("url", "")
                if (id < 0 || url.isBlank()) {
                    return jsonError(Response.Status.BAD_REQUEST, "bad_request", "Thiếu id hoặc url")
                }
                val ok = BrowserApiBridge.navigateTab(id, url)
                if (!BrowserApiBridge.isBrowserOpen()) browserNotOpen()
                else if (!ok) jsonError(Response.Status.NOT_FOUND, "not_found", "Không tìm thấy tab")
                else jsonOk(JSONObject().put("navigated", true))
            }

            uri == "/api/v1/profiles" && method == Method.GET ->
                jsonOk(wrap("profiles", egeo.com.browser.profile.ProfileHolder.ALL_PROFILE_IDS.let {
                    val arr = org.json.JSONArray()
                    it.forEach { id ->
                        arr.put(
                            JSONObject()
                                .put("id", id)
                                .put("name", egeo.com.browser.profile.ProfileHolder.displayName(id))
                                .put("current", id == egeo.com.browser.profile.ProfileHolder.currentProfileId)
                        )
                    }
                    arr
                }))

            else -> jsonError(Response.Status.NOT_FOUND, "not_found", "Không có endpoint này")
        }
    }

    private fun browserNotOpen(): Response =
        jsonError(Response.Status.NOT_FOUND, "browser_not_open", "Chưa có màn hình trình duyệt nào đang mở ở hồ sơ này")

    private fun wrap(key: String, value: Any): JSONObject = JSONObject().put(key, value)

    private fun readJsonBody(session: IHTTPSession): JSONObject {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val raw = files["postData"] ?: return JSONObject()
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } catch (e: JSONException) {
            JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val token = bearerToken(session)
        return apiKeyStore.validate(token)
    }

    private fun isAuthorizedForWebSocket(session: IHTTPSession): Boolean {
        val token = session.parms["key"] ?: bearerToken(session)
        return apiKeyStore.validate(token)
    }

    private fun bearerToken(session: IHTTPSession): String? {
        val header = session.headers.entries.firstOrNull { it.key.equals("authorization", ignoreCase = true) }
            ?.value ?: return null
        return if (header.startsWith("Bearer ", ignoreCase = true)) header.substring(7).trim() else null
    }

    private fun jsonOk(data: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", data.toString())

    private fun jsonError(status: Response.Status, code: String, message: String): Response {
        val o = JSONObject().put("error", code).put("message", message)
        return newFixedLengthResponse(status, "application/json", o.toString())
    }
}
