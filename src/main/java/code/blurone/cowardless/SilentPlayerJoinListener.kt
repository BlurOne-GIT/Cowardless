package code.blurone.cowardless

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.RegisteredListener

class SilentPlayerJoinListener(private val plugin: Plugin, private val oldPjeListeners: Array<RegisteredListener>) : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage(null)
        plugin.logger.info("#PJE ${PlayerJoinEvent.getHandlerList().registeredListeners.size}")

        // if (oldPjeListeners != null)
        //Bukkit.getGlobalRegionScheduler().run(plugin) {
            val pjeHandlerList = PlayerJoinEvent.getHandlerList()
            pjeHandlerList.unregister(this)
            pjeHandlerList.registerAll(oldPjeListeners.toList())
        //}
    }
}