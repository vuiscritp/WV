package egeo.com.browser.theme

/**
 * 5 lựa chọn hiển thị cho người dùng: SYSTEM đi theo dark mode của Android,
 * 4 lựa chọn còn lại là cố định.
 */
enum class ThemeMode {
    SYSTEM,
    MORNING,
    DAY,
    NIGHT,
    MIDNIGHT;

    companion object {
        fun fromPref(value: String?): ThemeMode =
            values().find { it.name == value } ?: SYSTEM
    }
}
