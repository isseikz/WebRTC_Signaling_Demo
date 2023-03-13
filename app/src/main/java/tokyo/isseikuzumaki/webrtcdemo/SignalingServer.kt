package tokyo.isseikuzumaki.webrtcdemo

import android.util.Log
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.collections.HashMap

class SignalingServer: Server {
    companion object {
        private const val TAG = "SignalingServer"
    }
    private val waitingReceivers = HashMap<String, DefaultWebSocketServerSession>()
    private val waitingSenders = HashMap<String, DefaultWebSocketServerSession>()

    override fun initialize(application: Application) {
        application.apply {
            install(WebSockets)
        }
    }

    override fun setRouting(routing: Routing) {
        routing.apply {
            webSocket("/") {
                for (frame in incoming) {
                    send("Received: ${(frame as Frame.Text).readText()}")
                }
            }
            webSocket("/wait_offer") {
                val endpoint = "/wait_offer"
                Log.d(TAG, endpoint)
                var token = ""
                try {
                    for (frame in incoming) {
                       val json = (frame as Frame.Text).readText()
                        val request = Json.decodeFromString<WaitOfferRequest>(json)
                        token = request.token
                        Log.d(TAG, "[$endpoint] token saved: $token")
                        waitingReceivers[token] = this
                    }
                } catch (e: ClosedReceiveChannelException) {
                    Log.e(TAG, "[$endpoint] closed", e)
                    waitingReceivers.remove(token)
                } catch (e: Throwable) {
                    Log.e(TAG, "[$endpoint] exception thrown", e)
                    waitingReceivers.remove(token)
                }
            }
            webSocket("/offer") {
                val endpoint = "/offer"
                Log.d(TAG, endpoint)
                var token = ""
                try {
                    for (frame in incoming) {
                        val json = (frame as Frame.Text).readText()
                        Json.decodeFromString<Offer>(json).let { offer ->
                            token = offer.token
                            waitingReceivers[token]?.let{ receiver ->
                                receiver.send(json)
                                waitingSenders[token] = this
                            }
                            Log.d(TAG, "[$endpoint] sdp sent to $token")
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    Log.e(TAG, "$endpoint closed", e)
                    waitingSenders.remove(token)
                } catch (e: Throwable) {
                    Log.e(TAG, "$endpoint exception thrown", e)
                    waitingSenders.remove(token)
                }
            }
            webSocket("/answer") {
                val endpoint = "/answer"
                Log.d(TAG, endpoint)
                try {
                    for (frame in incoming) {
                        val json = (frame as Frame.Text).readText()
                        Json.decodeFromString<Answer>(json).let {
                            waitingSenders[it.token]?.send(json)
                            Log.d(TAG, "[$endpoint] sdp sent to ${it.token}")
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    Log.e(TAG, "$endpoint closed", e)
                } catch (e: Throwable) {
                    Log.e(TAG, "$endpoint exception thrown", e)
                }
            }
        }
    }
}

@Serializable
data class WaitOfferRequest(val token: String)

@Serializable
data class Offer(val token: String, val sdp: String, val candidate: String)

@Serializable
data class Answer(val token: String, val sdp: String, val candidate: String)
