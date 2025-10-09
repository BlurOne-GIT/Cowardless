package code.blurone.cowardless

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.translation.Argument
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.RegisteredListener

class SilentPlayerJoinListener(private val oldPjeListeners: Array<RegisteredListener>, private val withMessage: Boolean) : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage(if (withMessage)
            Component.translatable("chat_coward",
                Argument.component("p", Component.text(event.player.name))
            ).colorIfAbsent(NamedTextColor.YELLOW)

        else null)

        val pjeHandlerList = PlayerJoinEvent.getHandlerList()
        pjeHandlerList.unregister(this)
        pjeHandlerList.registerAll(oldPjeListeners.toList())
    }
}