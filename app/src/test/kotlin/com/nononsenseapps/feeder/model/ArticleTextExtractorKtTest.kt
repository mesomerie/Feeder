package com.nononsenseapps.feeder.model

import org.jsoup.Jsoup
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

    @Test
    fun testFzLeadingImage() {
        val fullArticle = extractArticleText(
                getResourceAsStream("article_fz.html"),
                "[IMG] \t\t\t\t\t \t\t\t\tEurogamers källor bekräf"
        )
        assertTrue("Should contain end of article") {
            fullArticle!!.contains("Varken Nintendo eller Bandai Namco har bekr&auml;ftat dessa rykten")
        }
        assertTrue("Should contain start of article") {
            fullArticle!!.contains("Eurogamers k&auml;llor bekr&auml;ftar veckans rykten")
        }
        assertFalse("Comment should not have been included") {
            fullArticle!!.contains("<div class=\"article-comments\">")
        }
    }

    @Test
    fun testSlashdot() {
        val fullArticle = extractArticleText(
                getResourceAsStream("article_slashdot.html"),
                "The Washington Post: Melissa Hobley, an executive at the dating app OkCupid, hears the complaints about the apps [being unable to find good matches] regularly and thinks they get a bad rap. Silicon Valley workers \"are in the business of scalable, quick solutions. And that's not what love is,\" Hobley said. \"You can't hurry love. It's reciprocal. You're not ordering an object. You're not getting a delivery in less than seven minutes.\" Finding love, she added, takes commitment and energy -- and, yes, time, no matter how inefficiently it's spent. \"You have a whole city obsessed with algorithms and data, and they like to say dating apps aren't solving the problem,\" Hobley said. \"But if a city is male-dominant, if a city is known for 16-hour work days, those are issues that dating apps can't solve.\" One thing distinguishes the Silicon Valley dating pool: The men-to-women ratio for employed, young singles in the San Jose metro area is higher than in any other major area. There were about 150 men for every 100 women, compared with about 125 to 100 nationwide, of never-married young people between 25 and 34 in San Jose, U.S. Census Bureau data from 2016 shows. That ratio permeates the economy here, all the way to the valley's biggest employers, which have struggled for years to bring more women into their ranks. Men make up about 70% of the workforces of "
        )
        val plain = Jsoup.parse(fullArticle).body().text().trim()
        assertTrue("Should end with end of article") {
            plain.endsWith("company filings show.")
        }
        assertTrue("Should start with start of article") {
            plain.startsWith("The Washington Post:")
        }
    }

    @Test
    fun testLibreElec() {
        val fullArticle = extractArticleText(
                getResourceAsStream("article_libreelec.html"),
                "LibreELEC 8.2.3 is released to change our embedded pastebin provider from sprunge.us (RIP) to ix.io (working) so users can continue to submit logs"
        )
        val plain = Jsoup.parse(fullArticle).body().text().trim()
        assertTrue("Should end with end of article") {
            plain.endsWith("Click to share on Reddit (Opens in new window)")
        }
        assertTrue("Should start with start of article") {
            plain.startsWith("LibreELEC 8.2.3 is released to change our embedded pastebin")
        }
    }

    private fun getResourceAsStream(filename: String): InputStream =
            javaClass.getResourceAsStream(filename)
}
