package tokyo.isseikuzumaki.webrtcdemo

import io.ktor.server.application.*
import io.ktor.server.routing.*

interface Server {
    fun initialize(application: Application)
    fun setRouting(routing: Routing)
}
