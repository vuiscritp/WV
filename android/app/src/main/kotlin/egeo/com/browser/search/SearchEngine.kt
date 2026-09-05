package egeo.com.browser.search

import java.net.URLEncoder

private const val QUERY_PLACEHOLDER = "%QUERY%"

/**
 * Trừu tượng hoá công cụ tìm kiếm.
 *
 * Cố tình KHÔNG dùng companion object cho ALL/byId (đã từng gây NullPointerException
 * khó chẩn đoán khi kết hợp sealed class + companion + object lồng trên một số
 * cấu hình JVM/Kotlin). Thay vào đó, danh sách và hàm tra cứu là top-level,
 * đơn giản và không phụ thuộc thứ tự khởi tạo giữa các class.
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
}

val ALL_SEARCH_ENGINES: List<SearchEngine> = listOf(
    SearchEngine.Google,
    SearchEngine.Bing,
    SearchEngine.CocCoc,
    SearchEngine.DuckDuckGo
)

fun searchEngineById(id: String?): SearchEngine =
    ALL_SEARCH_ENGINES.find { it.id == id } ?: SearchEngine.Google
