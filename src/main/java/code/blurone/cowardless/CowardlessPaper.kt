package code.blurone.cowardless

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
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
import org.bukkit.event.player.PlayerJoinEvent
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
    private val combatTicksThreshold = config.getLong("combat_seconds_threshold", 30) * 20L
    private val despawnTicksThreshold = config.getLong("despawn_seconds_threshold", 30) * 20L
    private val resetDespawnThreshold = config.getBoolean("reset_despawn_threshold", true)
    private val redWarning = config.getBoolean("red_warning", false)
    private val pvpOnly = config.getBoolean("pvp_only", false)
    private val redUnwarnBukkitTasks: MutableMap<String, BukkitTask> = mutableMapOf()
    private val redUnwarnScheduledTasks: MutableMap<String, ScheduledTask> = mutableMapOf()
    private val redUnwarnRunnables: MutableMap<String, BukkitRunnable> = mutableMapOf()
    private val exemptedReasons: MutableSet<QuitReason> = mutableSetOf()
    private val commandBlacklist: MutableSet<String> = mutableSetOf()
    private val isFolia: Boolean by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

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

    @EventHandler(priority = EventPriority.MONITOR)
    fun onNpcDamagedByPlayer(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return
        val damagerIsPlayer = event.damager is Player
        // Fix ServerNpc no knockback
        if (player.name in ServerNpc.byName && damagerIsPlayer)
        {
            shallCancelVelocityEvent.add(player.name)
            return
        }

        if (pvpOnly && damagerIsPlayer && player.name !in hurtByTickstamps)
            damageHandler(player, event.cause)
    }

    // Fix ServerNpc no knockback
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onNpcVelocityCanceler(event: PlayerVelocityEvent) {
        if (shallCancelVelocityEvent.remove(event.player.name))
            event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return

        // Reset timer for NPC
        ServerNpc.byName[event.entity.name]?.let {
            if (resetDespawnThreshold && player.health != 0.0)
                it.remainingTicks = despawnTicksThreshold
            return
        }

        if (!pvpOnly || player.name in hurtByTickstamps)
            damageHandler(player, event.cause)
    }

    fun damageHandler(player: Player, cause: DamageCause) {
        val inTicks = when (cause) {
            // Constant damage
            DamageCause.CONTACT,
            DamageCause.SUFFOCATION,
            DamageCause.FIRE,
            DamageCause.FIRE_TICK,
            DamageCause.LAVA,
            DamageCause.DROWNING,
            DamageCause.VOID,
            DamageCause.HOT_FLOOR,
            //DamageCause.CAMPFIRE,
            DamageCause.CRAMMING,
            DamageCause.FREEZE
                -> if ((hurtByTickstamps[player.name] ?: 0L) > player.world.gameTime + 50L) combatTicksThreshold else 40L

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
                -> combatTicksThreshold

            else -> return
        }

        setCombatTicks(player, inTicks)
    }

    fun setCombatTicks(player: Player, ticks: Long) {
        // Set timestamp for cowards
        hurtByTickstamps[player.name] = player.world.gameTime + ticks

        // Add red warning
        if (!redWarning) return

        if (isFolia)
            redUnwarnScheduledTasks.remove(player.name)?.cancel()
        else
            redUnwarnBukkitTasks.remove(player.name)?.cancel()

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

        if (isFolia)
            player.scheduler.runDelayed(this, { runnable.run() }, runnable, ticks)?.let {
                redUnwarnScheduledTasks[player.name] = it
            }
        else
            redUnwarnBukkitTasks[player.name] = runnable.runTaskLater(this, ticks)
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onDead(event: PlayerDeathEvent) {
        // Get rid of the timestamp
        hurtByTickstamps.remove(event.entity.name)
        if (isFolia)
            redUnwarnScheduledTasks.remove(event.entity.name)
        else
            redUnwarnBukkitTasks.remove(event.entity.name)?.cancel()
        redUnwarnRunnables.remove(event.entity.name)?.run()

        // Remove the NPC if present
        ServerNpc.byName[event.entity.name]?.let {
            it.remainingTicks = -1L
            val runnable = object : BukkitRunnable() {
                override fun run() = it.remove("${it.name}'s NPCoward has died.", event.isAsynchronous)
            }

            if (isFolia)
                event.entity.scheduler.execute(this, runnable, null, 20L)
            else
                runnable.runTaskLater(this, 20L)

        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        logger.info("${event.player.name} leaving ${hurtByTickstamps[event.player.name]}")
        if (
            (hurtByTickstamps.remove(event.player.name) ?: return) <= event.player.world.gameTime ||
            event.reason in exemptedReasons
        ) return

        val player = event.player

        val runnable = object : BukkitRunnable() {
            override fun run() {
                if (player.isOnline) return
                logger.info("${player.name} is a COWARD!")
                // Create and spawn NPC
                ServerNpc.createNpc(this@CowardlessPaper, player, despawnTicksThreshold, isFolia)
            }
        }

        if (isFolia)
            server.globalRegionScheduler.execute(this, runnable)
        else
            runnable.runTask(this)
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        ServerNpc.byName[event.name]?.let {
            logger.info("Setting hurtByTickstamp ${it.remainingTicks}")
            hurtByTickstamps[event.name] = combatTicksThreshold
            it.remove(
                "${event.name}'s NPCoward has been replaced by the real player.", true
            )
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        hurtByTickstamps[event.player.name]?.let { hurtByTickstamp ->
            event.player.scheduler.run(this, {setCombatTicks(event.player, hurtByTickstamp)}, null)
            //logger.info("${event.player.name} $it")
            //setCombatTicks(event.player, it)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerCommandPreprocessEvent(event: PlayerCommandPreprocessEvent) {
        if (event.player.name !in hurtByTickstamps) return

        val commandName = event.message.split(' ').first().removePrefix("/")
        if (commandName in commandBlacklist)
            event.isCancelled = true
    }
}