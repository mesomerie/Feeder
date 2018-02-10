package com.nononsenseapps.feeder.model

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.InputStream


@Throws(Exception::class)
fun extractArticleTextRecursively(input: InputStream, plaintTextSnippet: String): String? {
    val indicator = plaintTextSnippet.split(Regex("\\[[^]]*]")).fold("") { acc, s ->
        when (s.trim().length > acc.length) {
            true -> s.trim()
            false -> acc
        }
    }
    return extractArticleTextWithIndicator(input, indicator)
}

@Throws(Exception::class)
private fun extractArticleTextWithIndicator(input: InputStream, contentIndicator: String): String? {
    val document: Document? = Jsoup.parse(input, null, "")
    if (document != null) {
        return runBlocking {
            extractContent(document, contentIndicator).await()
        }
    }
    return null
}

private fun extractContent(doc: Document, contentIndicator: String): Deferred<String?> = async(CommonPool) {
    // now remove the clutter
    prepareDocument(doc)

    // init elements
    val nodes = getInterestingNodes(doc)
    //val nodes = doc.body().children()
    val initAcc: Pair<Element?, Int> = (null to Int.MIN_VALUE)

    nodes.map {
        async(CommonPool) {
            it to getElementWeight(it, contentIndicator)
        }
    }.map {
                it.await()
            }.fold(initAcc) { acc, pair ->

                val weight = pair.second

                val maxWeight = acc.second

                when (weight > maxWeight) {
                    true -> pair
                    false -> acc
                }
            }.first?.toString()
}

suspend fun getElementWeight(e: Element, contentIndicator: String): Int {
    val selfWeight = async(CommonPool) { getSelfWeight(e, contentIndicator) }

    val childWeights = e.children().map { child ->
        async(CommonPool) {
            getElementWeight(child, contentIndicator)
        }
    }

    return selfWeight.await() + childWeights.fold(0) { acc, deferred ->
        acc + deferred.await()
    }
}

suspend fun getSelfWeight(e: Element, contentIndicator: String): Int {
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

    weight += Math.round(e.ownText().length / 10.0).toInt()

    if (e.ownText().contains(contentIndicator)) {
        weight += 100 // We certainly found the item
    }

    return weight
}

private suspend fun prepareDocument(doc: Document) {
    doc.getElementsByTag("script").forEach { item -> item.remove() }
    doc.getElementsByTag("noscript").forEach { item -> item.remove() }
    doc.getElementsByTag("style").forEach { style -> style.remove() }
}
