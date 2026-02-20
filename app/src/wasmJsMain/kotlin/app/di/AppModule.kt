package app.di

import core.auth.di.authModule
import core.network.di.networkModule
import feature.auth.di.featureAuthModule
import feature.dashboard.di.dashboardModule
import feature.feeding.di.feedingModule
import feature.money.di.moneyModule
import feature.payment.di.paymentModule
import feature.quest.di.questModule
import feature.report.di.reportModule
import feature.settings.di.settingsModule

val appModules =
    listOf(
        authModule,
        networkModule,
        featureAuthModule,
        dashboardModule,
        feedingModule,
        moneyModule,
        paymentModule,
        questModule,
        reportModule,
        settingsModule,
    )
