package com.nononsenseapps.feeder.model

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.support.v4.app.NotificationCompat
import com.nononsenseapps.feeder.R
import com.nononsenseapps.feeder.coroutines.Background
import com.nononsenseapps.feeder.db.COL_FEED
import com.nononsenseapps.feeder.db.COL_ID
import com.nononsenseapps.feeder.db.COL_NOTIFIED
import com.nononsenseapps.feeder.db.COL_NOTIFY
import com.nononsenseapps.feeder.db.COL_UNREAD
import com.nononsenseapps.feeder.db.FeedItemSQL
import com.nononsenseapps.feeder.ui.EXTRA_FEEDITEMS_TO_MARK_AS_NOTIFIED
import com.nononsenseapps.feeder.ui.FeedActivity
import com.nononsenseapps.feeder.ui.ReaderActivity
import com.nononsenseapps.feeder.util.getFeedItems
import com.nononsenseapps.feeder.util.getFeeds
import com.nononsenseapps.feeder.util.notificationManager
import kotlinx.coroutines.experimental.launch


const val notificationId = 73583
const val channelId = "feederNotifications"

fun notifyInBackground(context: Context) {
    val appContext = context.applicationContext
    launch(Background) {
        val feedItems = getItemsToNotify(appContext)

        val notification: Notification? = when (feedItems.size) {
            0 -> null
            1 -> singleNotification(appContext, feedItems.first())
            else -> manyNotification(appContext, feedItems)
        }

        val nm = appContext.notificationManager
        when (notification) {
            null -> nm.cancel(notificationId)
            else -> nm.notify(notificationId, notification)
        }
    }
}

private fun singleNotification(context: Context, item: FeedItemSQL): Notification {
    val style = NotificationCompat.BigTextStyle()
    val title = item.plaintitle
    val text = item.plainsnippet

    style.bigText(text)
    style.setBigContentTitle(title)

    val intent = when (item.description.isBlank()) {
        true -> Intent(context, FeedActivity::class.java)
        false -> {
            val i = Intent(context, ReaderActivity::class.java)
            ReaderActivity.setRssExtras(i, item)
            i
        }
    }

    val builder = notificationBuilder(context)

    builder.setContentText(text)
            .setContentTitle(title)
            .setContentIntent(PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT))
            .setDeleteIntent(getDeleteIntent(context, listOf(item)))

    if (item.enclosurelink != null) {
        builder.addAction(R.drawable.ic_action_av_play_circle_outline,
                context.getString(R.string.open_enclosed_media), PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW,
                Uri.parse(item.enclosurelink)), PendingIntent.FLAG_UPDATE_CURRENT))
    }

    builder.addAction(R.drawable.ic_action_location_web_site,
            context.getString(R.string.open_link_in_browser),
            PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW,
                    Uri.parse(item.link)), PendingIntent.FLAG_UPDATE_CURRENT))

    style.setBuilder(builder)
    return style.build()
}

private fun manyNotification(context: Context, feedItems: List<FeedItemSQL>): Notification {
    val style = NotificationCompat.InboxStyle()
    val title = context.getString(R.string.updated_feeds)
    val text = feedItems.joinToString(separator = "\n") { "${it.feedtitle} \u2014 ${it.plaintitle}" }

    style.setBigContentTitle(title)
    feedItems.forEach {
        style.addLine("${it.feedtitle} \u2014 ${it.plaintitle}")
    }

    val intent = Intent(context, FeedActivity::class.java)
    intent.putExtra(EXTRA_FEEDITEMS_TO_MARK_AS_NOTIFIED, LongArray(feedItems.size, { i ->  feedItems[i].id }))

    val builder = notificationBuilder(context)

    builder.setContentText(text)
            .setContentTitle(title)
            .setContentIntent(PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT))
            .setDeleteIntent(getDeleteIntent(context, feedItems))

    style.setBuilder(builder)
    return style.build()
}

private fun getDeleteIntent(context: Context, feedItems: List<FeedItemSQL>): PendingIntent {
    val intent = Intent(context, RssNotificationBroadcastReceiver::class.java)
    intent.action = ACTION_MARK_AS_NOTIFIED

    val ids = LongArray(feedItems.size, { i ->  feedItems[i].id })
    intent.putExtra(EXTRA_FEEDITEM_ID_ARRAY, ids)

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}

private fun notificationBuilder(context: Context): NotificationCompat.Builder {
    val bm = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

    return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_f)
            .setLargeIcon(bm)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
}

private fun getItemsToNotify(context: Context): List<FeedItemSQL> {
    val feeds = getFeedIdsToNotify(context)

    return when (feeds.isEmpty()) {
        true -> emptyList()
        false -> context.contentResolver.getFeedItems(
                where = "$COL_FEED IN (${feeds.joinToString(separator = ",")}) AND $COL_NOTIFIED IS 0 AND $COL_UNREAD IS 1")
    }
}

private fun getFeedIdsToNotify(context: Context): List<Long> =
        context.contentResolver.getFeeds(columns = listOf(COL_ID),
                where = "$COL_NOTIFY IS 1").map { it.id }
