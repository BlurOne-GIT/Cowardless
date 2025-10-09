package code.blurone.cowardless

import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.plugin.RegisteredListener

class SilentPlayerKickListener(private val oldPkeListeners: Array<RegisteredListener>) : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onPlayerKicked(event: PlayerKickEvent) {
        if (event.cause != PlayerKickEvent.Cause.PLUGIN) return
        event.leaveMessage(Component.empty())

        val pkeHandlerList = PlayerKickEvent.getHandlerList()
        pkeHandlerList.unregister(this)
        pkeHandlerList.registerAll(oldPkeListeners.toList())
    }
}