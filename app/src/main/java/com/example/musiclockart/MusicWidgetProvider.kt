package com.example.musiclockart

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

/**
 * Home-screen widget showing now-playing art, title/artist, and play/prev/next.
 * Because RemoteViews can't observe a StateFlow, MediaNotificationListener calls
 * MusicWidgetProvider.pushUpdate(...) whenever the track changes, and the button
 * taps come back here as broadcasts which we forward to Transport.
 */
class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Draw whatever we last knew about.
        for (id in appWidgetIds) {
            renderInto(context, appWidgetManager, id, lastTrack)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> Transport.playPause()
            ACTION_NEXT -> Transport.next()
            ACTION_PREV -> Transport.previous()
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.musiclockart.WIDGET_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musiclockart.WIDGET_NEXT"
        const val ACTION_PREV = "com.example.musiclockart.WIDGET_PREV"

        @Volatile
        private var lastTrack: TrackInfo = TrackInfo()

        /** Called by the listener whenever now-playing changes. */
        fun pushUpdate(context: Context, track: TrackInfo) {
            lastTrack = track
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, MusicWidgetProvider::class.java)
            )
            for (id in ids) renderInto(context, mgr, id, track)
        }

        private fun renderInto(
            context: Context,
            mgr: AppWidgetManager,
            widgetId: Int,
            track: TrackInfo
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_music)

            views.setTextViewText(
                R.id.widgetTitle,
                track.title.ifBlank { context.getString(R.string.nothing_playing) }
            )
            views.setTextViewText(R.id.widgetArtist, track.artist)

            val art: Bitmap? = track.art
            if (art != null) {
                views.setImageViewBitmap(R.id.widgetArt, art)
            } else {
                views.setImageViewResource(R.id.widgetArt, R.drawable.ic_launcher_foreground)
            }

            views.setImageViewResource(
                R.id.widgetPlayPause,
                if (track.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )

            views.setOnClickPendingIntent(R.id.widgetPlayPause, pi(context, ACTION_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.widgetNext, pi(context, ACTION_NEXT, 2))
            views.setOnClickPendingIntent(R.id.widgetPrev, pi(context, ACTION_PREV, 3))

            mgr.updateAppWidget(widgetId, views)
        }

        private fun pi(context: Context, action: String, req: Int): PendingIntent {
            val intent = Intent(context, MusicWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
