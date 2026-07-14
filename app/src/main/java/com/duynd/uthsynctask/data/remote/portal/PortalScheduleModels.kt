package com.duynd.uthsynctask.data.remote.portal

import com.duynd.uthsynctask.data.model.EventSource
import com.duynd.uthsynctask.data.model.LoginResult
import com.duynd.uthsynctask.data.model.SyncedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

data class PortalLoginResponse(
    val success: Boolean,
    val status: Int,
    val message: String?,
    val body: String?,
    val token: String?
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
 * Xử lý lấy dữ liệu từ hệ Portal UTH (portal.ut.edu.vn).
 * Hệ này dùng API JSON trả về JWT (token) để xác thực.
 */
class PortalScheduleRepository {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    private val gson = Gson()

    private val inputTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    /**
     * Đăng nhập vào Portal để lấy Token.
     * Endpoint: https://portal.ut.edu.vn/api/v1/auth/login
     */
    suspend fun login(mssv: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        // Thử gọi login không có CAPTCHA với các header giống hệt trình duyệt
        val url = "https://portal.ut.edu.vn/api/v1/auth/login?g-recaptcha-response=" 
        
        val credentialsMap = mapOf(
            "username" to mssv.trim(),
            "password" to password
        )
        val json = gson.toJson(credentialsMap)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", "https://portal.ut.edu.vn")
            .addHeader("Referer", "https://portal.ut.edu.vn/login")
            .addHeader("Sec-Ch-Ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"")
            .addHeader("Sec-Ch-Ua-Mobile", "?0")
            .addHeader("Sec-Ch-Ua-Platform", "\"Windows\"")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: return@withContext LoginResult.UnknownError("Không có phản hồi từ máy chủ")
                val parsed = try {
                    gson.fromJson(text, PortalLoginResponse::class.java)
                } catch (e: Exception) {
                    null
                }

                when {
                    response.code == 401 -> LoginResult.InvalidCredentials("Sai mã số sinh viên hoặc mật khẩu Portal.")
                    parsed?.success == true && parsed.token != null -> LoginResult.SuccessWithToken(parsed.token)
                    else -> LoginResult.InvalidCredentials(parsed?.message ?: "Portal phản hồi lỗi ${response.code}")
                }
            }
        } catch (e: Exception) {
            LoginResult.NetworkError("Không thể kết nối tới Portal: ${e.message}")
        }
    }

    /**
     * Lấy thời khóa biểu tuần.
     * @param dateYyyyMmDd Định dạng "yyyy-MM-dd"
     */
    suspend fun fetchWeeklySchedule(dateYyyyMmDd: String, authToken: String): List<PortalScheduleItem> =
        withContext(Dispatchers.IO) {
            val url = "https://portal.ut.edu.vn/api/v1/lichhoc/lichTuan?date=$dateYyyyMmDd"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $authToken")
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
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

        val location = item.tenPhong?.substringBefore(" - ") ?: ""
        val title = (item.tenMonHoc ?: "Buổi học").let {
            if (location.isNotEmpty()) "$it ($location)" else it
        }

        return SyncedEvent(
            id = "PORTAL_${item.id}",
            source = EventSource.PORTAL,
            title = title,
            courseName = item.maLopHocPhan,
            startTimeMillis = startMillis,
            endTimeMillis = endMillis,
            sourceUrl = item.link ?: "https://portal.ut.edu.vn/calendar",
            isPreciseTime = true
        )
    }
}
