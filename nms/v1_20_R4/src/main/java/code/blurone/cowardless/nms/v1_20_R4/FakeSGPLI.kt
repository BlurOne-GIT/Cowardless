package code.blurone.cowardless.nms.v1_20_R4

import code.blurone.cowardless.nms.common.SilentPlayerQuitListener
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
    minecraftserver: MinecraftServer?,
    networkmanager: Connection?,
    entityplayer: ServerPlayer?,
    commonlistenercookie: CommonListenerCookie?
) : ServerGamePacketListenerImpl(minecraftserver, networkmanager, entityplayer, commonlistenercookie) {
    override fun onDisconnect(ichatbasecomponent: Component?) {
        val pqeHandlerList = PlayerQuitEvent.getHandlerList()
        val oldPqeListeners = pqeHandlerList.registeredListeners
        for (listener in oldPqeListeners)
            pqeHandlerList.unregister(listener)

        val silencer = SilentPlayerQuitListener()
        plugin.server.pluginManager.registerEvents(silencer, plugin)

        super.onDisconnect(ichatbasecomponent)

        pqeHandlerList.unregister(silencer)

        pqeHandlerList.registerAll(oldPqeListeners.toList())
    }
}