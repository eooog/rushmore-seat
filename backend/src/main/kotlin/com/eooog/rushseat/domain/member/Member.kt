package com.eooog.rushseat.domain.member

import com.eooog.rushseat.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "member")
class Member protected constructor() : AuditableEntity() {

    @field:Column(name = "name", nullable = false, length = 100)
    lateinit var name: String
        protected set

    fun rename(name: String) {
        this.name = validateName(name)
    }

    companion object {
        fun create(name: String): Member {
            return Member().apply {
                this.name = validateName(name)
            }
        }

        private fun validateName(name: String): String {
            val normalized = name.trim()
            require(normalized.isNotBlank()) { "회원 이름은 비어 있을 수 없습니다" }
            require(normalized.length <= 100) { "회원 이름은 100자를 초과할 수 없습니다" }
            return normalized
        }
    }
}
