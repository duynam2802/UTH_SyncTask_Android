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
import com.duynd.uthsynctask.data.remote.portal.PortalScheduleRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    private val portalRepository = PortalScheduleRepository()

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

        // Lấy danh sách sự kiện hiện tại trên Cloud để đối chiếu (tránh trùng lặp với lịch có sẵn)
        val cloudEvents = googleCalendarRepository.listAllEvents(accessToken, selectedCalendar.id, System.currentTimeMillis() - 24 * 60 * 60 * 1000)
            .getOrElse { emptyList() }

        // Chỉ 2 nguồn này chạy được Moodle login flow.
        for (source in listOf(EventSource.COURSES, EventSource.THNN)) {
            val moodleRepo = MoodleScheduleRepository(source)
            when (val loginResult = moodleRepo.login(credentials.mssv, credentials.password)) {
                is LoginResult.Success, is LoginResult.SuccessWithToken -> {
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

        // PORTAL: Lấy thời khoá biểu học trên lớp.
        val storedPortalToken = credentialStore.getPortalToken()
        if (storedPortalToken != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            for (weekOffset in 0..3) {
                val cal = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, weekOffset) }
                val dateStr = sdf.format(cal.time)
                try {
                    val items = portalRepository.fetchWeeklySchedule(dateStr, storedPortalToken)
                    for (item in items) {
                        val ev = portalRepository.toSyncedEvent(item) ?: continue
                        val existing = existingEventsById[ev.id]
                        planItems.add(SyncPlanItem(existing, ev))
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("401") == true) {
                        warnings.add("Portal: Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại Portal trong ứng dụng.")
                        break 
                    } else {
                        warnings.add("Portal (tuần $dateStr): ${e.message}")
                    }
                }
            }
        } else {
            // Thử login tự động một lần, nếu thất bại do CAPTCHA thì sẽ không có token lưu trữ
            val portalLogin = portalRepository.login(credentials.mssv, credentials.password)
            if (portalLogin is LoginResult.SuccessWithToken) {
                credentialStore.savePortalToken(portalLogin.token)
                // Đệ quy nhẹ hoặc copy logic ở trên, nhưng để an toàn ta báo user chạy lại lần sau
                warnings.add("Portal: Đã lấy được token mới, vui lòng nhấn đồng bộ lại.")
            } else {
                warnings.add("Portal: Chưa đăng nhập hoặc vướng CAPTCHA. Hãy vào Đăng nhập Portal.")
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
                        // TRƯỚC KHI THÊM MỚI: Kiểm tra xem trên Google Calendar đã có sự kiện 
                        // trùng Tên và Giờ chưa (để xoá bản cũ/bản mặc định/bản sai phòng)
                        val fuzzyTitle = ev.title.substringBefore(" (").substringBefore(" -").trim()
                        val startTimeStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
                        }.format(java.util.Date(ev.startTimeMillis))

                        val cloudDuplicate = cloudEvents.firstOrNull { cloudEv ->
                            val cloudTitle = cloudEv.summary?.removePrefix("✅ ")?.trim() ?: ""
                            cloudTitle.startsWith(fuzzyTitle) && cloudEv.start?.dateTime?.startsWith(startTimeStr) == true
                        }

                        if (cloudDuplicate != null) {
                            googleCalendarRepository.deleteEvent(accessToken, selectedCalendar.id, cloudDuplicate.id!!)
                        }

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
     * Dọn dẹp cực mạnh:
     * 1. Xoá sự kiện bị lưu trùng ở các lịch KHÔNG được chọn.
     * 2. Xoá sự kiện trùng lặp theo TIÊU ĐỀ và THỜI GIAN ở ngay trong lịch đang chọn.
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
        
        // Bước 1: Dọn theo ID (logic cũ)
        val events = eventStore.getAll()
        for (event in events) {
            val occurrences = googleCalendarRepository.findAllOccurrences(accessToken, event.id, calendars)
            val duplicates = occurrences.filterNot { it.first == selectedCalendar.id }
            for ((calId, eventId) in duplicates) {
                googleCalendarRepository.deleteEvent(accessToken, calId, eventId)
                removedCount++
            }
        }

        // Bước 2: Dọn theo TIÊU ĐỀ trong cùng một NGÀY (Cực mạnh)
        // Chỉ quét các sự kiện từ 1 tháng trước đến 3 tháng sau để tránh tốn API
        val oneMonthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val allEvents = googleCalendarRepository.listAllEvents(accessToken, selectedCalendar.id, oneMonthAgo)
            .getOrElse { return Result.failure(it) }

        // Nhóm theo "Tên (đã lọc ✅) | Ngày (YYYY-MM-DD)"
        val grouped = allEvents.groupBy { event ->
            val cleanTitle = event.summary?.removePrefix("✅ ")?.trim() ?: ""
            // Lấy phần ngày YYYY-MM-DD từ dateTime (2024-07-15T...) hoặc date
            val datePart = event.start?.dateTime?.substringBefore("T") ?: event.start?.date ?: ""
            "$cleanTitle|$datePart"
        }
        
        for ((_, duplicates) in grouped) {
            if (duplicates.size > 1) {
                // Giữ lại 1 bản, xoá các bản còn lại
                for (i in 1 until duplicates.size) {
                    val eventId = duplicates[i].id ?: continue
                    googleCalendarRepository.deleteEvent(accessToken, selectedCalendar.id, eventId)
                    removedCount++
                }
            }
        }

        return Result.success(removedCount)
    }
}
