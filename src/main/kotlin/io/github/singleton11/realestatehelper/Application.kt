package io.github.singleton11.realestatehelper

import io.github.singleton11.realestatehelper.DataInput.Action.Data
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    routing {
        head("/") {
            call.respond(HttpStatusCode.OK)
        }
        post("/") {
            val input = call.receive<String>()
            val json = Json { ignoreUnknownKeys = true }
            val type = json.decodeFromString<Input>(input).action.type
            if (type == "createCard") {
                val data = json.decodeFromString<DataInput>(input)
                val address = data.action.data.card.name
                println(address)
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}
