package com.example.nyxa_interview.presentation.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Product ids look like "gid://shopify/Product/0". Navigation-Compose's default String NavType
 * splits path segments on '/', so passing an un-encoded id into a "product/{id}" route crashes
 * with "Navigation destination ... cannot be found in the navigation graph" (reproduced live on
 * device tapping a product card). Round-tripping through URL encode/decode is the fix.
 */
class ProductRouteEncodingTest {

    @Test
    fun `a product id containing slashes and colons round-trips through encode-decode unchanged`() {
        val productId = "gid://shopify/Product/0"

        val encoded = URLEncoder.encode(productId, StandardCharsets.UTF_8.name())
        val decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())

        assertThat(encoded).doesNotContain("/")
        assertThat(decoded).isEqualTo(productId)
    }
}
