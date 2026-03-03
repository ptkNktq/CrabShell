package server.quest

import model.CreateQuestRequest
import model.Quest
import model.QuestCategory
import model.QuestStatus
import model.WebhookEvent
import java.time.Instant
import java.time.LocalDate

private const val MAX_ACTIVE_QUESTS = 10

sealed class QuestResult<out T> {
    data class Success<T>(
        val data: T,
    ) : QuestResult<T>()

    data class NotFound(
        val message: String,
    ) : QuestResult<Nothing>()

    data class Conflict(
        val message: String,
    ) : QuestResult<Nothing>()

    data class Forbidden(
        val message: String,
    ) : QuestResult<Nothing>()
}

class QuestService(
    private val questRepository: QuestRepository,
    private val pointRepository: PointRepository,
    private val webhookService: WebhookService,
) {
    suspend fun listQuests(
        statusFilter: String?,
        now: LocalDate = LocalDate.now(),
    ): List<Quest> {
        val rawQuests = questRepository.getQuests(statusFilter)
        return rawQuests.map { (id, data) ->
            val status = data["status"] as? String ?: "Open"
            val deadline = data["deadline"] as? String

            // 期限切れチェック: Open/Accepted のクエストで期限を過ぎていたら Expired に更新
            val effectiveStatus =
                if (isQuestExpired(status, deadline, now)) {
                    questRepository.updateQuest(id, mapOf("status" to "Expired"))
                    "Expired"
                } else {
                    status
                }

            val questStatus = QuestStatus.entries.find { it.name == effectiveStatus } ?: QuestStatus.Open
            buildQuest(id, data, questStatus)
        }
    }

    suspend fun createQuest(
        request: CreateQuestRequest,
        creatorUid: String,
        creatorName: String,
        now: Instant = Instant.now(),
    ): QuestResult<Quest> {
        val activeCount = questRepository.countActiveQuests()
        if (activeCount >= MAX_ACTIVE_QUESTS) {
            return QuestResult.Conflict("同時に発行できるクエストは${MAX_ACTIVE_QUESTS}件までです")
        }

        val questData =
            mapOf(
                "title" to request.title,
                "description" to request.description,
                "category" to request.category.name,
                "rewardPoints" to request.rewardPoints,
                "creatorUid" to creatorUid,
                "creatorName" to creatorName,
                "assigneeUid" to null,
                "assigneeName" to null,
                "status" to QuestStatus.Open.name,
                "deadline" to request.deadline,
                "createdAt" to now.toString(),
                "completedAt" to null,
            )

        val docId = questRepository.createQuest(questData)
        val created =
            Quest(
                id = docId,
                title = request.title,
                description = request.description,
                category = request.category,
                rewardPoints = request.rewardPoints,
                creatorUid = creatorUid,
                creatorName = creatorName,
                status = QuestStatus.Open,
                deadline = request.deadline,
                createdAt = questData["createdAt"] as String,
            )

        webhookService.notify(WebhookEvent.QUEST_CREATED, created)
        return QuestResult.Success(created)
    }

    suspend fun acceptQuest(
        id: String,
        assigneeUid: String,
        assigneeName: String,
    ): QuestResult<Quest> {
        val quest =
            questRepository.getQuest(id)
                ?: return QuestResult.NotFound("Quest not found")

        val data = quest.second
        val status = data["status"] as? String ?: ""
        val creatorUid = data["creatorUid"] as? String ?: ""

        if (status != QuestStatus.Open.name) {
            return QuestResult.Conflict("Quest is not open")
        }
        if (creatorUid == assigneeUid) {
            return QuestResult.Forbidden("Cannot accept own quest")
        }

        questRepository.updateQuest(
            id,
            mapOf(
                "status" to QuestStatus.Accepted.name,
                "assigneeUid" to assigneeUid,
                "assigneeName" to assigneeName,
            ),
        )

        return QuestResult.Success(buildQuest(id, data, QuestStatus.Accepted, assigneeUid, assigneeName))
    }

    suspend fun verifyQuest(
        id: String,
        verifierUid: String,
        now: Instant = Instant.now(),
    ): QuestResult<Quest> {
        val quest =
            questRepository.getQuest(id)
                ?: return QuestResult.NotFound("Quest not found")

        val data = quest.second
        val status = data["status"] as? String ?: ""
        val creatorUid = data["creatorUid"] as? String ?: ""

        if (status != QuestStatus.Accepted.name) {
            return QuestResult.Conflict("Quest is not accepted")
        }
        if (creatorUid != verifierUid) {
            return QuestResult.Forbidden("Only creator can verify")
        }

        val completedAt = now.toString()
        questRepository.updateQuest(
            id,
            mapOf(
                "status" to QuestStatus.Verified.name,
                "completedAt" to completedAt,
            ),
        )

        // ポイント付与
        val assigneeUid = data["assigneeUid"] as? String
        val assigneeName = data["assigneeName"] as? String ?: ""
        val rewardPoints = (data["rewardPoints"] as? Number)?.toInt() ?: 0
        val questTitle = data["title"] as? String ?: ""
        if (assigneeUid != null && rewardPoints > 0) {
            pointRepository.awardPoints(assigneeUid, assigneeName, rewardPoints, "クエスト達成: $questTitle", questId = id)
        }

        val verifiedQuest = buildQuest(id, data, QuestStatus.Verified, completedAtOverride = completedAt)
        webhookService.notify(WebhookEvent.QUEST_VERIFIED, verifiedQuest)
        return QuestResult.Success(verifiedQuest)
    }

    suspend fun deleteQuest(
        id: String,
        requesterUid: String,
    ): QuestResult<Unit> {
        val quest =
            questRepository.getQuest(id)
                ?: return QuestResult.NotFound("Quest not found")

        val data = quest.second
        val status = data["status"] as? String ?: ""
        val creatorUid = data["creatorUid"] as? String ?: ""

        if (status != QuestStatus.Open.name && status != QuestStatus.Expired.name) {
            return QuestResult.Conflict("Can only delete open or expired quests")
        }
        if (creatorUid != requesterUid) {
            return QuestResult.Forbidden("Only creator can delete")
        }

        questRepository.deleteQuest(id)
        return QuestResult.Success(Unit)
    }

    private fun parseCategory(value: String?): QuestCategory =
        try {
            QuestCategory.valueOf(value ?: "Other")
        } catch (_: IllegalArgumentException) {
            QuestCategory.Other
        }

    private fun buildQuest(
        id: String,
        data: Map<String, Any>,
        statusOverride: QuestStatus,
        assigneeUidOverride: String? = null,
        assigneeNameOverride: String? = null,
        completedAtOverride: String? = null,
    ): Quest =
        Quest(
            id = id,
            title = data["title"] as? String ?: "",
            description = data["description"] as? String ?: "",
            category = parseCategory(data["category"] as? String),
            rewardPoints = (data["rewardPoints"] as? Number)?.toInt() ?: 0,
            creatorUid = data["creatorUid"] as? String ?: "",
            creatorName = data["creatorName"] as? String ?: "",
            assigneeUid = assigneeUidOverride ?: data["assigneeUid"] as? String,
            assigneeName = assigneeNameOverride ?: data["assigneeName"] as? String,
            status = statusOverride,
            deadline = data["deadline"] as? String,
            createdAt = data["createdAt"] as? String ?: "",
            completedAt = completedAtOverride ?: data["completedAt"] as? String,
        )
}
