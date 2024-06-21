package code.blurone.cowardless

import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

class FakeSGPLI(
    private val plugin: Plugin,
    server: MinecraftServer,
    connection: Connection,
    player: ServerPlayer,
    clientData: CommonListenerCookie
) : ServerGamePacketListenerImpl(server, connection, player, clientData) {
    override fun onDisconnect(reason: Component, quitMessage: net.kyori.adventure.text.Component?) {
        val pqeHandlerList = PlayerQuitEvent.getHandlerList()
        val oldPqeListeners = pqeHandlerList.registeredListeners
        for (listener in oldPqeListeners)
            pqeHandlerList.unregister(listener)

        val silencer = SilentPlayerQuitListener()
        plugin.server.pluginManager.registerEvents(silencer, plugin)

        super.onDisconnect(reason, quitMessage)

        pqeHandlerList.unregister(silencer)

        pqeHandlerList.registerAll(oldPqeListeners.toList())
    }
}