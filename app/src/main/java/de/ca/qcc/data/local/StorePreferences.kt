package de.ca.qcc.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class StorePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("duplicate_search", Context.MODE_PRIVATE)

    var lastStore: String
        get() = prefs.getString(KEY_LAST_STORE, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LAST_STORE, value).apply()
        }

    private companion object {
        const val KEY_LAST_STORE = "last_store"
    }
}
