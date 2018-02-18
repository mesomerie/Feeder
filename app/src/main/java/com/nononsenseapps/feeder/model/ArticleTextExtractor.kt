package com.nononsenseapps.feeder.model

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.InputStream

/**
 * Original code by authors below.
 *
 * @author Alex P (ifesdjeen from jreadability)
 * @author Peter Karich
 */

private val HEADERS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

// Interesting nodes
private val INTERESTING_NODES = setOf("p", "div", "td", "h1", "h2", "article", "section")

// Unlikely candidates
internal val UNLIKELY = Regex("com(bx|ment|munity)|dis(qus|cuss)|e(xtra|[-]?mail)|foot|"
        + "header|menu|re(mark|ply)|rss|sh(are|outbox)|sponsor"
        + "a(d|ll|gegate|ggregate|rchive|ttachment)|(pag(er|ination))|popup|print|"
        + "login|si(debar|gn|ngle)")

// Most likely positive candidates
internal val POSITIVE = Regex("(^(body|content|h?entry|main|page|post|text|blog|story|haupt))"
        + "|arti(cle|kel)|instapaper_body")

// Most likely negative candidates
internal val NEGATIVE = Regex("nav($|igation)|user|com(ment|bx)|(^com-)|contact|"
        + "foot|masthead|(me(dia|ta))|outbrain|promo|related|scroll|(sho(utbox|pping))|"
        + "sidebar|sponsor|tags|tool|widget|player|disclaimer|toc|infobox|vcard")

internal val NEGATIVE_STYLE = Regex("hidden|display: ?none|font-size: ?small")

/**
 * @param input extracts article text from given html string. wasn't tested with improper HTML,
 * although jSoup should be able to handle minor stuff.
 * @param plaintTextSnippet plaintext snippet of Feeditem. Will strip plain text representations of
 * images and trim the text.
 * @throws Exception if something else goes wrong
 */
@Throws(Exception::class)
fun extractArticleText(input: InputStream, plaintTextSnippet: String): String? {
    val indicator = plaintTextSnippet.split(Regex("\\[[^]]*]")).fold("") { acc, s ->
        when (s.trim().length > acc.length) {
            true -> s.trim()
            false -> acc
        }
    }
    return extractArticleTextWithIndicator(input, indicator.take(40))
}

/**
 * @param input extracts article text from given html string. wasn't tested with improper HTML,
 * although jSoup should be able to handle minor stuff.
 * @param contentIndicator a text which should be included into the extracted content, or null
 * @return extracted article, all HTML tags stripped, or NULL if parsing failed
 * @throws Exception if something else goes wrong
 */
@Throws(Exception::class)
private fun extractArticleTextWithIndicator(input: InputStream, contentIndicator: String): String? {
    val document: Document? = Jsoup.parse(input, null, "")
    if (document != null) {
        return extractContent(document, contentIndicator)
    }
    return null
}

private fun extractContent(doc: Document, contentIndicator: String): String? {
    // now remove the clutter
    prepareDocument(doc)

    // init elements
    val nodes = getInterestingNodes(doc)
    var maxWeight = 0
    var bestMatchElement: Element? = null

    for (entry in nodes) {
        val currentWeight = getWeight(entry, contentIndicator)
        if (currentWeight > maxWeight) {
            maxWeight = currentWeight
            bestMatchElement = entry

            if (maxWeight > 300) {
                break
            }
        }
    }

    return bestMatchElement?.toString()
}

/**
 * Weights current element. By matching it with positive candidates and
 * weighting child nodes. Since it's impossible to predict which exactly
 * names, ids or class names will be used in HTML, major role is played by
 * child nodes
 *
 * @param e                Element to weight, along with child nodes
 * @param contentIndicator a text which should be included into the extracted content, or null
 */
private fun getWeight(e: Element, contentIndicator: String): Int {
    var weight = calcWeight(e)
    weight += Math.round(e.ownText().length / 100.0 * 10).toInt()
    weight += weightChildNodes(e, contentIndicator)
    return weight
}

/**
 * Weights a child nodes of given Element. During tests some difficulties
 * were met. For instance, not every single document has nested paragraph
 * tags inside of the major article tag. Sometimes people are adding one
 * more nesting level. So, we're adding 4 points for every 100 symbols
 * contained in tag nested inside of the current weighted element, but only
 * 3 points for every element that's nested 2 levels deep. This way we give
 * more chances to extract the element that has less nested levels,
 * increasing probability of the correct extraction.
 *
 * @param rootEl           Element, who's child nodes will be weighted
 * @param contentIndicator a text which should be included into the extracted content
 */
private fun weightChildNodes(rootEl: Element, contentIndicator: String): Int {
    var weight = 0
    val pEls = mutableListOf<Element>()
    for (child in rootEl.children()) {
        val text = child.text()
        val textLength = text.length
        if (textLength < 20) {
            continue
        }

        if (text.contains(contentIndicator)) {
            weight += 100 // We certainly found the item
        }

        val ownText = child.ownText()
        val ownTextLength = ownText.length
        if (ownTextLength > 200) {
            weight += Math.max(50, ownTextLength / 10)
        }

        when (child.tagName()) {
            "h1", "h2" -> weight += 30
            "div", "p" -> {
                weight += calcWeightForChild(ownText)
                if (child.tagName() == "p" && textLength > 50) {
                    pEls.add(child)
                }
            }
        }
    }

    if (pEls.size >= 2) {
        rootEl.children()
                .filter { HEADERS.contains(it.tagName()) }
                .forEach { weight += 20 }
    }

    return weight
}

private fun calcWeightForChild(text: String): Int = text.length / 25

private fun calcWeight(e: Element): Int {
    var weight = 0
    if (POSITIVE.containsMatchIn(e.className())) {
        weight += 35
    }

    if (POSITIVE.containsMatchIn(e.id())) {
        weight += 40
    }

    if (UNLIKELY.containsMatchIn(e.className())) {
        weight -= 20
    }

    if (UNLIKELY.containsMatchIn(e.id())) {
        weight -= 20
    }

    if (NEGATIVE.containsMatchIn(e.className())) {
        weight -= 50
    }

    if (NEGATIVE.containsMatchIn(e.id())) {
        weight -= 50
    }

    val style = e.attr("style")
    if (style != null && !style.isEmpty() && NEGATIVE_STYLE.containsMatchIn(style)) {
        weight -= 50
    }
    return weight
}

/**
 * Prepares document. Currently only stipping unlikely candidates, since
 * from time to time they're getting more score than good ones especially in
 * cases when major text is short.
 */
private fun prepareDocument(doc: Document) {
    doc.getElementsByTag("script").forEach { item -> item.remove() }
    doc.getElementsByTag("noscript").forEach { item -> item.remove() }
    doc.getElementsByTag("style").forEach { style -> style.remove() }
}

internal fun getInterestingNodes(doc: Document): Set<Element> = doc
        .select("body")
        .select("*")
        .filter { INTERESTING_NODES.contains(it.tagName()) }
        .toSet()
