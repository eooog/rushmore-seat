package com.eooog.rushseat.domain.seatmap

import com.eooog.rushseat.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Check

@Entity
@Table(
    name = "tile",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_tile_seat_map_code",
            columnNames = ["seat_map_id", "code"]
        )
    ]
)
@Check(constraints = "seat_count >= 0")
class Tile protected constructor() : AuditableEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "seat_map_id", nullable = false)
    lateinit var seatMap: SeatMap
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "sector_id", nullable = false)
    lateinit var sector: Sector
        protected set

    @field:Column(name = "code", nullable = false, length = 50)
    lateinit var code: String
        protected set

    @field:Column(name = "name", nullable = false, length = 100)
    lateinit var name: String
        protected set

    /**
     * WebSocket delta sync를 위한 tile version.
     *
     * 주의:
     * 현재 schema에서는 seat_map 기준 tile version이다.
     * 여러 Performance가 같은 SeatMap을 공유할 경우 회차별 version 분리가 필요하다.
     */
    @field:Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    @field:Column(name = "seat_count", nullable = false)
    var seatCount: Int = 0
        protected set

    fun rename(name: String) {
        this.name = validateName(name)
    }

    fun increaseVersion() {
        this.version += 1
    }

    fun changeSeatCount(seatCount: Int) {
        this.seatCount = validateSeatCount(seatCount)
    }

    fun increaseSeatCount() {
        this.seatCount += 1
    }

    companion object {
        fun create(
            seatMap: SeatMap,
            sector: Sector,
            code: String,
            name: String,
            seatCount: Int = 0,
        ): Tile {
            require(sector.seatMap == seatMap) {
                "타일의 구역은 동일한 좌석 배치에 속해야 합니다"
            }

            return Tile().apply {
                this.seatMap = seatMap
                this.sector = sector
                this.code = validateCode(code)
                this.name = validateName(name)
                this.seatCount = validateSeatCount(seatCount)
                this.version = 0
            }
        }

        private fun validateCode(code: String): String {
            val normalized = code.trim()

            require(normalized.isNotBlank()) {
                "타일 코드는 비어 있을 수 없습니다"
            }

            require(normalized.length <= 50) {
                "타일 코드는 50자를 초과할 수 없습니다"
            }

            return normalized
        }

        private fun validateName(name: String): String {
            val normalized = name.trim()

            require(normalized.isNotBlank()) {
                "타일 이름은 비어 있을 수 없습니다"
            }

            require(normalized.length <= 100) {
                "타일 이름은 100자를 초과할 수 없습니다"
            }

            return normalized
        }

        private fun validateSeatCount(seatCount: Int): Int {
            require(seatCount >= 0) {
                "타일 좌석 수는 0 이상이어야 합니다"
            }

            return seatCount
        }
    }
}