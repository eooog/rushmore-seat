package com.eooog.rushseat.application.performance.required

import com.eooog.rushseat.domain.performance.PerformanceSalesStatus
import com.eooog.rushseat.domain.performance.PerformanceStatus

interface LoadPerformanceSalesStatusPort {
    fun load(performanceId: Long): PerformanceSalesStatusSnapshot?
}

data class PerformanceSalesStatusSnapshot(
    val performanceId: Long,
    val status: PerformanceStatus,
    val salesStatus: PerformanceSalesStatus,
) {
    fun isOnSale(): Boolean = status == PerformanceStatus.SCHEDULED && salesStatus == PerformanceSalesStatus.ON_SALE
}
