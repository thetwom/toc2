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

import android.content.Context
import android.widget.Toast
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

class Scene(var title: String = "", var date: String = "",
                 var time: String = "", var bpm: Bpm = Bpm(0f, NoteDuration.Quarter),
                 var noteList: ArrayList<NoteListItem>,
                 val stableId: Long) {
    companion object {
        const val NO_STABLE_ID = 0L
    }

    fun clone(title: String = this.title, date: String = this.date, time: String = this.time,
              bpm: Bpm = this.bpm, noteList: ArrayList<NoteListItem> = this.noteList,
              stableId: Long = this.stableId): Scene {
        val noteListCopy = ArrayList<NoteListItem>()
        deepCopyNoteList(noteList, noteListCopy)
        return Scene(title, date, time, bpm.copy(), noteListCopy, stableId)
    }

    fun isEqualApartFromStableId(other: Scene?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (title != other.title) return false
        if (date != other.date) return false
        if (time != other.time) return false
        if (bpm != other.bpm) return false
        // if (stableId != other.stableId) return false
        if (!areNoteListsEqual(noteList, other.noteList)) return false
        return true
    }
}

private fun isVersion1LargerOrEqualThanVersion2(v1: String, v2: String): Boolean {
    if (v1 == v2)
        return true

    val l1 = v1.split('.')
    val l2 = v2.split('.')
    val count = min(l1.size, l2.size)

    var isLarger = false
    for (i in 0 until count) {
        if (l1[i] > l2[i]) {
            isLarger = true
            break
        }
        else if (l1[i] < l2[i]) {
            break
        }
    }

    return isLarger
}

class SceneDatabase {
    private val _scenes = mutableListOf<Scene>()
    val scenes: List<Scene> get() = _scenes
    val size get() = _scenes.size

    private val stableIds = mutableSetOf<Long>()

    fun interface DatabaseChangedListener {
        fun onChanged(sceneDatabase: SceneDatabase)
    }
    var databaseChangedListener: DatabaseChangedListener? = null

    private fun getNewStableId(): Long {
        var newId = Scene.NO_STABLE_ID + 1
        while (stableIds.contains(newId)) {
            ++newId
        }
        return newId
    }

    fun getScene(stableId: Long): Scene? {
        return scenes.firstOrNull { it.stableId == stableId }
    }

    fun editScene(stableId: Long?, title: String? = null, bpm: Bpm? = null, noteList: ArrayList<NoteListItem>? = null) {
        if (stableId == null)
            return
        val scene = getScene(stableId) ?: return
        title?.let { scene.title = it }
        bpm?.let { scene.bpm = it }
        noteList?.let { deepCopyNoteList(it, scene.noteList) }
        databaseChangedListener?.onChanged(this)
    }

    fun remove(position: Int) : Scene {
        if (BuildConfig.DEBUG && position >= _scenes.size)
            throw RuntimeException("Invalid position")

        val scene = _scenes.removeAt(position)
        stableIds.remove(scene.stableId)
        databaseChangedListener?.onChanged(this)
        return scene
    }

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex >= _scenes.size)
            return
        val scene = _scenes.removeAt(fromIndex)
        val toIndexCorrected = min(toIndex, _scenes.size)
        _scenes.add(toIndexCorrected, scene)
        databaseChangedListener?.onChanged(this)
    }

    fun add(scene: Scene, callDatabaseChangedListener: Boolean = true): Long {
        //Log.v("Metronome", "SceneDatabase.add: Adding: ${scene.title}, ${scene.noteList}")
        return add(scenes.size, scene, callDatabaseChangedListener)
    }

    fun add(position: Int, scene: Scene, callDatabaseChangedListener: Boolean = true) : Long {
        val positionCorrected = min(position, _scenes.size)

        // keep the scene stable id if possible else create a new one
        val stableId = if (scene.stableId == Scene.NO_STABLE_ID || stableIds.contains(scene.stableId)) {
            val newScene = scene.clone(stableId = getNewStableId())
            _scenes.add(positionCorrected, newScene)
            stableIds.add(newScene.stableId)
            newScene.stableId
        }
        else {
            _scenes.add(positionCorrected, scene)
            stableIds.add(scene.stableId)
            scene.stableId
        }

        if (callDatabaseChangedListener)
            databaseChangedListener?.onChanged(this)
        return stableId
    }

    fun getScenesString() : String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(String.format(Locale.ENGLISH, "%50s", BuildConfig.VERSION_NAME))
        for (si in scenes) {
            stringBuilder.append(String.format(Locale.ENGLISH, "%200s%10s%5s%12.5f%30s%sEND",
                si.title, si.date, si.time, si.bpm.bpm, si.bpm.noteDuration.toString(),
                noteListToString(si.noteList)))
        }
