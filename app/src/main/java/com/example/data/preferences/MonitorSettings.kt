package com.example.data.preferences

object VodafonePageDefaults {
    const val DEFAULT_URL =
        "https://eshop.vodafone.com.eg/en/lines/red/numbers?channelId=REDPlanAR&c_id=DACS0335&c_medium=PPC&c_name=REDPlanAR&c_source=Search&c_term=REDPlanAR&c_type=Anonymous"
}

data class MonitorSettings(
    val targetUrl: String = VodafonePageDefaults.DEFAULT_URL,
    val pinnedSelector: String = "",
    val autoReloadSeconds: Int = 0,
    val backgroundCheckEnabled: Boolean = true,
    val alertOnIncrease: Boolean = true,
    val alertOnChange: Boolean = false,
    val alertOnZero: Boolean = false,
    val thresholdEnabled: Boolean = false,
    val thresholdValue: Int = 60,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val lastKnownNumeric: Long? = null,
    val lastKnownRaw: String = "",
    val lastBackgroundCheckMs: Long? = null,
    val lastBackgroundStatus: String = ""
)
