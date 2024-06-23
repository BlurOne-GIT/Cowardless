package code.blurone.cowardless

import code.blurone.cowardless.nms.common.ServerNpc
import code.blurone.cowardless.nms.manager.getCommon
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

@Suppress("unused")
class Cowardless : JavaPlugin(), Listener {
    private val hurtByTickstamps: MutableMap<String, Long> = mutableMapOf()
    private val shallCancelVelocityEvent: MutableSet<String> = mutableSetOf()
    private val pvpTicksThreshold = config.getLong("pvp_seconds_threshold", 30) * 20L
    private val despawnTicksThreshold = config.getLong("despawn_seconds_threshold", 30) * 20L
    private val resetDespawnThreshold = config.getBoolean("reset_despawn_threshold", true)
    private val redWarning = config.getBoolean("red_warning", false)
    private val redUnwarnTasks: MutableMap<String, BukkitTask> = mutableMapOf()
    private val redUnwarnRunnables: MutableMap<String, BukkitRunnable> = mutableMapOf()
    private val common = getCommon(server.bukkitVersion)(this, despawnTicksThreshold)

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()
        // Register plugin events
        server.pluginManager.registerEvents(this, this)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onNpcDamagedByPlayer(event: EntityDamageByEntityEvent) {
        if (event.entity.name in ServerNpc.byName && event.damager is Player)
            shallCancelVelocityEvent.add(event.entity.name)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onNpcVelocityCanceler(event: PlayerVelocityEvent) {
        if (shallCancelVelocityEvent.remove(event.player.name))
            event.isCancelled = true
    }

    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        ServerNpc.byName[player.name]?.let {
            if (resetDespawnThreshold && player.health != .0)
                it.remainingTicks = despawnTicksThreshold
            return
        }

        val inTicks = when (event.cause) {
            // Constant damage
            DamageCause.CONTACT,
            DamageCause.DRAGON_BREATH,
            DamageCause.DROWNING,
            DamageCause.FIRE,
            DamageCause.FREEZE,
            DamageCause.HOT_FLOOR,
            DamageCause.LAVA,
            DamageCause.SUFFOCATION -> if ((hurtByTickstamps[player.name] ?: 0L) > player.world.gameTime + 50L) pvpTicksThreshold else 50L

            // Pvp damage
            DamageCause.ENTITY_ATTACK,
            DamageCause.ENTITY_EXPLOSION,
            DamageCause.ENTITY_SWEEP_ATTACK,
            DamageCause.MAGIC,
            DamageCause.PROJECTILE,
            DamageCause.SONIC_BOOM,
            DamageCause.THORNS -> pvpTicksThreshold

            else -> return
        }

        // Set timestamp for cowards
        hurtByTickstamps[player.name] = player.world.gameTime + inTicks

        // Add red warning
        if (!redWarning) return

        redUnwarnTasks.remove(player.name)?.cancel()
        redUnwarnRunnables.remove(player.name)?.run()
        val oldWorldBorder = player.worldBorder ?: run {
            player.worldBorder = Bukkit.createWorldBorder()
            player.worldBorder!!
        }
        val oldWarningDistance = oldWorldBorder.warningDistance
        oldWorldBorder.warningDistance = Int.MAX_VALUE
        val runnable = object : BukkitRunnable() {
            override fun run() {
                if (oldWorldBorder == player.worldBorder)
                    oldWorldBorder.warningDistance = oldWarningDistance
            }
        }
        redUnwarnRunnables[player.name] = runnable
        redUnwarnTasks[player.name] = runnable.runTaskLater(this, inTicks)
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onDead(event: PlayerDeathEvent) {
        // Get rid of the timestamp
        hurtByTickstamps.remove(event.entity.name)
        redUnwarnTasks.remove(event.entity.name)?.cancel()
        redUnwarnRunnables.remove(event.entity.name)?.run()

        // Remove the NPC if present
        ServerNpc.byName[event.entity.name]?.let {
            object : BukkitRunnable() {
                override fun run() = it.remove("${it.name}'s NPCoward has died.")
            }.runTaskLater(this, 20)
        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        if ((hurtByTickstamps.remove(event.player.name) ?: return) <= event.player.world.gameTime) return

        val player = event.player
        object : BukkitRunnable() {
            override fun run() {
                logger.info("${player.name} is a COWARD!")
                // Create and spawn NPC
                common.spawnBody(player)
            }
        }.runTask(this)
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        ServerNpc.byName[event.name]?.flagForRemoval()
    }
}