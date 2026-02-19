package com.reader.shared.domain.model

enum class NotificationInterval(val displayName: String, val minutes: Long?) {
    OFF("Off", null),
    FIFTEEN_MINUTES("15 minutes", 15L),
    THIRTY_MINUTES("30 minutes", 30L),
    ONE_HOUR("1 hour", 60L),
    SIX_HOURS("6 hours", 360L),
    TWENTY_FOUR_HOURS("24 hours", 1440L)
}
