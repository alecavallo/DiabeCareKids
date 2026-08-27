package com.diabecarekids.app

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {
    @Test
    fun greetingDefaultsToHelloWorld() {
        assertEquals("Hello World", Greeting().text)
    }
}
