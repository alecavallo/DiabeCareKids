package com.diabecarekids.app

/**
 * Domain seam shared by all platforms.
 *
 * Baseline value placeholder that later DM1 modules (SOS, meal logging, carb
 * estimation, PDF, reminders) can build on without coupling to the UI module.
 */
data class Greeting(val text: String = "Hello World")
