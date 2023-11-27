package codemwnci.bootsocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.*
import org.springframework.web.socket.config.annotation.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.atomic.AtomicLong


class User(val id: Long, val name: String)
class Message(val msgType: String, val data: Any)

class ChatHandler : TextWebSocketHandler() {

    val sessionList = HashMap<WebSocketSession, User>()
    var uids = AtomicLong(0)

    @Throws(Exception::class)
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessionList -= session
    }

    public override fun handleTextMessage(session: WebSocketSession?, message: TextMessage?) {
        val json = ObjectMapper().readTree(message?.payload)
        // {type: "join/say", data: "name/msg"}
        when (json.get("type").asText()) {
            "join" -> {
                val user = User(uids.getAndIncrement(), json.get("data").asText())
                sessionList.put(session!!, user)
                // tell this user about all other users
                emit(session, Message("users", sessionList.values))
                // tell all other users, about this user
                broadcastToOthers(session, Message("join", user))
            }
            "say" -> {
                broadcast(Message("say", json.get("data").asText()))
            }
        }
    }

    fun emit(session: WebSocketSession, msg: Message) = session.sendMessage(TextMessage(jacksonObjectMapper().writeValueAsString(msg)))
    fun broadcast(msg: Message) = sessionList.forEach { emit(it.key, msg) }
    fun broadcastToOthers(me: WebSocketSession, msg: Message) = sessionList.filterNot { it.key == me }.forEach { emit(it.key, msg) }
}

@Configuration @EnableWebSocket
class WSConfig : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(ChatHandler(), "/chat").withSockJS()
    }
}


@SpringBootApplication
class ChatApplication

fun main(args: Array<String>) {
    runApplication<ChatApplication>(*args)
}