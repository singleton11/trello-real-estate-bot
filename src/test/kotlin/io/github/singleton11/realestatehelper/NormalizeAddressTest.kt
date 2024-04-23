package io.github.singleton11.realestatehelper

import kotlin.test.Test
import kotlin.test.assertEquals

class NormalizeAddressTest {

    @Test
    fun `H Diesveldsingel 158`() {
        // Given
        val address = "H. Diesveldsingel 158"

        // When
        val normalizedAdress = address.normalizeAddress()

        // Then
        assertEquals("h-diesveldsingel-158", normalizedAdress)
    }

    @Test
    fun `Wethouder In 't Veldstraat 136`() {
        // Given
        val address = "Wethouder In 't Veldstraat 136"

        // When
        val normalizedAdress = address.normalizeAddress()

        // Then
        assertEquals("wethouder-in-t-veldstraat-136", normalizedAdress)
    }
}
