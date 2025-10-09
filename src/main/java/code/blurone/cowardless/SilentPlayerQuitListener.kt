package code.blurone.cowardless

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.RegisteredListener

class SilentPlayerQuitListener(private val oldPqeListeners: Array<RegisteredListener>) : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onPlayerQuit(event: PlayerQuitEvent) {
        event.quitMessage(null)

        val pqeHandlerList = PlayerQuitEvent.getHandlerList()
        pqeHandlerList.unregister(this)
        pqeHandlerList.registerAll(oldPqeListeners.toList())
    }
}