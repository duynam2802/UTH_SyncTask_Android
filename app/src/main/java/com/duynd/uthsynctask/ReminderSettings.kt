package com.duynd.uthsynctask

enum class NotificationMode(val value: String, val label: String) {
    OFF("off", "Tắt"),
    BASIC("basic", "Cơ bản"),
    FULL("full", "Đầy đủ");

    companion object {
        fun fromPreferenceValue(value: String?): NotificationMode = when (value?.lowercase()) {
            FULL.value -> FULL
            OFF.value -> OFF
            else -> BASIC
        }
    }
}

data class UthCredentials(
    val mssv: String,
    val password: String,
    val remember: Boolean = true
)
