package com.eooog.rushseat.domain.seatmap

import com.eooog.rushseat.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "sector",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_sector_seat_map_code",
            columnNames = ["seat_map_id", "code"]
        )
    ]
)
class Sector protected constructor() : AuditableEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "seat_map_id", nullable = false)
    lateinit var seatMap: SeatMap
        protected set

    @field:Column(name = "code", nullable = false, length = 50)
    lateinit var code: String
        protected set

    @field:Column(name = "name", nullable = false, length = 100)
    lateinit var name: String
        protected set

    @field:Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
        protected set

    fun rename(name: String) {
        this.name = validateName(name)
    }

    fun changeSortOrder(sortOrder: Int) {
        this.sortOrder = validateSortOrder(sortOrder)
    }

    companion object {
        fun create(
            seatMap: SeatMap,
            code: String,
            name: String,
            sortOrder: Int = 0,
        ): Sector {
            return Sector().apply {
                this.seatMap = seatMap
                this.code = validateCode(code)
                this.name = validateName(name)
                this.sortOrder = validateSortOrder(sortOrder)
            }
        }

        private fun validateCode(code: String): String {
            val normalized = code.trim()

            require(normalized.isNotBlank()) {
                "구역 코드는 비어 있을 수 없습니다"
            }

            require(normalized.length <= 50) {
                "구역 코드는 50자를 초과할 수 없습니다"
            }

            return normalized
        }

        private fun validateName(name: String): String {
            val normalized = name.trim()

            require(normalized.isNotBlank()) {
                "구역 이름은 비어 있을 수 없습니다"
            }

            require(normalized.length <= 100) {
                "구역 이름은 100자를 초과할 수 없습니다"
            }

            return normalized
        }

        private fun validateSortOrder(sortOrder: Int): Int {
            require(sortOrder >= 0) {
                "구역 정렬 순서는 0 이상이어야 합니다"
            }

            return sortOrder
        }
    }
}