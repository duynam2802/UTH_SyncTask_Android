package com.duynd.uthsynctask.domain

import android.content.Context
import com.duynd.uthsynctask.data.local.AppSettingsStore
import com.duynd.uthsynctask.data.local.EventStore
import com.duynd.uthsynctask.data.local.SecureCredentialStore
import com.duynd.uthsynctask.data.model.EventSource
import com.duynd.uthsynctask.data.model.LoginResult
import com.duynd.uthsynctask.data.model.SyncOutcome
import com.duynd.uthsynctask.data.model.SyncedEvent
import com.duynd.uthsynctask.data.remote.google.AuthorizationOutcome
import com.duynd.uthsynctask.data.remote.google.GoogleAuthManager
import com.duynd.uthsynctask.data.remote.google.GoogleCalendarRepository
import com.duynd.uthsynctask.data.remote.moodle.MoodleScheduleRepository
import kotlinx.coroutines.flow.first

private data class SyncPlanItem(val previous: SyncedEvent?, val current: SyncedEvent)

/**
 * Điều phối toàn bộ luồng đồng bộ: đăng nhập 2 hệ Moodle (Courses + thnn), lấy deadline,
 * đẩy lên Google Calendar (chỉ thêm/sửa khi thật sự cần để tiết kiệm API call mỗi giờ),
 * và các thao tác quản lý: đánh dấu hoàn thành, xoá, dọn dẹp trùng lặp giữa các lịch.
 */
class SyncRepository(context: Context) {

    private val appContext = context.applicationContext
    private val credentialStore = SecureCredentialStore(appContext)
    private val eventStore = EventStore(appContext)
    private val settingsStore = AppSettingsStore(appContext)
    private val googleAuthManager = GoogleAuthManager(appContext)
    private val googleCalendarRepository = GoogleCalendarRepository()

