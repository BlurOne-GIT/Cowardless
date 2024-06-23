package code.blurone.cowardless.nms.common

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

abstract class Common(protected val plugin: Plugin, protected val despawnTicksThreshold: Long) {
    abstract fun spawnBody(player: Player): ServerNpc
}