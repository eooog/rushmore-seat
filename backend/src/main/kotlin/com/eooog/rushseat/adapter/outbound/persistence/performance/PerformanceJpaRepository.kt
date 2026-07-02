package com.eooog.rushseat.adapter.outbound.persistence.performance

import com.eooog.rushseat.domain.performance.Performance
import com.eooog.rushseat.domain.performance.PerformanceSalesStatus
import com.eooog.rushseat.domain.performance.PerformanceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PerformanceJpaRepository : JpaRepository<Performance, Long> {

    @Query(
        """
        SELECT p.id AS performanceId,
               p.status AS status,
               p.salesStatus AS salesStatus
        FROM Performance p
        WHERE p.id = :performanceId
        """
    )
    fun findSalesStatus(
        @Param("performanceId") performanceId: Long,
    ): PerformanceSalesStatusProjection?
}

interface PerformanceSalesStatusProjection {
    val performanceId: Long
    val status: PerformanceStatus
    val salesStatus: PerformanceSalesStatus
}
