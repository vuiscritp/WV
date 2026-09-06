package egeo.com.browser.api

data class ApiKey(
    val id: String,
    val token: String,
    val label: String,
    val createdAt: Long,
    /** null = vô thời hạn */
    val expiresAt: Long?,
    val revoked: Boolean
) {
    val isExpired: Boolean
        get() = expiresAt != null && System.currentTimeMillis() > expiresAt

    val isValid: Boolean
        get() = !revoked && !isExpired

    /** Chỉ hiện vài ký tự đầu/cuối khi hiển thị cho người dùng, không lộ toàn bộ token. */
    val maskedToken: String
        get() = if (token.length <= 10) token else "${token.take(6)}…${token.takeLast(4)}"
}
