package com.eooog.rushseat.domain.venue

import com.eooog.rushseat.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Check

@Entity
@Table(name = "hall")
@Check(constraints = "capacity > 0")
class Hall protected constructor() : AuditableEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "venue_id", nullable = false)
    lateinit var venue: Venue
        protected set

    @field:Column(name = "name", nullable = false, length = 255)
    lateinit var name: String
        protected set

    @field:Column(name = "capacity", nullable = false)
    var capacity: Int = 0
        protected set

    fun rename(name: String) {
        this.name = validateName(name)
    }

    fun changeCapacity(capacity: Int) {
        this.capacity = validateCapacity(capacity)
    }

    companion object {
        fun create(
            venue: Venue,
            name: String,
            capacity: Int,
        ): Hall {
            return Hall().apply {
                this.venue = venue
                this.name = validateName(name)
                this.capacity = validateCapacity(capacity)
            }
        }

        private fun validateName(name: String): String {
            val normalized = name.trim()

            require(normalized.isNotBlank()) {
                "홀 이름은 비어 있을 수 없습니다"
            }

            require(normalized.length <= 200) {
                "홀 이름은 200자를 초과할 수 없습니다"
            }

            return normalized
        }

        private fun validateCapacity(capacity: Int): Int {
            require(capacity > 0) {
                "홀 수용 인원은 0보다 커야 합니다"
            }
            return capacity
        }
    }

}