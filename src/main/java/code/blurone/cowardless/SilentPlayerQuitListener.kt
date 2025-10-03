package code.blurone.cowardless

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.RegisteredListener

class SilentPlayerQuitListener(private val plugin: Plugin, private val oldPqeListeners: Array<RegisteredListener>) : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.logger.info("#PQE ${PlayerQuitEvent.getHandlerList().registeredListeners.size}")
        event.quitMessage(null)

        //Bukkit.getGlobalRegionScheduler().run(plugin) {
            val pqeHandlerList = PlayerQuitEvent.getHandlerList()
            pqeHandlerList.unregister(this)
            pqeHandlerList.registerAll(oldPqeListeners.toList())
        //}
    }
}