package io.github.singleton11.realestatehelper

import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationKtTest {

    @Test
    fun normalizeAddress() {
        // Given
        val address = "H. Diesveldsingel 158"

        // When
        val normalizedAdress = address.normalizeAddress()

        // Then
        assertEquals("h-diesveldsingel-158", normalizedAdress)
    }
}
