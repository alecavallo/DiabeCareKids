package com.diabecarekids.app

/**
 * Platform seam proving the expect/actual mechanism works. Each supported
 * platform provides its own actual implementation (see Platform.android.kt).
 */
expect fun platformName(): String
