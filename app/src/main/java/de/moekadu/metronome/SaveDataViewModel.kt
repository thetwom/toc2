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

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SaveDataViewModel(initialDatabaseString: String) : ViewModel() {

    private val savedItemDatabase = SavedItemDatabase()
    private val _savedItems = MutableLiveData(savedItemDatabase)
    val savedItems: LiveData<SavedItemDatabase> get() = _savedItems
    val savedItemsAsString: String get() = savedItems.value?.getSaveDataString() ?: ""

    private val _activeStableId = MutableLiveData(SavedItem.NO_STABLE_ID)
    val activeStableId: LiveData<Long> get() = _activeStableId

    init  {
        savedItemDatabase.databaseChangedListener = SavedItemDatabase.DatabaseChangedListener {
//            Log.v("Metronome", "SaveDataViewModel.init: database changed")
            _savedItems.value = it
        }
        savedItemDatabase.loadDataFromString(initialDatabaseString, SavedItemDatabase.REPLACE)
    }

    fun setActiveStableId(stableId: Long) {
        _activeStableId.value = stableId
    }

    class Factory(private val initialDatabaseString: String) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return SaveDataViewModel(initialDatabaseString) as T
        }
    }
}