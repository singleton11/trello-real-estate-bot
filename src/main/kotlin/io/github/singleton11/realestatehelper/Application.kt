package io.github.singleton11.realestatehelper

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun main(args: Array<String>) = EngineMain.main(args)

@Serializable
data class Input(val action: Action) {
    @Serializable
    data class Action(val type: String, val display: Display) {
        @Serializable
        data class Display(val translationKey: String)
    }
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

@Serializable
data class ChangeListDataInput(val action: Action) {
    @Serializable
    data class Action(val data: Data, val type: String) {
        @Serializable
        data class Data(val card: Card) {
            @Serializable
            data class Card(val idList: String, val id: String, val name: String)
        }
    }
}

@Serializable
data class Comment(val text: String)

val jsonObject = Json { ignoreUnknownKeys = true }

val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(jsonObject)
    }
}

fun Application.module() {
    val key = environment.config.propertyOrNull("trello.key") ?: error("Can't read trello API key")
    val token = environment.config.propertyOrNull("trello.token") ?: error("Can't read trello API Token")

    val viewingList = environment.config.propertyOrNull("viewing.trelloList") ?: error("Can't read trello viewing list")
    val message = environment.config.propertyOrNull("viewing.message") ?: error("Can't read viewing message")
    val email = environment.config.propertyOrNull("viewing.email") ?: error("Can't read viewing email")
    val salutation = environment.config.propertyOrNull("viewing.salutation") ?: error("Can't read viewing salutation")
    val firstName = environment.config.propertyOrNull("viewing.firstName") ?: error("Can't read viewing firstName")
    val lastName = environment.config.propertyOrNull("viewing.lastName") ?: error("Can't read viewing lastName")
    val phone = environment.config.propertyOrNull("viewing.phone") ?: error("Can't read viewing phone")

    routing {
        head("/") {
            call.respond(HttpStatusCode.OK)
        }
        post("/") {
            val input = call.receive<String>()
            logger.info("Raw input $input")
            val json = jsonObject
            val inputData = json.decodeFromString<Input>(input)
            val type = inputData.action.type
            when (type) {
                "createCard" -> {
                    val data = json.decodeFromString<DataInput>(input)
                    logger.info("Card created: $data")
                    val address = data.action.data.card.name
                    val cardId = data.action.data.card.id
                    TrelloAccess(key.getString(), token.getString())
                        .populateCard(TrelloCard(cardId, address, "", null))
                }

                "updateCard" -> {
                    logger.info("Sub event: ${inputData.action.display.translationKey}")
                    if (inputData.action.display.translationKey == "action_move_card_from_list_to_list") {
                        val data = json.decodeFromString<ChangeListDataInput>(input)
                        logger.info("Card moved: $data")
                        if (data.action.data.card.idList == viewingList.getString()) {
                            // get funda url
                            val trelloCard = TrelloAccess(key.getString(), token.getString())
                                .getTrelloCard(data.action.data.card.id)
                            logger.info("Got trello card $trelloCard")

                            // get funda link
                            fun findLink(text: String): String {
                                val regex = Regex("https://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
                                val matches = regex.findAll(text)
                                return matches.map { it.value }.first()
                            }

                            val fundaUrl = findLink(trelloCard.desc)
                            // open a form to get a request code
                            logger.info("Funda url obtained $fundaUrl from trello card $trelloCard")
                            val url = URLBuilder(fundaUrl).appendPathSegments("bezichtiging").build()
                            logger.info("Viewing form url $url")
                            val fundaViewingFormHtml = client.get {
                                url(url)
                                safariUserAgent()
                            }.body<String>()
                            val regex = "<input name=\"__RequestVerificationToken\" type=\"hidden\" value=\"(.*?)\""
                                .toRegex()
                            val requestToken = regex.find(fundaViewingFormHtml)?.groupValues?.get(1)
                            logger.info("Request verification token obtained $requestToken")
                            requestToken?.let {
                                // send a form
                                val submitResponse = client.submitForm(
                                    url.toString(),
                                    formParameters = Parameters.build {
                                        append("__RequestVerificationToken", requestToken)
                                        append("Opmerking", message.getString())
                                        append("Aanhef", salutation.getString())
                                        append("Voornaam", firstName.getString())
                                        append("Achternaam", lastName.getString())
                                        append("Telefoon", phone.getString())
                                        append("Email", email.getString())
                                    },
                                    encodeInQuery = true
                                ) {
                                    safariUserAgent()
                                }

                                logger.info("Viewing form submitted, status ${submitResponse.status}")
                                logger.info("Viewing form submission response html")
                                logger.info(submitResponse.body())
                                if (submitResponse.status.isSuccess()) {
                                    // add a comment to a card
                                    val postCommentResponse = client.post {
                                        url("https://api.trello.com/1/cards/${trelloCard.id}/actions/comments?key=${key.getString()}&token=${token.getString()}")
                                        contentType(ContentType.Application.Json)
                                        setBody(Comment("Viewing requested"))
                                    }
                                    logger.info("Adding comment status ${postCommentResponse.status}")
                                }
                            }
                        }
                    }
                }
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
    val alternatives = searchAlternativeForAddress(address)

    if (alternatives.isEmpty()) {
        return
    }

    val selectedArea = buildString {
        append("[")
        for (alternative in alternatives) {
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
    val normalizedAddress = address.normalizeAddress()
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

suspend fun TrelloAccess.getTrelloCard(cardId: String): TrelloCard =
    client.get("https://api.trello.com/1/cards/$cardId?key=$key&token=$token").body<TrelloCard>()

internal suspend fun searchAlternativeForAddress(address: String): List<Alternatives.Results> {
    val urlEncodedAddress = address.encodeURLQueryComponent()
    val response = client
        .get("https://zb.funda.info/suggest/alternatives/?query=$urlEncodedAddress&max=7&type=koop&areatype=")
    val alternatives = response.body<Alternatives>()
    logger.info("Alternatives: $alternatives")
    return alternatives.results.filter { it.geoIdentifier.contains("/straat-") }
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

fun String.normalizeAddress(): String = replace(" ", "-")
    .replace(".", "")
    .replace("'", "")
    .lowercase()

val logger: Logger = LoggerFactory.getLogger("Application")
