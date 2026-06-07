package com.duynd.uthsynctask

import okhttp3.FormBody
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.CookieManager
import java.util.concurrent.TimeUnit

class UthScraper {

    // Tăng timeout một chút vì server của trường đôi khi phản hồi khá chậm
    private val client = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager()))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 1. Định nghĩa các URL chính xác của hệ thống LMS UTH
    private val LOGIN_PAGE_URL = "https://courses.ut.edu.vn/login/index.php"
    private val CALENDAR_URL = "https://courses.ut.edu.vn/calendar/view.php?view=month"

    fun loginAndGetSchedule(mssv: String, pass: String): String? {
        try {
            // --- BƯỚC 1: GET TRANG LOGIN ĐỂ LẤY LOGIN TOKEN ---
            val getLoginRequest = Request.Builder()
                .url(LOGIN_PAGE_URL)
                .build()

            var loginToken = ""
            client.newCall(getLoginRequest).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null

                // Dùng Jsoup tìm thẻ <input name="logintoken" value="..." />
                val document = Jsoup.parse(html)
                loginToken = document.select("input[name=logintoken]").attr("value")
            }

            if (loginToken.isEmpty()) {
                println("Không tìm thấy logintoken. Có thể giao diện web đã thay đổi.")
                return null
            }

            // --- BƯỚC 2: GỬI REQUEST POST ĐỂ ĐĂNG NHẬP THỰC SỰ ---
            // Trên Moodle, các key mặc định thường là 'username' và 'password'
            val formBody = FormBody.Builder()
                .add("username", mssv)
                .add("password", pass)
                .add("logintoken", loginToken)
                .build()

            val postLoginRequest = Request.Builder()
                .url(LOGIN_PAGE_URL) // Gửi POST về chính trang login index.php
                .post(formBody)
                .build()

            client.newCall(postLoginRequest).execute().use { response ->
                // Moodle sau khi đăng nhập thành công thường sẽ redirect (302) về trang chính.
                // OkHttp tự động handle redirect này nên ta chỉ cần check xem có thành công không.
                if (!response.isSuccessful) return null
            }

            // --- BƯỚC 3: TRUY CẬP TRANG LỊCH ĐỂ LẤY HTML ---
            val scheduleRequest = Request.Builder()
                .url(CALENDAR_URL)
                .build()

            client.newCall(scheduleRequest).execute().use { schedResponse ->
                if (!schedResponse.isSuccessful) return null
                return schedResponse.body?.string() // Đây chính là HTML chứa các ô lịch học
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}