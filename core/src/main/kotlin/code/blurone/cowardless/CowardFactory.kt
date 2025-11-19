package code.blurone.cowardless

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

interface CowardFactory {
    fun createNpc(plugin: Plugin, player: Player, despawnTicksThreshold: Long, isFolia: Boolean): Coward
}