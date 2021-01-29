package de.moekadu.metronome

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SaveDataViewModel(activity: FragmentActivity) : ViewModel() {

    private val savedItemDatabase = SavedItemDatabase()
    private val _savedItems = MutableLiveData(savedItemDatabase)
    val savedItems: LiveData<SavedItemDatabase> get() = _savedItems

    init  {
        savedItemDatabase.databaseChangedListener = SavedItemDatabase.DatabaseChangedListener {
            Log.v("Metronome", "SaveDataViewModel.init: database changed")
            _savedItems.value = it
        }
        loadDatabaseFromAppPreferences(activity)
    }

    fun saveDatabaseInAppPreferences(activity : FragmentActivity) {
        val s = savedItemDatabase.getSaveDataString()
        if (s != "") {
            val preferences = activity.getPreferences(Context.MODE_PRIVATE)
            val editor = preferences.edit()
            editor.putString("savedDatabase", s)
            editor.apply()
        }
    }

    private fun loadDatabaseFromAppPreferences(activity : FragmentActivity) {
        val preferences = activity.getPreferences(Context.MODE_PRIVATE)
        val dataString = preferences.getString("savedDatabase", "") ?: ""
        savedItemDatabase.loadDataFromString(dataString, SavedItemDatabase.REPLACE)
    }

    class Factory(private val activity: FragmentActivity) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return SaveDataViewModel(activity) as T
        }
    }
}