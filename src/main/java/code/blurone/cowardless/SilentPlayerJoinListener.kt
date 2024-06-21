package code.blurone.cowardless

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class SilentPlayerJoinListener : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onPlayerJoin(event: PlayerJoinEvent) = event.joinMessage(null)
}