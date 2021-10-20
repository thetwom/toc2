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

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ScenesViewModel(initialDatabaseString: String) : ViewModel() {

    private val sceneDatabase = SceneDatabase()
    private val _scenes = MutableLiveData(sceneDatabase)
    val scenes: LiveData<SceneDatabase> get() = _scenes
    val scenesAsString: String get() = scenes.value?.getScenesString() ?: ""

    private val _activeStableId = MutableLiveData(Scene.NO_STABLE_ID)
    val activeStableId: LiveData<Long> get() = _activeStableId

    private val _editingStableId = MutableLiveData(Scene.NO_STABLE_ID)
    val editingStableId: LiveData<Long> get() = _editingStableId

    private val _uri = MutableLiveData<Uri?>()
    val uri: LiveData<Uri?> get() = _uri
    private var uriHandledByScenesFragment = false
    private var uriHandledByMetronomeAndScenesFragment = false

    var isVisible = false
        set(value) {
            _isVisibleLiveData.value = value
            field = value
        }
    private val _isVisibleLiveData = MutableLiveData(isVisible)
    val isVisibleLiveData: LiveData<Boolean> get() = _isVisibleLiveData

    init  {
        sceneDatabase.databaseChangedListener = SceneDatabase.DatabaseChangedListener {
//            Log.v("Metronome", "ScenesViewModel.init: database changed")
            _scenes.value = it
        }

        val (check, scenes) = SceneDatabase.stringToScenes(initialDatabaseString)
        if (check == SceneDatabase.FileCheck.Ok)
            sceneDatabase.loadScenes(scenes, SceneDatabase.InsertMode.Replace)
    }

    fun setActiveStableId(stableId: Long) {
//        Log.v("Metronome", "ScenesViewModel.setStableActiveId: $stableId")
        _activeStableId.value = stableId
    }

    fun setEditingStableId(stableId: Long) {
        _editingStableId.value = stableId
    }

    fun loadScenesFromFile(uri: Uri) {
        uriHandledByMetronomeAndScenesFragment = false
        uriHandledByScenesFragment = false
        _uri.value = uri
    }

    fun loadingFileComplete(fragmentType: FragmentTypes) {
        when (fragmentType) {
            FragmentTypes.MetronomeAndScenes -> uriHandledByMetronomeAndScenesFragment = true
            FragmentTypes.Scenes -> uriHandledByScenesFragment = true
        }
        if (uriHandledByMetronomeAndScenesFragment && uriHandledByScenesFragment)
            _uri.value = null
    }
    class Factory(private val initialDatabaseString: String) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
//            Log.v("Metronome", "ScenesViewModel.factory.create")
            return ScenesViewModel(initialDatabaseString) as T
        }
    }

    enum class FragmentTypes {
        Scenes,
        MetronomeAndScenes
    }
}