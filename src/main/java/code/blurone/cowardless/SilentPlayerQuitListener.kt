package code.blurone.cowardless

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class SilentPlayerQuitListener : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onPlayerQuit(event: PlayerQuitEvent) = event.quitMessage(null)
}