package server.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import model.Feeding
import model.FeedingLog
import model.MealTime
import model.Pet
import server.feeding.FeedingRepository
import server.pet.PetRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedingMcpToolsTest {
    private val feedingRepository = mockk<FeedingRepository>(relaxUnitFun = true)
    private val petRepository = mockk<PetRepository>()

    // JST 2026-03-14 08:00
    private val now = Instant.parse("2026-03-13T23:00:00Z")
    private val tools = FeedingMcpTools(feedingRepository, petRepository) { now }

    private val uid = "uid1"
    private val pet = Pet(id = "pet1", name = "ぬい")

    private fun args(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } }

    private fun CallToolResult.text(): String = (content.single() as TextContent).text

    private fun CallToolResult.log(): FeedingMcpTools.FeedingLogResult =
        Json.decodeFromString(FeedingMcpTools.FeedingLogResult.serializer(), text())

    private fun CallToolResult.recordResult(): FeedingMcpTools.RecordFeedingResult =
        Json.decodeFromString(FeedingMcpTools.RecordFeedingResult.serializer(), text())

    @Test
    fun getFeedingLogDefaultsToCurrentFeedingDateOfFirstMemberPet() =
        runTest {
            coEvery { petRepository.getPetsForMember(uid) } returns listOf(pet, Pet(id = "pet2", name = "もう一匹"))
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns FeedingLog(date = "2026-03-14", note = "元気")

            val result = tools.getFeedingLog(uid, null)

            assertEquals(null, result.isError)
            val log = result.log()
            assertEquals("ぬい", log.petName)
            assertEquals("2026-03-14", log.date)
            assertEquals("元気", log.note)
            // 未給餌も省略せずに出力する
            assertTrue("\"done\":false" in result.text())
        }

    @Test
    fun getFeedingLogUsesPreviousDayBefore5amJst() =
        runTest {
            // JST 2026-03-14 04:30
            val earlyTools = FeedingMcpTools(feedingRepository, petRepository) { Instant.parse("2026-03-13T19:30:00Z") }
            coEvery { petRepository.getPetsForMember(uid) } returns listOf(pet)
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-13") } returns FeedingLog(date = "2026-03-13")

            assertEquals("2026-03-13", earlyTools.getFeedingLog(uid, null).log().date)
        }

    @Test
    fun getFeedingLogUsesSpecifiedDate() =
        runTest {
            coEvery { petRepository.getPetsForMember(uid) } returns listOf(pet)
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-01") } returns FeedingLog(date = "2026-03-01")

            assertEquals("2026-03-01", tools.getFeedingLog(uid, args("date" to "2026-03-01")).log().date)
        }

    @Test
    fun recordFeedingRecordsCurrentTime() =
        runTest {
            coEvery { petRepository.getPetsForMember(uid) } returns listOf(pet)
            val recorded =
                FeedingLog(
                    date = "2026-03-14",
                    feedings =
                        MealTime.entries.associateWith { Feeding() } + (MealTime.MORNING to Feeding(true, now.toString())),
                )
            coEvery { feedingRepository.recordFeedingIfNotDone("pet1", "2026-03-14", MealTime.MORNING, now.toString()) } returns true
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns recorded

            val result = tools.recordFeeding(uid, args("mealTime" to "MORNING"))

            coVerify(exactly = 1) { feedingRepository.recordFeedingIfNotDone("pet1", "2026-03-14", MealTime.MORNING, now.toString()) }
            assertEquals(true, result.recordResult().recorded)
            assertEquals(Feeding(true, now.toString()), result.recordResult().feedingLog.feedings[MealTime.MORNING])
        }

    @Test
    fun recordFeedingReturnsExistingRecordWhenAlreadyDone() =
        runTest {
            coEvery { petRepository.getPetsForMember(uid) } returns listOf(pet)
            val existing =
                FeedingLog(
                    date = "2026-03-14",
                    feedings =
                        MealTime.entries.associateWith { Feeding() } + (MealTime.MORNING to Feeding(true, "2026-03-13T22:00:00Z")),
                )
            coEvery { feedingRepository.recordFeedingIfNotDone(any(), any(), any(), any()) } returns false
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns existing

            val result = tools.recordFeeding(uid, args("mealTime" to "MORNING"))

            assertEquals(false, result.recordResult().recorded)
            assertEquals(
                "2026-03-13T22:00:00Z",
                result
                    .recordResult()
                    .feedingLog.feedings[MealTime.MORNING]
                    ?.timestamp,
            )
        }

    @Test
    fun updateNoteReplacesNote() =
        runTest {
            coEvery { petRepository.getPetsForMember(uid) } returns listOf(pet)
            coEvery { feedingRepository.getFeedingLog("pet1", "2026-03-14") } returns FeedingLog(date = "2026-03-14", note = "食欲あり")

            val result = tools.updateNote(uid, args("note" to "食欲あり"))

            coVerify(exactly = 1) { feedingRepository.updateNote("pet1", "2026-03-14", "食欲あり") }
            assertEquals("食欲あり", result.log().note)
        }

    @Test
    fun invalidArgumentsAreRejectedWithoutWriting() =
        runTest {
            coEvery { petRepository.getPetsForMember(uid) } returns listOf(pet)

            // ツール本体は例外を投げ、MCP 経由ではツールのエラー結果に変換される（McpRoutesTest で確認）
            val invalidCalls =
                listOf<suspend () -> Unit>(
                    { tools.getFeedingLog(uid, args("date" to "2026/03/14")) },
                    { tools.recordFeeding(uid, args("mealTime" to "BREAKFAST")) },
                    { tools.recordFeeding(uid, null) },
                    { tools.updateNote(uid, null) },
                    { tools.recordFeeding(uid, buildJsonObject { put("mealTime", JsonPrimitive(1)) }) },
                )
            invalidCalls.forEach { call ->
                assertTrue(runCatching { call() }.isFailure)
            }
            coVerify(exactly = 0) { feedingRepository.recordFeedingIfNotDone(any(), any(), any(), any()) }
            coVerify(exactly = 0) { feedingRepository.updateNote(any(), any(), any()) }
        }

    @Test
    fun userWithoutPetIsRejected() =
        runTest {
            coEvery { petRepository.getPetsForMember(uid) } returns emptyList()

            val error = runCatching { tools.getFeedingLog(uid, null) }.exceptionOrNull()

            assertEquals("メンバーになっているペットがいません", error?.message)
        }
}
