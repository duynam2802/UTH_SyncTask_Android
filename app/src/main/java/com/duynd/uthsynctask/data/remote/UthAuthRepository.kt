package com.duynd.uthsynctask.data.remote

import com.duynd.uthsynctask.data.model.LoginResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

/**
 * Xử lý đăng nhập vào hệ thống LMS Moodle của UTH.
 *
 * Mặc định trỏ tới courses.ut.edu.vn, nhưng nhận [baseUrl] tuỳ chỉnh vì thnn.ut.edu.vn
 * có cùng cấu trúc URL (/calendar/view.php?view=month) nên nhiều khả năng cũng chạy Moodle -
 * sẽ tái sử dụng đúng lớp này ở phần Đồng bộ (Part 2) cho cả 2 nguồn.
 *
 * Repository này ở Part 1 chỉ dùng để XÁC THỰC tài khoản/mật khẩu tại màn hình đăng nhập.
 * Việc lấy dữ liệu lịch học/deadline thật sự sẽ được xây dựng riêng trong module Đồng bộ,
 * vì nó cần thêm bước lấy sesskey + gọi API lịch của Moodle để có giờ chính xác.
 */
class UthAuthRepository(
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    private val client: OkHttpClient by lazy {
        val cookieManager = CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        }
        OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val loginPageUrl: String get() = "$baseUrl/login/index.php"

    suspend fun login(mssv: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val loginToken = fetchLoginToken()
                ?: return@withContext LoginResult.UnknownError(
                    "Không thể tải trang đăng nhập UTH. Giao diện web có thể đã thay đổi."
                )

            val formBody = FormBody.Builder()
                .add("username", mssv.trim())
                .add("password", password)
                .add("logintoken", loginToken)
                .build()

            val postRequest = Request.Builder()
                .url(loginPageUrl)
                .post(formBody)
                .build()

            client.newCall(postRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LoginResult.NetworkError(
                        "Máy chủ UTH phản hồi lỗi (mã ${response.code}). Vui lòng thử lại sau."
                    )
                }

                val finalUrl = response.request.url.toString()
                val html = response.body?.string().orEmpty()

                // Moodle: đăng nhập sai sẽ render lại chính trang login (vẫn còn form + logintoken).
                val stillOnLoginPage = finalUrl.contains("login/index.php") &&
                    html.contains("name=\"logintoken\"")

                if (stillOnLoginPage) {
                    val errorMessage = extractLoginError(html)
                    LoginResult.InvalidCredentials(
                        errorMessage ?: "Sai mã số sinh viên hoặc mật khẩu. Vui lòng kiểm tra lại."
                    )
                } else {
                    LoginResult.Success
                }
            }
        } catch (e: IOException) {
            LoginResult.NetworkError(
                "Không thể kết nối tới máy chủ UTH. Vui lòng kiểm tra mạng và thử lại."
            )
        } catch (e: Exception) {
            LoginResult.UnknownError(
                "Đã xảy ra lỗi không xác định: ${e.message ?: "không rõ nguyên nhân"}"
            )
        }
    }

    private fun fetchLoginToken(): String? {
        val request = Request.Builder().url(loginPageUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null
            val token = Jsoup.parse(html).select("input[name=logintoken]").attr("value")
            return token.ifEmpty { null }
        }
    }

    private fun extractLoginError(html: String): String? {
        val document = Jsoup.parse(html)
        val candidateSelectors = listOf(
            "#loginerrormessage",
            ".loginerrors",
            "[data-region=loginerrors]"
        )
        for (selector in candidateSelectors) {
            val text = document.select(selector).text().trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://courses.ut.edu.vn"
    }
}