    suspend fun sync(): SyncOutcome {
        val credentials = credentialStore.getSavedCredentials()
            ?: return SyncOutcome.UthLoginFailed("Chưa đăng nhập tài khoản UTH.")

        val selectedCalendar = settingsStore.selectedCalendarFlow.first()
            ?: return SyncOutcome.NeedsCalendarSelection

        val authOutcome = googleAuthManager.authorize()
        val accessToken = when (authOutcome) {
            is AuthorizationOutcome.Granted -> authOutcome.accessToken
            is AuthorizationOutcome.NeedsConsent -> return SyncOutcome.NeedsGoogleAuthorization
            is AuthorizationOutcome.Failed -> return SyncOutcome.Error(authOutcome.message)
        }

        val existingEventsById = eventStore.getAll().associateBy { it.id }
        val planItems = mutableListOf<SyncPlanItem>()
        val warnings = mutableListOf<String>()

        // Chỉ 2 nguồn này chạy được Moodle login flow. PORTAL dùng API riêng, chưa tích hợp
        // vào luồng đồng bộ chính (xem PortalScheduleRepository - còn thiếu bước đăng nhập).
        for (source in listOf(EventSource.COURSES, EventSource.THNN)) {
            val moodleRepo = MoodleScheduleRepository(source)
            when (val loginResult = moodleRepo.login(credentials.mssv, credentials.password)) {
                is LoginResult.Success -> {
                    val discovered = try {
                        moodleRepo.discoverActivities()
                    } catch (e: Exception) {
                        warnings.add("${source.displayName}: không lấy được danh sách hoạt động (${e.message}).")
                        emptyList()
                    }

                    for (activity in discovered) {
                        val id = "${source.name}_${activity.activityId}"
                        val existing = existingEventsById[id]

                        val current: SyncedEvent = if (existing != null && existing.isPreciseTime) {
                            // Đã có giờ chính xác từ trước -> không cần ghé lại trang chi tiết,
                            // chỉ cập nhật lại tiêu đề/link phòng khi có thay đổi nhỏ.
                            existing.copy(sourceUrl = activity.url)
                        } else {
                            val precise = try {
                                moodleRepo.fetchPreciseTimes(activity.url)
                            } catch (e: Exception) {
                                null
                            }
                            when {
                                precise != null -> SyncedEvent(
                                    id = id,
                                    source = source,
                                    title = precise.cleanTitle ?: activity.title,
                                    courseName = null,
                                    startTimeMillis = precise.startMillis,
                                    endTimeMillis = precise.endMillis,
                                    sourceUrl = activity.url,
                                    isPreciseTime = true,
                                    googleCalendarId = existing?.googleCalendarId,
                                    googleEventId = existing?.googleEventId,
                                    isCompleted = existing?.isCompleted ?: false
                                )
                                existing != null -> existing // giữ bản ước tính cũ, thử lại lần sau
                                else -> SyncedEvent(
                                    id = id,
                                    source = source,
                                    title = "${activity.title} (giờ ước tính)",
                                    courseName = null,
                                    startTimeMillis = activity.approxDayMillis,
                                    endTimeMillis = activity.approxDayMillis + 30 * 60 * 1000,
                                    sourceUrl = activity.url,
                                    isPreciseTime = false
                                )
                            }
                        }
                        planItems.add(SyncPlanItem(existing, current))
                    }
                }
                is LoginResult.InvalidCredentials -> warnings.add("${source.displayName}: ${loginResult.message}")
                is LoginResult.NetworkError -> warnings.add("${source.displayName}: ${loginResult.message}")
                is LoginResult.UnknownError -> warnings.add("${source.displayName}: ${loginResult.message}")
            }
        }

        if (planItems.isEmpty() && warnings.isNotEmpty()) {
            return SyncOutcome.Error(warnings.joinToString("\n"))
        }

        var newCount = 0
        var updatedCount = 0

        for (item in planItems) {
            val old = item.previous
            val ev = item.current
            try {
                when {
                    ev.googleEventId == null -> {
                        googleCalendarRepository.insertEvent(accessToken, selectedCalendar.id, ev)
                            .onSuccess { googleEventId ->
                                eventStore.upsert(
                                    ev.copy(
                                        googleCalendarId = selectedCalendar.id,
                                        googleEventId = googleEventId,
                                        lastSyncedAtMillis = System.currentTimeMillis()
                                    )
                                )
                                newCount++
                            }
                            .onFailure { warnings.add("Không thêm được '${ev.title}': ${it.message}") }
                    }

                    ev.googleCalendarId != selectedCalendar.id -> {
                        // Người dùng vừa đổi lịch lưu -> chuyển sự kiện sang lịch mới, xoá bản ở lịch cũ.
                        googleCalendarRepository.insertEvent(accessToken, selectedCalendar.id, ev)
                            .onSuccess { newGoogleEventId ->
                                googleCalendarRepository.deleteEvent(
                                    accessToken, ev.googleCalendarId!!, ev.googleEventId!!
                                )
                                eventStore.upsert(
                                    ev.copy(
                                        googleCalendarId = selectedCalendar.id,
                                        googleEventId = newGoogleEventId,
                                        lastSyncedAtMillis = System.currentTimeMillis()
                                    )
                                )
                                newCount++
                            }
                            .onFailure { warnings.add("Không chuyển được '${ev.title}' sang lịch mới: ${it.message}") }
                    }

                    old != null && hasMeaningfulChange(old, ev) -> {
                        googleCalendarRepository.updateEvent(accessToken, selectedCalendar.id, ev.googleEventId!!, ev)
                            .onSuccess {
                                eventStore.upsert(ev.copy(lastSyncedAtMillis = System.currentTimeMillis()))
                                updatedCount++
                            }
                            .onFailure { warnings.add("Không cập nhật được '${ev.title}': ${it.message}") }
                    }

                    else -> {
                        // Không có gì thay đổi -> chỉ lưu lại cục bộ, không gọi API cho đỡ tốn.
                        eventStore.upsert(ev)
                    }
                }
            } catch (e: Exception) {
                warnings.add("Lỗi khi xử lý '${ev.title}': ${e.message}")
            }
        }

        settingsStore.updateLastSyncAt(System.currentTimeMillis())

        return SyncOutcome.Success(
            newEventsCount = newCount,
            updatedEventsCount = updatedCount,
            totalEventsCount = planItems.size,
            warnings = warnings
        )
    }

