package code.blurone.cowardless

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable

class ActionBarRunnable(private val player: Player, private var seconds: Long) : BukkitRunnable() {
    var task: ScheduledTask? = null

    override fun run() {
        if (--seconds <= 0L || player.isDead) {
            player.sendActionBar(Component.translatable("actionbar_end"))
            cancel()
            return
        }

        player.sendActionBar(Component.translatable("actionbar_seconds", Component.text(seconds)))
    }

    override fun cancel() {
        if (task != null) {
            task!!.cancel()
            return
        }
        super.cancel()
    }
}