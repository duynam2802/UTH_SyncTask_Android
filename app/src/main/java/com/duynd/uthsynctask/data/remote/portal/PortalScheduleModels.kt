package com.duynd.uthsynctask.data.remote.portal

import com.duynd.uthsynctask.data.model.EventSource
import com.duynd.uthsynctask.data.model.SyncedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** 1 buổi học trong thời khoá biểu tuần, lấy từ API thật của Portal UTH. */
data class PortalScheduleItem(
    val id: Long,
    val ngayBatDauHoc: String?,   // "dd/MM/yyyy"
    val tenPhong: String?,
    val maLopHocPhan: String?,
    val maMonHoc: String?,
    val tenMonHoc: String?,
    val giangVien: String?,
    val isTamNgung: Boolean = false,
    val tuGio: String?,           // "HH:mm"
    val denGio: String?,          // "HH:mm"
    val link: String?,
    val ghiChu: String?,
    val timeToDisplay: String?,
    val coSoToDisplay: String?
)

data class PortalWeeklyScheduleResponse(
    val success: Boolean,
    val status: Int,
    val message: String?,
    val body: List<PortalScheduleItem>?,
    val token: String?,
    val timestamp: String?
)

/**
 * ⚠️ CHƯA HOÀN THIỆN - mới xác định được API lấy thời khoá biểu tuần:
 * `GET https://portal.ut.edu.vn/api/v1/lichhoc/lichTuan?date=yyyy-MM-dd`
 * (xem cấu trúc JSON trả về ở [PortalWeeklyScheduleResponse]).
 *
 * CÒN THIẾU: cách đăng nhập/xác thực cho hệ Portal (khác cơ chế POST form của Moodle bên
 * Courses/thnn - Portal có vẻ dùng API riêng, có thể trả JWT qua 1 endpoint đăng nhập dạng
 * `POST .../api/v1/auth/...`). Cần bạn lấy thêm bằng DevTools (F12 -> Network) lúc đăng nhập
 * vào portal.ut.edu.vn: URL request, phần body gửi lên, và response trả về.
 *
 * Sau khi có, chỉ cần bổ sung phần đăng nhập vào class này (điền [authToken] đúng cách lấy
 * được) - phần gọi API lấy lịch tuần và parse JSON bên dưới đã sẵn sàng dùng ngay.
 */
class PortalScheduleRepository {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    private val gson = Gson()

    private val inputDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val inputTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    /**
     * @param authToken Bearer token lấy được sau khi đăng nhập Portal thành công (CHƯA rõ cách
     * lấy - xem ghi chú ở class doc). Truyền tạm chuỗi rỗng sẽ khiến API trả lỗi 401.
     */
    suspend fun fetchWeeklySchedule(dateYyyyMmDd: String, authToken: String): List<PortalScheduleItem> =
        withContext(Dispatchers.IO) {
            val url = "https://portal.ut.edu.vn/api/v1/lichhoc/lichTuan?date=$dateYyyyMmDd"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $authToken")
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string()
                    ?: throw IllegalStateException("Không có phản hồi từ Portal (mã ${response.code}).")
                val parsed = gson.fromJson(text, PortalWeeklyScheduleResponse::class.java)
                if (parsed?.success != true) {
                    throw IllegalStateException(parsed?.message ?: "Portal trả về lỗi không xác định.")
                }
                parsed.body.orEmpty().filterNot { it.isTamNgung }
            }
        }

    /** Chuyển 1 buổi học thành [SyncedEvent] để tái sử dụng chung pipeline đồng bộ/nhắc nhở. */
    fun toSyncedEvent(item: PortalScheduleItem): SyncedEvent? {
        val date = item.ngayBatDauHoc ?: return null
        val startText = "$date ${item.tuGio ?: return null}"
        val endText = "$date ${item.denGio ?: return null}"
        val startMillis = runCatching { inputTimeFormat.parse(startText)?.time }.getOrNull() ?: return null
        val endMillis = runCatching { inputTimeFormat.parse(endText)?.time }.getOrNull() ?: return null

        return SyncedEvent(
            id = "PORTAL_${item.id}",
            source = EventSource.PORTAL,
            title = item.tenMonHoc ?: "Buổi học",
            courseName = item.maLopHocPhan,
            startTimeMillis = startMillis,
            endTimeMillis = endMillis,
            sourceUrl = item.link ?: "https://portal.ut.edu.vn/calendar",
            isPreciseTime = true
        )
    }
}
