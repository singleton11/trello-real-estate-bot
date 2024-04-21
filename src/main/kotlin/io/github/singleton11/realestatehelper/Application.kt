package io.github.singleton11.realestatehelper

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLQueryComponent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main(args: Array<String>) = EngineMain.main(args)

@Serializable
data class Input(val action: Action) {
    @Serializable
    data class Action(val type: String)
}

@Serializable
data class DataInput(val action: Action) {
    @Serializable
    data class Action(val data: Data, val type: String) {
        @Serializable
        data class Data(val card: Card) {
            @Serializable
            data class Card(val id: String, val name: String)
        }
    }
}

@Serializable
data class Alternatives(@SerialName("Results") val results: List<Results>) {
    @Serializable
    data class Results(@SerialName("GeoIdentifier") val geoIdentifier: String)
}

@Serializable
data class CardData(val desc: String)

@Serializable
data class SearchResult(val itemListElement: List<ItemListElement>) {
    @Serializable
    data class ItemListElement(val url: String)
}

@Serializable
data class PropertyInfo(val photo: List<Photo>) {
    @Serializable
    data class Photo(val contentUrl: String)
}

data class TrelloAccess(val key: String, val token: String)

@Serializable
data class AttachmentData(val id: String, val url: String, val setCover: Boolean)

@Serializable
data class TrelloCard(val id: String, val name: String, val desc: String, val idAttachmentCover: String?)

val jsonObject = Json { ignoreUnknownKeys = true }

val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(jsonObject)
    }
}

fun Application.module() {
    val key = environment.config.propertyOrNull("trello.key") ?: error("Can't read trello API key")
    val token = environment.config.propertyOrNull("trello.token") ?: error("Can't read trello API Token")

    routing {
        head("/") {
            call.respond(HttpStatusCode.OK)
        }
        post("/") {
            val input = call.receive<String>()
            val json = jsonObject
            val type = json.decodeFromString<Input>(input).action.type
            if (type == "createCard") {
                val data = json.decodeFromString<DataInput>(input)
                logger.info("Card created: $data")
                val address = data.action.data.card.name
                val cardId = data.action.data.card.id
                TrelloAccess(key.getString(), token.getString())
                    .populateCard(TrelloCard(cardId, address, "", null))
            }
            call.respond(HttpStatusCode.OK)
        }
        get("/test") {
            call.respondText("Test")
        }
    }
}

suspend fun TrelloAccess.populateCard(trelloCard: TrelloCard) {
    logger.info("Input trello card: $trelloCard")
    val address = trelloCard.name
    val urlEncodedAddress = address.encodeURLQueryComponent()
    val response =
        client.get("https://zb.funda.info/suggest/alternatives/?query=$urlEncodedAddress&max=7&type=koop&areatype=")
    val alternatives = response.body<Alternatives>()
    logger.info("Alternatives: $alternatives")

    if (alternatives.results.isEmpty()) {
        return
    }

    val selectedArea = buildString {
        append("[")
        for (alternative in alternatives.results) {
            append("\"")
            append(alternative.geoIdentifier)
            append("\",")
        }
        deleteCharAt(length - 1)
        append("]")
    }.encodeURLQueryComponent(true, true)
    val searchResponse = client.get {
        url("https://www.funda.nl/zoeken/koop?selected_area=$selectedArea")
        safariUserAgent()
    }
    val searchResponseHtml = searchResponse.body<String>()
    val regex = """(?<=<script type="application/ld\+json">)[\s\S]*?(?=</script>)""".toRegex()
    val searchResultJson = regex.find(searchResponseHtml)?.value ?: return
    val searchResult = jsonObject.decodeFromString<SearchResult>(searchResultJson)
    logger.info("Search result: $searchResult")
    val normalizedAddress = address.replace(" ", "-").lowercase()
    val link = searchResult.itemListElement.firstOrNull { it.url.contains(normalizedAddress) } ?: return

    coroutineScope {
        if (trelloCard.desc.isBlank()) {
            launch {
                val updateCardResponse = client.put {
                    url("https://api.trello.com/1/cards/${trelloCard.id}?key=$key&token=$token")
                    contentType(ContentType.Application.Json)
                    setBody(CardData(link.url))
                }
                logger.info("Update card response status: ${updateCardResponse.status}")
            }
        }

        if (trelloCard.idAttachmentCover == null) {
            launch {
                uploadImage(link, regex, trelloCard.id)
            }
        }
    }
}

private suspend fun TrelloAccess.uploadImage(
    link: SearchResult.ItemListElement,
    regex: Regex,
    cardId: String
) {
    val propertyPageResponse = client.get {
        url(link.url)
        safariUserAgent()
    }
    val propertyPageHtml = propertyPageResponse.body<String>()
    val propertyInfoJson = regex.find(propertyPageHtml)?.value ?: return
    val propertyInfo = jsonObject.decodeFromString<PropertyInfo>(propertyInfoJson)
    logger.info("Property info: $propertyInfo")
    val mainPhotoUrl = propertyInfo.photo.firstOrNull()?.contentUrl ?: return
    val createAttachmentResponse = client.post {
        url("https://api.trello.com/1/cards/$cardId/attachments?key=$key&token=$token")
        contentType(ContentType.Application.Json)
        setBody(AttachmentData(cardId, mainPhotoUrl, true))
    }
    logger.info("Create attachment response status: ${createAttachmentResponse.status}")
}

fun HttpRequestBuilder.safariUserAgent() {
    header(
        HttpHeaders.UserAgent,
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.183 Safari/537.36"
    )
}

val logger = LoggerFactory.getLogger("Application")
