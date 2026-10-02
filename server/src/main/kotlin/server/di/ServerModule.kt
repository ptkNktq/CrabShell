package server.di

import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.maxmind.geoip2.DatabaseReader
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import server.auth.FirebaseAdminAuthRepository
import server.auth.FirebaseAuthRepository
import server.auth.initializeFirebaseApp
import server.cache.CacheManager
import server.cache.Cacheable
import server.config.EnvConfig
import server.feeding.FeedingNotificationService
import server.feeding.FeedingRepository
import server.feeding.FeedingSettingsRepository
import server.feeding.FirestoreFeedingRepository
import server.feeding.FirestoreFeedingSettingsRepository
import server.garbage.FirestoreGarbageRepository
import server.garbage.GarbageNotificationService
import server.garbage.GarbageRepository
import server.geo.IpGeolocationService
import server.geo.MaxMindIpGeolocationService
import server.geo.NoOpIpGeolocationService
import server.loginhistory.FirestoreLoginHistoryRepository
import server.loginhistory.LoginHistoryRepository
import server.migration.FirestoreMigrations
import server.money.FirestoreMoneyRepository
import server.money.MoneyDueDateNotificationService
import server.money.MoneyRepository
import server.money.MoneyWebhookService
import server.money.PaymentWebhookService
import server.passkey.ChallengeStore
import server.passkey.ExposedPasskeyCredentialRepository
import server.passkey.PasskeyConfig
import server.passkey.PasskeyCredentialRepository
import server.passkey.PasskeyLoginService
import server.passkey.WebAuthnVerifier
import server.passkey.connectPasskeyDatabase
import server.pet.FirestorePetRepository
import server.pet.PetRepository
import server.quest.FirestorePointRepository
import server.quest.FirestoreQuestRepository
import server.quest.PointRepository
import server.quest.QuestRepository
import server.quest.QuestService
import server.quest.QuestWebhookService
import server.report.BalanceCalculationService
import java.io.Closeable
import java.io.File

private val serverModuleLogger = LoggerFactory.getLogger("server.di.ServerModule")

private const val DEFAULT_GEOIP_DB_PATH = "data/GeoLite2-City.mmdb"

private const val DEFAULT_PASSKEY_DB_PATH = "data/passkey.db"

private val PASSKEY_DATABASE = named("passkey")

private fun loadGeolocationService(): IpGeolocationService {
    val path = EnvConfig["GEOIP_DB_PATH"] ?: DEFAULT_GEOIP_DB_PATH
    val file = File(path)
    if (!file.exists()) {
        serverModuleLogger.warn("GeoLite2 DB not found at '$path'; IP geolocation disabled")
        return NoOpIpGeolocationService
    }
    return runCatching {
        val reader = DatabaseReader.Builder(file).build()
        serverModuleLogger.info("GeoLite2 DB loaded from '$path'")
        MaxMindIpGeolocationService(reader)
    }.getOrElse { e ->
        serverModuleLogger.warn("Failed to load GeoLite2 DB at '$path': ${e.message}; IP geolocation disabled")
        NoOpIpGeolocationService
    }
}

val serverModule =
    module {
        // Firestore・Firebase Auth はどちらも FirebaseApp に依存させ、初期化順を DI で保証する。
        // 起動時に初期化して、サービスアカウントの不備をリクエスト受付前に検出する。
        // 停止時の onClose は、Ktor の ApplicationStopping で koin-ktor が Koin を close したときに呼ばれる
        // （作成されていない single では null が渡る）
        single<FirebaseApp>(createdAtStart = true) { initializeFirebaseApp() } withOptions {
            onClose { app ->
                app?.let {
                    serverModuleLogger.info("Deleting FirebaseApp")
                    it.delete()
                }
            }
        }
        single<Firestore> { FirestoreClient.getFirestore(get<FirebaseApp>()) }
        single<FirebaseAuth> { FirebaseAuth.getInstance(get<FirebaseApp>()) }
        single<FirebaseAuthRepository> { FirebaseAdminAuthRepository(get()) }
        // DB ファイルの作成・スキーマ作成を起動時に済ませる
        // Exposed の Database は汎用型のため、他の DB と取り違えないよう修飾子を付ける
        single<Database>(PASSKEY_DATABASE, createdAtStart = true) {
            connectPasskeyDatabase(EnvConfig["PASSKEY_DB_PATH"] ?: DEFAULT_PASSKEY_DB_PATH)
        } withOptions {
            onClose { database ->
                database?.let {
                    serverModuleLogger.info("Closing passkey database")
                    TransactionManager.closeAndUnregister(it)
                }
            }
        }
        single<PasskeyCredentialRepository> { ExposedPasskeyCredentialRepository(get(PASSKEY_DATABASE)) }
        // 未設定時の警告を起動時ログに出すため eager 初期化する
        single(createdAtStart = true) { PasskeyConfig.fromEnv() }
        single { ChallengeStore() }
        single { WebAuthnVerifier(get()) }
        single { PasskeyLoginService(get(), get()) }
        // GeoLite2 DB のロード状態を起動時ログに出すため createdAtStart で eager 初期化する。
        // 遅延評価だと初回ログイン時まで「DB が読めているか / NoOp に落ちているか」が分からない。
        single<IpGeolocationService>(createdAtStart = true) { loadGeolocationService() } withOptions {
            onClose { service ->
                (service as? Closeable)?.let {
                    serverModuleLogger.info("Closing GeoLite2 DB")
                    it.close()
                }
            }
        }
        single<MoneyRepository> { FirestoreMoneyRepository(get()) }
        single<QuestRepository> { FirestoreQuestRepository(get()) }
        single<PointRepository> { FirestorePointRepository(get()) }
        single<FeedingRepository> { FirestoreFeedingRepository(get()) }
        single<FeedingSettingsRepository> { FirestoreFeedingSettingsRepository(get()) }
        single<GarbageRepository> { FirestoreGarbageRepository(get()) }
        single<LoginHistoryRepository> { FirestoreLoginHistoryRepository(get()) }
        single<PetRepository> { FirestorePetRepository(get()) }
        single { QuestWebhookService(get()) }
        single { MoneyWebhookService(get()) }
        single { PaymentWebhookService(get()) }
        single { MoneyDueDateNotificationService(get()) }
        single { QuestService(get(), get(), get()) }
        single { FeedingNotificationService(get(), get(), get()) }
        single { GarbageNotificationService(get()) }
        single { BalanceCalculationService() }
        single { FirestoreMigrations(get()) }
        single {
            CacheManager(
                listOf(
                    get<PetRepository>() as Cacheable,
                    get<GarbageRepository>() as Cacheable,
                    get<FeedingRepository>() as Cacheable,
                    get<MoneyRepository>() as Cacheable,
                ),
            )
        }
    }
