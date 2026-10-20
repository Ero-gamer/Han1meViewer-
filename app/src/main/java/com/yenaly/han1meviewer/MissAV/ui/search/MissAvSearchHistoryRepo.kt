package com.yenaly.han1meviewer.MissAV.ui.search

import com.yenaly.han1meviewer.HJson
import com.yenaly.han1meviewer.Preferences
import kotlinx.serialization.Serializable

@Serializable
data class MissAvSearchHistoryEntry(
    val query: String,
    val sort: String? = null,
    val filter: String? = null,
    val genre: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

object MissAvSearchHistoryRepo {

    private const val PREF_KEY = "missav_search_history_v2"
    private const val LEGACY_PREF_KEY = "missav_search_history"
    private const val MAX_ENTRIES = 30

    fun load(): List<MissAvSearchHistoryEntry> {
        val raw = Preferences.preferenceSp.getString(PREF_KEY, null)
        if (!raw.isNullOrBlank()) {
            return runCatching {
                HJson.decodeFromString<List<MissAvSearchHistoryEntry>>(raw)
            }.getOrDefault(emptyList())
        }
        return migrateLegacy()
    }

    fun push(entry: MissAvSearchHistoryEntry) {
        if (entry.query.isBlank() &&
            entry.sort.isNullOrBlank() &&
            entry.filter.isNullOrBlank() &&
            entry.genre.isNullOrBlank()
        ) return

        val existing = load().toMutableList()
        existing.removeAll {
            it.query == entry.query &&
                    it.sort == entry.sort &&
                    it.filter == entry.filter &&
                    it.genre == entry.genre
        }
        existing.add(0, entry)
        val trimmed = existing.take(MAX_ENTRIES)
        Preferences.preferenceSp.edit()
            .putString(PREF_KEY, HJson.encodeToString(trimmed))
            .apply()
    }

    fun remove(entry: MissAvSearchHistoryEntry) {
        val existing = load().toMutableList()
        existing.removeAll {
            it.query == entry.query &&
                    it.sort == entry.sort &&
                    it.filter == entry.filter &&
                    it.genre == entry.genre &&
                    it.createdAt == entry.createdAt
        }
        Preferences.preferenceSp.edit()
            .putString(PREF_KEY, HJson.encodeToString(existing))
            .apply()
    }

    fun clear() {
        Preferences.preferenceSp.edit()
            .remove(PREF_KEY)
            .remove(LEGACY_PREF_KEY)
            .apply()
    }

    private fun migrateLegacy(): List<MissAvSearchHistoryEntry> {
        val legacy = Preferences.preferenceSp.getString(LEGACY_PREF_KEY, null)
            ?: return emptyList()
        val entries = legacy.split('\u001F')
            .filter { it.isNotBlank() }
            .map { MissAvSearchHistoryEntry(query = it) }
        if (entries.isNotEmpty()) {
            Preferences.preferenceSp.edit()
                .putString(PREF_KEY, HJson.encodeToString(entries))
                .remove(LEGACY_PREF_KEY)
                .apply()
        }
        return entries
    }
}
