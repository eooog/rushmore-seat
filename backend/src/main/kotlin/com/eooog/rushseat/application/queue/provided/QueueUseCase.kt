package com.eooog.rushseat.application.queue.provided

import com.eooog.rushseat.application.queue.AdmitQueueCommand
import com.eooog.rushseat.application.queue.AdmitQueueResult
import com.eooog.rushseat.application.queue.AdmittedMember
import com.eooog.rushseat.application.queue.EnterQueueCommand
import com.eooog.rushseat.application.queue.GetQueueStatusQuery
import com.eooog.rushseat.application.queue.LeaveQueueCommand
import com.eooog.rushseat.application.queue.QueueEnterResult
import com.eooog.rushseat.application.queue.QueueStatusResult
import com.eooog.rushseat.application.queue.RefillAdmissionsCommand
import com.eooog.rushseat.application.queue.RefillAdmissionsResult
import com.eooog.rushseat.application.queue.ValidateAdmissionCommand

interface EnterQueueUseCase {
    fun enter(command: EnterQueueCommand): QueueEnterResult
}

interface GetQueueStatusUseCase {
    fun getStatus(query: GetQueueStatusQuery): QueueStatusResult
}

interface AdmitQueueUseCase {
    fun admit(command: AdmitQueueCommand): AdmitQueueResult
}

interface ValidateAdmissionUseCase {
    fun requireAdmitted(command: ValidateAdmissionCommand): AdmittedMember
}

interface LeaveQueueUseCase {
    fun leave(command: LeaveQueueCommand)
}

interface RefillAdmissionsUseCase {
    fun refill(command: RefillAdmissionsCommand): RefillAdmissionsResult
}
