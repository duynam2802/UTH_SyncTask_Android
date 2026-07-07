package com.duynd.uthsynctask.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duynd.uthsynctask.data.model.EventSource
import com.duynd.uthsynctask.data.model.SyncedEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.eventsDataStore by preferencesDataStore(name = "uth_synced_events")

/**
 * Lưu danh sách [SyncedEvent] dưới dạng JSON trong DataStore (theo lựa chọn "nhẹ, đơn giản"
 * thay vì Room). Toàn bộ danh sách được đọc/ghi như MỘT giá trị duy nhất - phù hợp vì
 * số lượng deadline của một sinh viên thường chỉ vài chục tới vài trăm, không cần
 * đến database quan hệ đầy đủ.
 */
class EventStore(private val context: Context) {

    private object Keys {
        val EVENTS_JSON = stringPreferencesKey("events_json")
    }

    private val gson = Gson()
    private val listType = object : TypeToken<List<SyncedEvent>>() {}.type

    /** Quan sát danh sách sự kiện theo thời gian thực (dùng cho màn hình Lịch). */
    val eventsFlow: Flow<List<SyncedEvent>> = context.eventsDataStore.data.map { prefs ->
        decode(prefs[Keys.EVENTS_JSON])
    }

    suspend fun getAll(): List<SyncedEvent> = decode(
        context.eventsDataStore.data.first()[Keys.EVENTS_JSON]
    )

    suspend fun getAllForSource(source: EventSource): List<SyncedEvent> =
        getAll().filter { it.source == source }

    /** Ghi đè toàn bộ danh sách - dùng khi đồng bộ xong một lượt. */
    suspend fun replaceAll(events: List<SyncedEvent>) {
        context.eventsDataStore.edit { prefs ->
            prefs[Keys.EVENTS_JSON] = gson.toJson(events)
        }
    }

    /** Thêm hoặc cập nhật (theo [SyncedEvent.id]) một sự kiện, giữ nguyên các sự kiện khác. */
    suspend fun upsert(event: SyncedEvent) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.id == event.id }
        if (index >= 0) current[index] = event else current.add(event)
        replaceAll(current)
    }

    suspend fun upsertAll(events: List<SyncedEvent>) {
        val current = getAll().associateBy { it.id }.toMutableMap()
        events.forEach { current[it.id] = it }
        replaceAll(current.values.toList())
    }

    suspend fun deleteById(id: String) {
        replaceAll(getAll().filterNot { it.id == id })
    }

    suspend fun setCompleted(id: String, completed: Boolean) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(isCompleted = completed)
            replaceAll(current)
        }
    }

    /** Ghi lại thời điểm vừa nhắc nhở (Part 3) - dùng để tránh nhắc lại quá dày trong 1 khung giờ. */
    suspend fun updateLastNotifiedAt(id: String, timeMillis: Long) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(lastNotifiedAtMillis = timeMillis)
            replaceAll(current)
        }
    }

    private fun decode(json: String?): List<SyncedEvent> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
