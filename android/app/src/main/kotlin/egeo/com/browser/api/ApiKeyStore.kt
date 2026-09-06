package egeo.com.browser.api

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

/**
 * Lưu API key ở SharedPreferences riêng theo TỪNG HỒ SƠ (profileId truyền vào
 * constructor, không tự đọc ProfileHolder.currentProfileId bên trong, vì lớp
 * này có thể được tạo từ 1 tiến trình khác - vd. màn Cài đặt luôn chạy ở tiến
 * trình mặc định bất kể người dùng đang ở hồ sơ nào). Nhờ vậy mỗi hồ sơ có bộ
 * API key độc lập thật sự, không lẫn giữa các hồ sơ.
 */
class ApiKeyStore(context: Context, profileId: String) {

    private val prefs = context.getSharedPreferences("$PREFS_PREFIX$profileId", Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    fun listKeys(): List<ApiKey> = loadAll().sortedByDescending { it.createdAt }

    fun generate(label: String, ttlMillis: Long?): ApiKey {
        val now = System.currentTimeMillis()
        val key = ApiKey(
            id = UUID.randomUUID().toString().take(8),
            token = randomToken(),
            label = label.ifBlank { "Khóa không tên" },
            createdAt = now,
            expiresAt = ttlMillis?.let { now + it },
            revoked = false
        )
        val all = loadAll()
        all.add(key)
        saveAll(all)
        return key
    }

    fun revoke(id: String) {
        val all = loadAll()
        val index = all.indexOfFirst { it.id == id }
        if (index == -1) return
        all[index] = all[index].copy(revoked = true)
        saveAll(all)
    }

    /** true nếu token hợp lệ (tồn tại, chưa thu hồi, chưa hết hạn). */
    fun validate(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return loadAll().any { it.token == token && it.isValid }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun loadAll(): MutableList<ApiKey> {
        val raw = prefs.getString(KEY_STORAGE, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<ApiKey>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(
                    ApiKey(
                        id = o.getString("id"),
                        token = o.getString("token"),
                        label = o.getString("label"),
                        createdAt = o.getLong("createdAt"),
                        expiresAt = if (o.isNull("expiresAt")) null else o.getLong("expiresAt"),
                        revoked = o.optBoolean("revoked", false)
                    )
                )
            }
            result
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveAll(keys: List<ApiKey>) {
        val arr = JSONArray()
        keys.forEach { k ->
            val o = JSONObject()
            o.put("id", k.id)
            o.put("token", k.token)
            o.put("label", k.label)
            o.put("createdAt", k.createdAt)
            o.put("expiresAt", k.expiresAt)
            o.put("revoked", k.revoked)
            arr.put(o)
        }
        prefs.edit().putString(KEY_STORAGE, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_PREFIX = "egeo_api_keys_"
        private const val KEY_STORAGE = "keys_json"

        // Vài tuỳ chọn thời hạn thường dùng cho UI.
        const val TTL_1_HOUR = 60 * 60 * 1000L
        const val TTL_1_DAY = 24 * TTL_1_HOUR
        const val TTL_7_DAYS = 7 * TTL_1_DAY
        const val TTL_30_DAYS = 30 * TTL_1_DAY
    }
}
