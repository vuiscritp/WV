package egeo.com.browser.profile

/**
 * Id hồ sơ của tiến trình hiện tại, set 1 lần trong Application.onCreate().
 * Mỗi hồ sơ chạy trong 1 tiến trình Android riêng (xem EgeoApplication +
 * AndroidManifest), nên đây chỉ là giá trị đọc-only cho phần còn lại của
 * code trong CÙNG tiến trình đó dùng để hiển thị / đặt tên file prefs.
 */
object ProfileHolder {
    const val DEFAULT_PROFILE_ID = "default"

    @Volatile
    var currentProfileId: String = DEFAULT_PROFILE_ID
        private set

    fun init(profileId: String) {
        currentProfileId = profileId.ifBlank { DEFAULT_PROFILE_ID }
    }

    /** Tên hiển thị cho người dùng. */
    fun displayName(profileId: String): String = when (profileId) {
        DEFAULT_PROFILE_ID -> "Mặc định"
        "p1" -> "Hồ sơ 1"
        "p2" -> "Hồ sơ 2"
        "p3" -> "Hồ sơ 3"
        else -> profileId
    }

    val ALL_PROFILE_IDS = listOf(DEFAULT_PROFILE_ID, "p1", "p2", "p3")
}
