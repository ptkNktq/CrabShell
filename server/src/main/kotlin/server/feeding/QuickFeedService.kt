package server.feeding

import model.MealTime
import org.slf4j.LoggerFactory
import server.config.EnvConfig
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = LoggerFactory.getLogger("QuickFeedService")

class QuickFeedService(
    private val feedingRepository: FeedingRepository,
) {
    private val secret: String? = EnvConfig["QUICK_FEED_SECRET"]

    fun generateToken(
        petId: String,
        date: String,
        mealTime: MealTime,
    ): String? {
        val key = secret ?: return null
        return hmacSha256(key, "$petId:$date:${mealTime.name}")
    }

    suspend fun execute(
        petId: String,
        date: String,
        mealTime: MealTime,
        token: String,
    ): Boolean {
        val expected = generateToken(petId, date, mealTime) ?: return false
        if (!timingSafeEquals(expected, token)) {
            logger.warn("Invalid quick-feed token for pet=$petId date=$date meal=$mealTime")
            return false
        }

        val log = feedingRepository.getFeedingLog(petId, date)
        if (log.feedings[mealTime]?.done == true) {
            logger.info("Quick-feed already done for pet=$petId date=$date meal=$mealTime")
            return true
        }

        val timestamp = Instant.now().toString()
        feedingRepository.recordFeeding(petId, date, mealTime, timestamp)
        logger.info("Quick-feed recorded for pet=$petId date=$date meal=$mealTime")
        return true
    }

    companion object {
        internal fun hmacSha256(
            key: String,
            data: String,
        ): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
            return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
        }

        private fun timingSafeEquals(
            a: String,
            b: String,
        ): Boolean {
            if (a.length != b.length) return false
            var result = 0
            for (i in a.indices) {
                result = result or (a[i].code xor b[i].code)
            }
            return result == 0
        }
    }
}