//        Log.v("Metronome", "SceneDatabase.getSceneString: string= ${stringBuilder}")
        return stringBuilder.toString()
    }

    fun loadScenes(newScenes: List<Scene>, mode: InsertMode = InsertMode.Replace): FileCheck {

        when (mode) {
            InsertMode.Replace -> {
                _scenes.clear()
                stableIds.clear()
                newScenes.forEach { add(it, false) }
            }
            InsertMode.Prepend -> {
                for (i in newScenes.indices)
                    add(i, newScenes[i], false)
            }
            InsertMode.Append -> {
                newScenes.forEach { add(it, false) }
            }
        }

        databaseChangedListener?.onChanged(this)
        return FileCheck.Ok
    }

    fun clear() {
        _scenes.clear()
        stableIds.clear()
        databaseChangedListener?.onChanged(this)
    }

    enum class FileCheck {Ok, Empty, Invalid}
    enum class InsertMode {Replace, Prepend, Append}

    data class ScenesAndFileCheckResult(val fileCheck: FileCheck, val scenes: List<Scene>)

    companion object {

        fun stringToScenes(sceneString: String): ScenesAndFileCheckResult {
            val scenes = mutableListOf<Scene>()

            if (sceneString == "")
                return ScenesAndFileCheckResult(FileCheck.Empty, scenes)
            else if (sceneString.length < 50)
                return ScenesAndFileCheckResult(FileCheck.Invalid, scenes)

            val version = sceneString.substring(0, 50).trim()
//        Log.v("Metronome", "SceneDatabase.loadDataFromString: version = $version, ${isVersion1LargerThanVersion2(BuildConfig.VERSION_NAME, version)}")
            var pos = 50
            var numScenesRead = 0

            while (pos < sceneString.length) {
                if (pos + 200 >= sceneString.length)
                    return ScenesAndFileCheckResult(FileCheck.Invalid, scenes)
                val title = sceneString.substring(pos, pos + 200).trim()
                pos += 200
                if (pos + 10 >= sceneString.length)
                    return ScenesAndFileCheckResult(FileCheck.Invalid, scenes)
                val date = sceneString.substring(pos, pos + 10)
                pos += 10
                if (pos + 5 >= sceneString.length)
                    return ScenesAndFileCheckResult(FileCheck.Invalid, scenes)
                val time = sceneString.substring(pos, pos + 5)
                pos += 5
                if (pos + 12 >= sceneString.length)
                    return ScenesAndFileCheckResult(FileCheck.Invalid, scenes)
                val bpmValue = try {
                    (sceneString.substring(pos, pos + 12).trim()).toFloat()
                } catch (e: NumberFormatException) {
                    return ScenesAndFileCheckResult(FileCheck.Invalid, scenes)
                }
                pos += 12
                val noteDuration = if (isVersion1LargerOrEqualThanVersion2(version, "4.0.0")) {
//                    Log.v("Metronome", "SceneDatabase.stringToScenes: version=$version >= 4.0.0")
                    if (pos + 30 >= sceneString.length)
                        return ScenesAndFileCheckResult(FileCheck.Invalid, scenes)
                    val noteDurationString = sceneString.substring(pos, pos + 30).trim()
                    pos += 30
                    NoteDuration.valueOf(noteDurationString)
                } else {
                    NoteDuration.Quarter
                }

                val noteListEnd = sceneString.indexOf("END", pos)
                if (noteListEnd == -1)
                    return ScenesAndFileCheckResult(FileCheck.Invalid, scenes)
                val noteList = sceneString.substring(pos, noteListEnd)
                if (!isNoteListStringValid(noteList))
                    return ScenesAndFileCheckResult(FileCheck.Invalid, scenes)
                pos = noteListEnd + 3

                val si = Scene(title, date, time, Bpm(bpmValue, noteDuration), stringToNoteList(noteList), Scene.NO_STABLE_ID)
                scenes.add(si)
                ++numScenesRead
            }
            return ScenesAndFileCheckResult(FileCheck.Ok, scenes)
        }

        fun toastFileCheckString(context: Context, filename: String?, fileCheck: FileCheck) {
            when (fileCheck) {
                FileCheck.Empty -> {
                    Toast.makeText(context, context.getString(R.string.file_empty, filename), Toast.LENGTH_LONG).show()
                }
                FileCheck.Invalid -> {
                    Toast.makeText(context, context.getString(R.string.file_invalid, filename), Toast.LENGTH_LONG).show()
                }
                else -> {
                }
            }
        }
    }
}