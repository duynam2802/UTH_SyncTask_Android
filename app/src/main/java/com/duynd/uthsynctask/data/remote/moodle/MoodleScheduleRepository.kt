package com.duynd.uthsynctask.data.remote.moodle

import com.duynd.uthsynctask.data.model.LoginResult
import com.duynd.uthsynctask.data.remote.UthAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Calendar
import java.util.Locale

/** Một hoạt động (bài tập/quiz/...) được phát hiện từ lịch tháng - chưa có giờ chính xác. */
data class DiscoveredActivity(
    val activityId: String,
    val title: String,
    val url: String,
    val approxDayMillis: Long
)

/** Giờ bắt đầu/kết thúc chính xác lấy được từ trang chi tiết hoạt động. */
data class PreciseTimes(
    val startMillis: Long,
    val endMillis: Long,
    val cleanTitle: String?
)

/**
 * Lấy dữ liệu deadline từ một hệ Moodle của UTH (courses.ut.edu.vn hoặc thnn.ut.edu.vn),
 * theo đúng cách đã kiểm chứng với dữ liệu thật:
 *
 * 1) [discoverActivities] - quét lịch tháng (HTML) để tìm ra CÓ những hoạt động nào và
 *    link chi tiết của từng hoạt động (giống cách code gốc đã làm).
 * 2) [fetchPreciseTimes] - ghé vào link chi tiết đó, đọc khối `data-region="activity-dates"`
 *    (VD: "Opened: ..." / "Closed: ...") để lấy ĐÚNG giờ bắt đầu/kết thúc.
 *
 * Bước 2 tốn thêm 1 request/hoạt động nên [com.duynd.uthsynctask.domain.SyncRepository] chỉ
 * gọi cho các hoạt động MỚI (chưa từng lấy giờ chính xác trước đó) để không tốn tài nguyên
 * mỗi lần đồng bộ nền hàng giờ.
 */
class MoodleScheduleRepository(private val source: com.duynd.uthsynctask.data.model.EventSource) {

    private val authRepository = UthAuthRepository(baseUrl = source.baseUrl)

    suspend fun login(mssv: String, password: String): LoginResult =
        authRepository.login(mssv, password)

    /**
     * Quét lịch tháng từ [monthsBack] tháng trước tới [monthsAhead] tháng sau để tìm hoạt động.
     * Trả về danh sách đã gộp trùng theo activityId (1 hoạt động có thể xuất hiện 2 lần trên
     * lịch - 1 lần lúc "mở", 1 lần lúc "hạn nộp" - nhưng cùng trỏ về 1 link chi tiết).
     */
    suspend fun discoverActivities(
        monthsBack: Int = 1,
        monthsAhead: Int = 2
    ): List<DiscoveredActivity> = withContext(Dispatchers.IO) {
        val byActivityId = LinkedHashMap<String, DiscoveredActivity>()

        for (offset in -monthsBack..monthsAhead) {
            val monthStartEpochSec = monthStartEpochSeconds(offset)
            val url = "${source.baseUrl}/calendar/view.php?view=month&time=$monthStartEpochSec"
            try {
                val request = Request.Builder().url(url).build()
                authRepository.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val html = response.body?.string() ?: return@use
                    for (activity in parseMonthHtml(html)) {
                        // Giữ bản ghi có timestamp sớm nhất nếu trùng activityId.
                        val existing = byActivityId[activity.activityId]
                        if (existing == null || activity.approxDayMillis < existing.approxDayMillis) {
                            byActivityId[activity.activityId] = activity
                        }
                    }
                }
            } catch (e: Exception) {
                // Bỏ qua tháng lỗi mạng, vẫn giữ kết quả các tháng khác.
            }
        }
        byActivityId.values.toList()
    }

    /** Ghé trang chi tiết hoạt động, đọc khối "activity-dates" để lấy giờ chính xác. */
    suspend fun fetchPreciseTimes(url: String): PreciseTimes? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            authRepository.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string() ?: return@use null
                parseActivityDetailPage(html)
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------
    // Bước 1: quét lịch tháng
    // ---------------------------------------------------------------------

    private fun monthStartEpochSeconds(monthOffset: Int): Long {
        val calendar = Calendar.getInstance(Locale.getDefault())
        calendar.add(Calendar.MONTH, monthOffset)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / 1000
    }

    private fun parseMonthHtml(html: String): List<DiscoveredActivity> {
        val results = mutableListOf<DiscoveredActivity>()
        val document = Jsoup.parse(html)
        val dayCells = document.select("td.hasevent")

        for (dayCell in dayCells) {
            val timestampStr = dayCell.attr("data-day-timestamp")
            val dayTimestampSec = timestampStr.toLongOrNull() ?: continue
            val eventItems = dayCell.select("li[data-region=event-item]")

            for (item in eventItems) {
                val title = item.select(".eventname").text().trim()
                if (title.isEmpty()) continue
                val url = item.select("a").attr("href")
                if (url.isEmpty()) continue
                val activityId = Regex("[?&]id=(\\d+)").find(url)?.groupValues?.get(1)
                    ?: continue // không lấy được id ổn định -> bỏ qua, tránh trùng lặp lung tung

                results.add(
                    DiscoveredActivity(
                        activityId = activityId,
                        title = title,
                        url = url,
                        approxDayMillis = dayTimestampSec * 1000
                    )
                )
            }
        }
        return results
    }

    // ---------------------------------------------------------------------
    // Bước 2: đọc trang chi tiết hoạt động
    // ---------------------------------------------------------------------

    private fun parseActivityDetailPage(html: String): PreciseTimes? {
        val document = Jsoup.parse(html)
        val datesBlock = document.select("div[data-region=activity-dates]").firstOrNull() ?: return null

        var startMillis: Long? = null
        var endMillis: Long? = null

        for (row in datesBlock.children()) {
            val label = row.select("strong").text().removeSuffix(":").trim().lowercase(Locale.ROOT)
            val fullText = row.text()
            val dateTimeText = fullText.substringAfter(":").trim()
            val parsedMillis = MoodleDateTimeParser.parse(dateTimeText) ?: continue

            when {
                // "Opened", "Opens", "Allows submissions from" -> mốc bắt đầu
                label.contains("open") || label.contains("from") -> {
                    if (startMillis == null || parsedMillis < startMillis!!) startMillis = parsedMillis
                }
                // "Closed", "Closes", "Due", "Cut-off date" -> mốc kết thúc
                label.contains("clos") || label.contains("due") || label.contains("cut") || label.contains("to") -> {
                    if (endMillis == null || parsedMillis > endMillis!!) endMillis = parsedMillis
                }
            }
        }

        val cleanTitle = extractCleanTitle(document)

        return when {
            startMillis != null && endMillis != null -> PreciseTimes(startMillis, endMillis, cleanTitle)
            endMillis != null -> PreciseTimes(endMillis - 30 * 60 * 1000, endMillis, cleanTitle)
            startMillis != null -> PreciseTimes(startMillis, startMillis + 30 * 60 * 1000, cleanTitle)
            else -> null
        }
    }

    private fun extractCleanTitle(document: Document): String? {
        // <title> Moodle có dạng "{mã lớp}: {tên hoạt động} | {tên site}".
        val rawTitle = document.title()
        if (rawTitle.isBlank()) return null
        val beforeSite = rawTitle.substringBeforeLast(" | ").trim()
        val afterColon = beforeSite.substringAfter(": ", missingDelimiterValue = beforeSite).trim()
        return afterColon.ifBlank { null }
    }
}
