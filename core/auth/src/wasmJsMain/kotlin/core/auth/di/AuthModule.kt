package core.auth.di

import core.auth.AuthRepository
import core.auth.AuthRepositoryImpl
import core.auth.AuthStateHolder
import core.common.TabResumedEvent
import org.koin.dsl.module

val authModule =
    module {
        single { AuthStateHolder() }
        single<AuthRepository> { AuthRepositoryImpl(get()) }
        // TabResumedEvent は core:common 定義だが、AuthenticatedApp（feature:auth）が
        // トークンリフレッシュ完了後に emit する起点なので、認証関連の DI としてここに登録
        single { TabResumedEvent() }
    }
