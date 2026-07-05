package com.duynd.uthsynctask

import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun parseEventsFromHtml(html: String?): List<Assignment> {
    val eventsList = mutableListOf<Assignment>()
    if (html.isNullOrEmpty()) return eventsList

    try {
        val document = Jsoup.parse(html)

        // Tương đương: driver.find_elements(By.CSS_SELECTOR, "td.hasevent")
        val daysWithEvents = document.select("td.hasevent")
        println("📅 Tìm thấy ${daysWithEvents.size} ngày có sự kiện")

        // Định dạng ngày tương đương %Y-%m-%d
        val dateFormat = SimpleDateFormat("Y-M-d", Locale.getDefault())

        for (dayCell in daysWithEvents) {
            try {
                // Lấy timestamp (Moodle dùng đơn vị giây -> cần đổi sang mili-giây cho Java Date)
                val timestampStr = dayCell.attr("data-day-timestamp")
                if (timestampStr.isEmpty()) continue

                val timestampMs = timestampStr.toLong() * 1000
                val dateStr = dateFormat.format(Date(timestampMs))

                // Tương đương: day_cell.find_elements(By.CSS_SELECTOR, "li[data-region='event-item']")
                val eventItems = dayCell.select("li[data-region=event-item]")

                for (item in eventItems) {
                    // Tương đương: item.find_element(By.CLASS_NAME, "eventname").text.strip()
                    val title = item.select(".eventname").text().trim()

                    // Tương đương: item.find_element(By.TAG_NAME, "a").get_attribute("href")
                    val url = item.select("a").attr("href")

                    if (title.isNotEmpty()) {
                        eventsList.add(Assignment(title = title, date = dateStr, url = url))
                    }
                }
            } catch (e: Exception) {
                println("⚠ Lỗi khi xử lý một ngày cụ thể: ${e.message}")
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return eventsList
}