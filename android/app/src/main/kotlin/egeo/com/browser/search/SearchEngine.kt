package egeo.com.browser.search

import java.net.URLEncoder

/**
 * Trừu tượng hoá công cụ tìm kiếm. Thêm engine mới = thêm 1 object vào ALL.
 *
 * Dùng java.net.URLEncoder (thuần Kotlin/JVM) thay vì android.net.Uri để
 * class này chạy được trong unit test JVM thường, không cần Android framework
 * hay Robolectric.
 */
sealed class SearchEngine(val id: String, val displayName: String, private val queryUrlTemplate: String) {

    fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        return queryUrlTemplate.replace(QUERY_PLACEHOLDER, encoded)
    }

    object Google : SearchEngine(
        id = "google",
        displayName = "Google",
        queryUrlTemplate = "https://www.google.com/search?q=$QUERY_PLACEHOLDER"
    )

    object Bing : SearchEngine(
        id = "bing",
        displayName = "Bing",
        queryUrlTemplate = "https://www.bing.com/search?q=$QUERY_PLACEHOLDER"
    )

    object CocCoc : SearchEngine(
        id = "coccoc",
        displayName = "Cốc Cốc",
        queryUrlTemplate = "https://coccoc.com/search?query=$QUERY_PLACEHOLDER"
    )

    object DuckDuckGo : SearchEngine(
        id = "duckduckgo",
        displayName = "DuckDuckGo",
        queryUrlTemplate = "https://duckduckgo.com/?q=$QUERY_PLACEHOLDER"
    )

    companion object {
        private const val QUERY_PLACEHOLDER = "%QUERY%"

        val ALL: List<SearchEngine> = listOf(Google, Bing, CocCoc, DuckDuckGo)

        fun byId(id: String?): SearchEngine = ALL.find { it.id == id } ?: Google
    }
}
