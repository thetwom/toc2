package de.moekadu.metronome

import android.util.Log
import kotlin.math.abs

class SoundProperties {
    companion object {
        fun parseMetaDataString(data: String): Array<AudioMixer.PlayListItem> {
            val elements = data.split(" ")

            val playList = Array(elements.size / 2) {
                AudioMixer.PlayListItem(0, 1.0f, -1f, null)
            }

            for (i in 0 until elements.size / 2) {
                playList[i].trackIndex = elements[2 * i].toInt()
                playList[i].volume = elements[2 * i + 1].toFloat()
            }

            return playList;
        }

        fun createMetaDataString(playList: Array<AudioMixer.PlayListItem>?): String {
            var meta = ""
            if (playList != null) {
                for (item in playList) {
                    meta += "${item.trackIndex} ${item.volume} "
                }
            }
            return meta
        }

        fun trackIndexAndVolumeEqual(playListItem1: AudioMixer.PlayListItem, playListItem2: AudioMixer.PlayListItem): Boolean {
            return playListItem1.trackIndex == playListItem2.trackIndex && abs(playListItem1.volume - playListItem2.volume) < 1e-3
        }

        fun trackIndexAndVolumeEqual(playList1: Array<AudioMixer.PlayListItem>?, playList2: Array<AudioMixer.PlayListItem>?): Boolean {
            if (playList1 == null && playList2 == null) {
                return true
            } else if (playList1 != null && playList2 == null) {
                return false
            } else if (playList1 == null && playList2 != null) {
                return false
            } else if (playList1 != null && playList2 != null) {
                if (playList1.size != playList2.size)
                    return false
                for (i in playList1.indices) {
                    if (!trackIndexAndVolumeEqual(playList1[i], playList2[i]))
                        return false
                }
            }
            return true
        }
    }
}