    private fun hasMeaningfulChange(old: SyncedEvent, new: SyncedEvent): Boolean {
        return old.startTimeMillis != new.startTimeMillis ||
            old.endTimeMillis != new.endTimeMillis ||
            old.title != new.title ||
            old.isCompleted != new.isCompleted
    }

    /** Đánh dấu hoàn thành/chưa hoàn thành - đẩy lên Google Calendar ngay nếu đã kết nối. */
    suspend fun setCompleted(eventId: String, completed: Boolean) {
        eventStore.setCompleted(eventId, completed)
        val event = eventStore.getAll().firstOrNull { it.id == eventId } ?: return
        val calendarId = event.googleCalendarId
        val googleEventId = event.googleEventId
        if (calendarId != null && googleEventId != null) {
            val authOutcome = googleAuthManager.authorize()
            if (authOutcome is AuthorizationOutcome.Granted) {
                googleCalendarRepository.updateEvent(authOutcome.accessToken, calendarId, googleEventId, event)
            }
        }
    }

    /** Xoá 1 deadline - xoá cả trên Google Calendar (nếu đã đồng bộ) lẫn dữ liệu cục bộ. */
    suspend fun deleteEvent(eventId: String) {
        val event = eventStore.getAll().firstOrNull { it.id == eventId }
        val calendarId = event?.googleCalendarId
        val googleEventId = event?.googleEventId
        if (calendarId != null && googleEventId != null) {
            val authOutcome = googleAuthManager.authorize()
            if (authOutcome is AuthorizationOutcome.Granted) {
                googleCalendarRepository.deleteEvent(authOutcome.accessToken, calendarId, googleEventId)
            }
        }
        eventStore.deleteById(eventId)
    }

    /**
     * Dọn dẹp sự kiện bị lưu trùng ở NHIỀU lịch khác nhau (VD: người dùng đổi lịch lưu vài lần).
     * Được gọi thủ công từ màn Cài đặt, KHÔNG chạy tự động mỗi giờ vì tốn nhiều API call.
     */
    suspend fun cleanupDuplicatesAcrossCalendars(): Result<Int> {
        val selectedCalendar = settingsStore.selectedCalendarFlow.first()
            ?: return Result.failure(IllegalStateException("Chưa chọn lịch lưu."))

        val authOutcome = googleAuthManager.authorize()
        val accessToken = (authOutcome as? AuthorizationOutcome.Granted)?.accessToken
            ?: return Result.failure(IllegalStateException("Chưa kết nối Google Calendar."))

        val calendars = googleCalendarRepository.listWritableCalendars(accessToken)
            .getOrElse { return Result.failure(it) }

        var removedCount = 0
        val events = eventStore.getAll()
        for (event in events) {
            val occurrences = googleCalendarRepository.findAllOccurrences(accessToken, event.id, calendars)
            val duplicates = occurrences.filterNot { it.first == selectedCalendar.id }
            for ((calId, eventId) in duplicates) {
                googleCalendarRepository.deleteEvent(accessToken, calId, eventId)
                removedCount++
            }
            val correctOccurrence = occurrences.firstOrNull { it.first == selectedCalendar.id }
            if (correctOccurrence != null && event.googleEventId != correctOccurrence.second) {
                eventStore.upsert(
                    event.copy(googleCalendarId = selectedCalendar.id, googleEventId = correctOccurrence.second)
                )
            }
        }
        return Result.success(removedCount)
    }
}
