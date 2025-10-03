package code.blurone.cowardless

import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.RegisteredListener

class SilentPlayerKickListener(private val plugin: Plugin, private val oldPkeListeners: Array<RegisteredListener>) : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onPlayerKicked(event: PlayerKickEvent) {
        plugin.logger.info("#PKE ${PlayerKickEvent.getHandlerList().registeredListeners.size}")
        if (event.cause != PlayerKickEvent.Cause.PLUGIN) return
        event.leaveMessage(Component.empty())

        //Bukkit.getGlobalRegionScheduler().run(plugin) {
            val pkeHandlerList = PlayerKickEvent.getHandlerList()
            pkeHandlerList.unregister(this)
            pkeHandlerList.registerAll(oldPkeListeners.toList())
        //}
    }
}