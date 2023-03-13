package tokyo.isseikuzumaki.webrtcdemo

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class WebRtcServer() {
    companion object {
        private const val PORT = 65432
    }
    private val serverScope = CoroutineScope(Dispatchers.IO)
    private val signalingServer = SignalingServer()
    private val protocols = listOf<Server>(
        signalingServer
    )

    // TODO support secure protocol
    init {
        serverScope.launch {
//            val keyStoreFile = File("build/keystore.jks")
//            val keyStore = buildKeyStore {
//                certificate("sampleAlias") {
//                    password = "foobar"
//                    domains = listOf("127.0.0.1", "0.0.0.0", "localhost")
//                }
//            }
//            keyStore.saveToFile(keyStoreFile, "123456")
            val environment = applicationEngineEnvironment {
                connector {
                    port = PORT
                }
//                sslConnector(
//                    keyStore = keyStore,
//                    keyAlias = "sampleAlias",
//                    keyStorePassword = {"123456".toCharArray()},
//                    privateKeyPassword = { "foobar".toCharArray() }
//                ) {
//                        port = 8843
//                        keyStorePath = keyStoreFile
//                }
                module {
                    protocols.forEach { it.initialize(this) }
                    routing {
                        protocols.forEach { it.setRouting(this) }
                    }
                }
            }
            embeddedServer(CIO, environment).start(true)
        }
    }
}
