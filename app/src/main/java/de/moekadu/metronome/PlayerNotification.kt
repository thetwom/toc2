/*
 * Copyright 2020 Michael Moessner
 *
 * This file is part of Metronome.
 *
 * Metronome is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Metronome is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Metronome.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.moekadu.metronome

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class PlayerNotification(val context: PlayerService) {

    private val notificationView = RemoteViews(context.packageName, R.layout.notification).apply {
        // define response to click on increment
//        val incrementIntent = Intent(PlayerService.BROADCAST_PLAYERACTION)
//        incrementIntent.putExtra(PlayerService.INCREMENT_SPEED, true)
//        val pendingIncrementIntent = PendingIntent.getBroadcast(
//                context, notificationStateID + 1, incrementIntent, PendingIntent.FLAG_UPDATE_CURRENT)
//        setOnClickPendingIntent(R.id.notification_button_p, pendingIncrementIntent)
//
//        // define response to click on decrement
//        val decrementIntent = Intent(PlayerService.BROADCAST_PLAYERACTION)
//        decrementIntent.putExtra(PlayerService.DECREMENT_SPEED, true)
//        val pendingDecrementIntent = PendingIntent.getBroadcast(
//                context, notificationStateID + 2, decrementIntent, PendingIntent.FLAG_UPDATE_CURRENT)
//        setOnClickPendingIntent(R.id.notification_button_m, pendingDecrementIntent)

    }
    private val notificationBuilder = NotificationCompat.Builder(context, App.CHANNEL_ID).apply {
        val actIntent = Intent(context, MainActivity::class.java)
        val launchAct = PendingIntent.getActivity(context, 0, actIntent, 0)
        setContentTitle(context.getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_toc_swb4)
                .setContentIntent(launchAct)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(notificationView)
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private val notificationStateID = 3214
    val id = 3252
    val notification: Notification
        get() = notificationBuilder.build()

    private val handler = object: Handler(Looper.myLooper() ?: Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == messageWhat) {
                // we only want to update notification, not create ones. (creating is done by startForeground in the service)
                var haveActiveNotification = false
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)?.let { notificationManager ->
                    val notifications = notificationManager.activeNotifications
                    for (n in notifications) {
                        if (n.id == id) {
                            haveActiveNotification = true
                            break
                        }
                    }
                }

                if (haveActiveNotification)
                    NotificationManagerCompat.from(context).notify(id, notificationBuilder.build())
            }
        }
    }

    private val messageWhat = 3282

    var bpm = Bpm(-1.0f, NoteDuration.Quarter)
        set(value) {
            if (value != field) {
                field = value
                notificationView.setTextViewText(R.id.notification_bpm_text, context.getString(R.string.bpm, Utilities.getBpmString(value.bpm, bpmIncrement)))
            }
        }

    var bpmIncrement = -1.0f
        set(value) {
            if (value != field) {
                field = value
//                notificationView.setTextViewText(R.id.notification_button_p, "   + " + Utilities.getBpmString(value, value) + " ")
//                notificationView.setTextViewText(R.id.notification_button_m, " - " + Utilities.getBpmString(value, value) + "   ")
            }
        }

    var state = PlaybackStateCompat.STATE_NONE
        set(value) {
            if (field != value) {
                field = value
                val intent = Intent(PlayerService.BROADCAST_PLAYERACTION)

                if (state == PlaybackStateCompat.STATE_PLAYING) {
                    notificationView.setImageViewResource(R.id.notification_button, R.drawable.ic_pause2)
                    intent.putExtra(PlayerService.PLAYER_STATE, PlaybackStateCompat.ACTION_PAUSE)
                    val pIntent = PendingIntent.getBroadcast(context, notificationStateID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                    notificationView.setOnClickPendingIntent(R.id.notification_button, pIntent)
                }
                else { // if(getState() == PlaybackStateCompat.STATE_PAUSED){
                    // Log.v("Metronome", "ispaused");
                    notificationView.setImageViewResource(R.id.notification_button, R.drawable.ic_play2)
                    intent.putExtra(PlayerService.PLAYER_STATE, PlaybackStateCompat.ACTION_PLAY)
                    val pIntent = PendingIntent.getBroadcast(context, notificationStateID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                    notificationView.setOnClickPendingIntent(R.id.notification_button, pIntent)
                }
            }
        }

    init {
        bpm = InitialValues.bpm
        bpmIncrement = 1.0f
        state = PlaybackStateCompat.STATE_PAUSED
    }

    fun postNotificationUpdate() {
        handler.removeMessages(messageWhat)
        handler.sendEmptyMessageDelayed(messageWhat, 150)
    }

}