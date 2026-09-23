package core.auth.di

import core.auth.AuthRepository
import core.auth.AuthRepositoryImpl
import core.auth.AuthStateHolder
import core.common.ApplicationScope
import core.common.FeedingSettingsChangedEvent
import core.common.TabResumedEvent
import org.koin.dsl.module

val authModule =
    module {
        single { AuthStateHolder() }
        single<AuthRepository> { AuthRepositoryImpl(get()) }
        // TabResumedEvent は core:common 定義だが、AuthenticatedApp（feature:auth）が
        // トークンリフレッシュ完了後に emit する起点なので、認証関連の DI としてここに登録
        single { TabResumedEvent() }
        // 同じ理由でアプリ全体共有イベントバスはここに集約
        single { FeedingSettingsChangedEvent() }
        // アプリ全体で 1 つの CoroutineScope（画面より長く生存させる処理用）もここに集約
        single { ApplicationScope() }
    }
