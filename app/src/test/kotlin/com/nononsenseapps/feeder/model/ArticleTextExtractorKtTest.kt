package com.nononsenseapps.feeder.model

import org.junit.Ignore
import org.junit.Test
import java.io.InputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleTextExtractorKtTest {
    @Test
    fun testBloggerCornucopia() {
        val fullArticle = extractArticleText(getResourceAsStream("article_cornucopia.html"),
                "Numera är jag tydligen så pass pseudokänd att jag i stort sett inte kan röra mig i en större stad utan att bli igenkänd, detta trots att jag inte skyltar med mitt foto på bloggens framsida")
        assert(fullArticle!!.startsWith("<div class=\"post-body entry-content\" id=\"post-body-1699731792956043249\" itemprop=\"articleBody\">"))
    }

    @Ignore
    @Test
    fun testFzLeadingImage() {
        val fullArticle = extractArticleText(
                getResourceAsStream("article_fz.html"),
                "[IMG] \t\t\t\t\t \t\t\t\tEurogamers källor bekräf"
        )
        assertFalse("Comment should not have been included") {
            fullArticle!!.contains("<div class=\"article-comments\">")
        }
        assertTrue("Top tag should be article") {
            fullArticle!!.startsWith("<div class=\"article-body primary-content content-gutter cg-b\">")
        }
    }

    private fun getResourceAsStream(filename: String): InputStream =
            javaClass.getResourceAsStream(filename)
}