package tokyo.isseikuzumaki.webrtcdemo

import android.util.Log
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SignalingServer: Server {
    companion object {
        private const val TAG = "SignalingServer"
        private val PROTOCOL_ERROR: (Throwable) -> CloseReason = {
            CloseReason(CloseReason.Codes.PROTOCOL_ERROR, it.message ?: "")
        }
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
                    closeSession(this, token)
                } catch (e: Throwable) {
                    Log.e(TAG, "[$endpoint] exception thrown", e)
                    PROTOCOL_ERROR(e).also {
                        closeSession(this, token, it)
                    }
                }
            }
            webSocket("/offer") {
                val endpoint = "/offer"
                Log.d(TAG, endpoint)
                var token = ""
                try {
                    for (frame in incoming) {
                        val json = (frame as Frame.Text).readText()
                        Json.decodeFromString<SignalingInfo>(json).let { offer ->
                            token = offer.token
                            waitingReceivers[token]?.let { receiver ->
                                receiver.send(json)
                                waitingSenders[token] = this
                            }
                            Log.d(TAG, "[$endpoint] sdp sent to $token")
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    Log.e(TAG, "$endpoint closed", e)
                    closeSession(this, token)
                } catch (e: Throwable) {
                    Log.e(TAG, "$endpoint exception thrown", e)
                    PROTOCOL_ERROR(e).also {
                        closeSession(this, token, it)
                    }
                }
            }
            webSocket("/answer") {
                val endpoint = "/answer"
                Log.d(TAG, endpoint)
                var token = ""
                try {
                    for (frame in incoming) {
                        val json = (frame as Frame.Text).readText()
                        Json.decodeFromString<SignalingInfo>(json).let {
                            token = it.token
                            waitingSenders[token]?.send(json)
                            Log.d(TAG, "[$endpoint] sdp sent to $token")
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    Log.e(TAG, "$endpoint closed", e)
                    closeSession(this, token)
                } catch (e: Throwable) {
                    Log.e(TAG, "$endpoint exception thrown", e)
                    PROTOCOL_ERROR(e).also {
                        closeSession(this, token, it)
                    }
                }
            }
        }
    }

    private suspend fun closeSession(
        session: WebSocketSession,
        token: String,
        reason: CloseReason = CloseReason(CloseReason.Codes.NORMAL, "")
    ) {
        Log.d(TAG, "Closing session for token $token")
        waitingReceivers.remove(token)
        waitingSenders.remove(token)
        try {
            session.close(reason)
        } catch (e: Throwable) {
            Log.e(TAG, "Error occurred while closing session", e)
        }
    }
}

/**
 * WaitOfferRequest class.
 * @param token string representing token of the signaling session
 */
@Serializable
data class WaitOfferRequest(val token: String)

/**
 * SignalingInfo class.
 * @param token string representing token of the signaling session
 * @param sdp string representing Session Description Protocol
 * @param candidate string representing ICE Candidate used for establishing the P2P connection
 */
@Serializable
data class SignalingInfo(val token: String, val sdp: String, val candidate: String)
