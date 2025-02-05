package code.blurone.cowardless

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
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerQuitEvent.QuitReason
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

@Suppress("unused")
class CowardlessPaper : JavaPlugin(), Listener {
    private val hurtByTickstamps: MutableMap<String, Long> = mutableMapOf()
    private val shallCancelVelocityEvent: MutableSet<String> = mutableSetOf()
    private val pvpTicksThreshold = config.getLong("pvp_seconds_threshold", 30) * 20L
    private val despawnTicksThreshold = config.getLong("despawn_seconds_threshold", 30) * 20L
    private val resetDespawnThreshold = config.getBoolean("reset_despawn_threshold", true)
    private val redWarning = config.getBoolean("red_warning", false)
    private val redUnwarnTasks: MutableMap<String, BukkitTask> = mutableMapOf()
    private val redUnwarnRunnables: MutableMap<String, BukkitRunnable> = mutableMapOf()
    private val exemptedReasons: MutableSet<QuitReason> = mutableSetOf()
    private val commandBlacklist: MutableSet<String> = mutableSetOf()

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()
        // Register plugin events
        server.pluginManager.registerEvents(this, this)

        if (config.getBoolean("exempt_kicked", true))
            exemptedReasons.add(QuitReason.KICKED)
        if (config.getBoolean("exempt_timed_out", true))
            exemptedReasons.add(QuitReason.TIMED_OUT)
        if (config.getBoolean("exempt_erroneous_state", false))
            exemptedReasons.add(QuitReason.ERRONEOUS_STATE)

        commandBlacklist.addAll(config.getStringList("command_blacklist"))
    }
    
    // TODO: check if fixed
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

        ServerNpc.byName[event.entity.name]?.let {
            if (resetDespawnThreshold && player.health != 0.0)
                it.remainingTicks = despawnTicksThreshold
            return
        }

        // TODO: customizable maybe??
        val inTicks = when (event.cause) {
            // Constant damage
            DamageCause.CONTACT,
            DamageCause.SUFFOCATION,
            DamageCause.FIRE,
            DamageCause.FIRE_TICK,
            DamageCause.LAVA,
            DamageCause.DROWNING,
            DamageCause.VOID,
            DamageCause.DRAGON_BREATH,
            DamageCause.HOT_FLOOR,
            DamageCause.CAMPFIRE,
            DamageCause.CRAMMING,
            DamageCause.FREEZE
                -> if ((hurtByTickstamps[player.name] ?: 0L) > player.world.gameTime + 50L) pvpTicksThreshold else 40L

            // Pvp damage
            DamageCause.ENTITY_ATTACK,
            DamageCause.ENTITY_SWEEP_ATTACK,
            DamageCause.PROJECTILE,
            DamageCause.BLOCK_EXPLOSION,
            DamageCause.ENTITY_EXPLOSION,
            DamageCause.POISON,
            DamageCause.MAGIC,
            DamageCause.WITHER,
            DamageCause.THORNS,
            DamageCause.SONIC_BOOM
                -> pvpTicksThreshold

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
            it.remainingTicks = -1L
            object : BukkitRunnable() {
                override fun run() = it.remove("${it.name}'s NPCoward has died.", false)
            }.runTaskLater(this, 20L)
        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        if (
            (hurtByTickstamps.remove(event.player.name) ?: return) <= event.player.world.gameTime ||
            event.reason in exemptedReasons
        ) return

        val player = event.player
        object : BukkitRunnable() {
            override fun run() {
                if (player.isOnline) return
                logger.info("${player.name} is a COWARD!")
                // Create and spawn NPC
                ServerNpc.createNpc(this@CowardlessPaper, player, despawnTicksThreshold)
            }
        }.runTask(this)
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        ServerNpc.byName[event.name]?.remove(
            "${event.name}'s NPCoward has been replaced by the real player.", true
        )
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerCommandPreprocessEvent(event: PlayerCommandPreprocessEvent) {
        if (event.player.name !in hurtByTickstamps) return

        val commandName = event.message.split(' ').first().removePrefix("/")
        if (commandName in commandBlacklist)
            event.isCancelled = true
    }
}