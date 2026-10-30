package io.github.sorry4nothing.cevios.net

import io.github.sorry4nothing.cevios.BuildConfig
import okhttp3.Interceptor

/**
 * Im Debug-Build antwortet der [DemoServer] anstelle des Netzwerks (nur wenn `BuildConfig.DEMO_DATA` gilt). Mit
 * `-PdemoLeader` ist die Demo-Person Stufenleitung.
 */
fun demoInterceptors(): List<Interceptor> = listOf(DemoServer(leader = BuildConfig.DEMO_LEADER))
