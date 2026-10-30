package com.rynekryz.rynemaps.data

import android.content.Context
import org.json.JSONArray

class SavedStore(context: Context) {
    private val prefs = context.getSharedPreferences("rymaps", Context.MODE_PRIVATE)

    fun saved(): List<Place> = read("saved")

    fun recents(): List<Place> = read("recents")

    fun toggle(place: Place) {
        val list = saved().toMutableList()
        if (!list.removeAll { it.id == place.id }) list.add(0, place)
        write("saved", list)
    }

    fun addRecent(place: Place) {
        val list = recents().filterNot { it.id == place.id }.toMutableList()
        list.add(0, place)
        write("recents", list.take(12))
    }

    fun clearRecents() {
        prefs.edit().remove("recents").apply()
    }

    fun clearSaved() {
        prefs.edit().remove("saved").apply()
    }

    private fun read(key: String): List<Place> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        List(a.length()) { Place.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun write(key: String, list: List<Place>) {
        prefs.edit().putString(key, JSONArray(list.map { it.toJson() }).toString()).apply()
    }
}
