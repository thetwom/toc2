package de.moekadu.metronome

import kotlin.math.abs

class SoundProperties {
    companion object {
        fun parseMetaDataString(data: String): NoteList {
            val elements = data.split(" ")

            val noteList = NoteList()
            for(i in 0 until elements.size / 2) {
                noteList.add(NoteListItem(elements[2 * i].toInt(), elements[2 * i + 1].toFloat(), -1f))
            }
            return noteList
        }

        fun createMetaDataString(noteList: NoteList?): String {
            var meta = ""
            if (noteList != null) {
                for (item in noteList) {
                    meta += "${item.id} ${item.volume} "
                }
            }
            return meta
        }

        fun noteIdAndVolumeEqual(playListItem1: NoteListItem, playListItem2: NoteListItem): Boolean {
            return playListItem1.id == playListItem2.id && abs(playListItem1.volume - playListItem2.volume) < 1e-3
        }

        fun noteIdAndVolumeEqual(noteList1 : NoteList?, noteList2: NoteList?): Boolean {
            if (noteList1 == null && noteList2 == null) {
                return true
            } else if (noteList1 != null && noteList2 == null) {
                return false
            } else if (noteList1 == null && noteList2 != null) {
                return false
            } else if (noteList1 != null && noteList2 != null) {
                if (noteList1.size != noteList2.size)
                    return false
                for (i in noteList1.indices) {
                    if (!noteIdAndVolumeEqual(noteList1[i], noteList2[i]))
                        return false
                }
            }
            return true
        }
    }
}