package io.github.singleton11.realestatehelper

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun main(args: Array<String>) {
    val key = System.getenv("TRELLO_API_KEY") ?: error("There is no TRELLO_API_KEY")
    val token = System.getenv("TRELLO_API_TOKEN") ?: error("There is no TRELLO_API_TOKEN")
    val boardId = "5e85ab69876f17187085de1f"

    val cardsResponse = client.get("https://api.trello.com/1/boards/$boardId/cards?key=$key&token=$token")
    val cards = cardsResponse.body<List<TrelloCard>>()
    logger.info("Cards: $cards")
    coroutineScope {
        for (card in cards) {
            launch {
                TrelloAccess(key, token).populateCard(card)
            }
        }
    }
}