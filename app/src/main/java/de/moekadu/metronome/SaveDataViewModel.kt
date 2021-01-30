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

    init  {
        savedItemDatabase.databaseChangedListener = SavedItemDatabase.DatabaseChangedListener {
            Log.v("Metronome", "SaveDataViewModel.init: database changed")
            _savedItems.value = it
        }
        savedItemDatabase.loadDataFromString(initialDatabaseString, SavedItemDatabase.REPLACE)
    }

    class Factory(private val initialDatabaseString: String) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return SaveDataViewModel(initialDatabaseString) as T
        }
    }
}