package com.diabecarekids.app.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpClientEngine(): HttpClientEngine = OkHttp.create()

actual fun epochMillisNow(): Long = System.currentTimeMillis()
