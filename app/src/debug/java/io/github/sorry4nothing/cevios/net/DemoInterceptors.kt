package io.github.sorry4nothing.cevios.net

import okhttp3.Interceptor

/** Im Debug-Build antwortet der [DemoServer] anstelle des Netzwerks (nur wenn `BuildConfig.DEMO_DATA` gilt). */
fun demoInterceptors(): List<Interceptor> = listOf(DemoServer())
