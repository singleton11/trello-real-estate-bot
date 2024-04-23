package io.github.singleton11.realestatehelper

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchForAlternativesTest {
    @Test
    fun `Logger 166`() = runBlocking {
        // Given
        val address = "Logger 166"

        // When
        val alternatives = searchAlternativeForAddress(address)

        // Then
        assertEquals(2, alternatives.size)
    }
}