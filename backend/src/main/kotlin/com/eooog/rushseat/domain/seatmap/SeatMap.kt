package com.eooog.rushseat.domain.seatmap

import com.eooog.rushseat.domain.AuditableEntity
import com.eooog.rushseat.domain.venue.Hall
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Check

@Entity
@Table(
    name = "seat_map",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_seat_map_hall_version",
            columnNames = ["hall_id", "version"]
        )
    ]
)
@Check(constraints = "version > 0")
class SeatMap protected constructor() : AuditableEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "hall_id", nullable = false)
    lateinit var hall: Hall
        protected set

    @field:Column(name = "name", nullable = false, length = 200)
    lateinit var name: String
        protected set

    @field:Column(name = "version", nullable = false)
    var version: Int = 0
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 30)
    var status: SeatMapStatus = SeatMapStatus.ACTIVE
        protected set

    fun rename(name: String) {
        this.name = validateName(name)
    }

    fun activate() {
        this.status = SeatMapStatus.ACTIVE
    }

    fun deactivate() {
        this.status = SeatMapStatus.INACTIVE
    }

    fun assertActive() {
        check(status == SeatMapStatus.ACTIVE) {
            "활성 좌석 배치도만 사용할 수 있습니다"
        }
    }

    fun isActive(): Boolean {
        return status == SeatMapStatus.ACTIVE
    }

    companion object {
        fun create(
            hall: Hall,
            name: String,
            version: Int,
            status: SeatMapStatus = SeatMapStatus.ACTIVE,
        ): SeatMap {
            return SeatMap().apply {
                this.hall = hall
                this.name = validateName(name)
                this.version = validateVersion(version)
                this.status = status
            }
        }

        private fun validateName(name: String): String {
            val normalized = name.trim()

            require(normalized.isNotBlank()) {
                "좌석 배치 이름은 비어 있을 수 없습니다"
            }

            require(normalized.length <= 200) {
                "좌석 배치 이름은 200자를 초과할 수 없습니다"
            }

            return normalized
        }

        private fun validateVersion(version: Int): Int {
            require(version > 0) {
                "좌석 배치 버전은 0보다 커야 합니다"
            }

            return version
        }
    }
}