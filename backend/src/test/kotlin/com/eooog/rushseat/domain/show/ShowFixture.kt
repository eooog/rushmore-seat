package com.eooog.rushseat.domain.show

object ShowFixture {
    fun show(
        title: String = "The 23rd Jarasum Jazz Festival",
        description: String = "The Jazz Festival Held on Jarasum in 2026",
        runningMinutes: Int = 1800,
    ): Show =
        Show.create(
            title = title,
            description = description,
            runningMinutes = runningMinutes,
        )
}
