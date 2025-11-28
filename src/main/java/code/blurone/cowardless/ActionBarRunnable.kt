package code.blurone.cowardless

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.translation.Argument
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable

class ActionBarRunnable(private val player: Player, private var seconds: Long) : BukkitRunnable() {

    override fun run() {
        val audience = Cowardless.adventure!!.player(player)

        if (seconds <= 0L || player.isDead) {
            audience.sendActionBar(Component.translatable("actionbar_end"))
            cancel()
            return
        }

        audience.sendActionBar(Component.translatable("actionbar_seconds", Argument.numeric("s", seconds--)))
    }
}