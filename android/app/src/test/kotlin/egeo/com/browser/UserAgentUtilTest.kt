package egeo.com.browser

import egeo.com.browser.search.SearchEngine
import egeo.com.browser.util.UserAgentUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserAgentUtilTest {

    @Test
    fun `removes wv marker when present`() {
        val input = "Mozilla/5.0 (Linux; Android 13; Pixel 6 Build/TQ3A.230901.001; wv) AppleWebKit/537.36"
        val result = UserAgentUtil.withoutWvMarker(input)
        assertFalse(result.contains("wv"))
        assertEquals(
            "Mozilla/5.0 (Linux; Android 13; Pixel 6 Build/TQ3A.230901.001) AppleWebKit/537.36",
            result
        )
    }

    @Test
    fun `leaves user agent unchanged when marker absent`() {
        val input = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
        assertEquals(input, UserAgentUtil.withoutWvMarker(input))
    }
}

class SearchEngineTest {

    @Test
    fun `builds google search url with encoded query`() {
        val url = SearchEngine.Google.buildSearchUrl("egeo browser")
        assertEquals("https://www.google.com/search?q=egeo%20browser", url)
    }

    @Test
    fun `byId falls back to google for unknown id`() {
        assertEquals(SearchEngine.Google, SearchEngine.byId("unknown-engine"))
    }

    @Test
    fun `byId resolves coccoc`() {
        assertEquals(SearchEngine.CocCoc, SearchEngine.byId("coccoc"))
    }
}
