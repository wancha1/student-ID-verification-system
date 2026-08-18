package com.example.model

enum class FeeStatus(val displayName: String) {
    CLEARED("CLEARED"),
    OUTSTANDING("OUTSTANDING")
}

enum class DayScholarStatus(val label: String, val isDayScholar: Boolean) {
    DAY_SCHOLAR_BUS("Day Scholar (Bus)", true),
    DAY_SCHOLAR_PRIVATE("Day Scholar (Private Drop-off)", true),
    DAY_SCHOLAR_WALK("Day Scholar (Walking/Local)", true),
    BOARDER("Boarding Student", false)
}
