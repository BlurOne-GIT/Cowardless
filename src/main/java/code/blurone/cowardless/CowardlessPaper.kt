package code.blurone.cowardless

import com.mojang.authlib.GameProfile
import net.minecraft.network.DisconnectionDetails
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.GameProtocols
import net.minecraft.server.network.CommonListenerCookie
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.*
import org.bukkit.event.player.PlayerQuitEvent.QuitReason
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.spigotmc.event.player.PlayerSpawnLocationEvent

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

        // TODO: update list
        val inTicks = when (event.cause) {
            // Constant damage
            DamageCause.CONTACT,
            DamageCause.DRAGON_BREATH,
            DamageCause.DROWNING,
            DamageCause.FIRE,
            DamageCause.FREEZE,
            DamageCause.HOT_FLOOR,
            DamageCause.LAVA,
            DamageCause.SUFFOCATION -> if ((hurtByTickstamps[player.name] ?: 0L) > player.world.gameTime + 50L) pvpTicksThreshold else 40L

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
                spawnBody(player)
            }
        }.runTask(this)
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        ServerNpc.byName[event.name]?.remove(
            "${event.name}'s NPCoward has been replaced by the real player.", true
        )
    }

    // TODO: attempt to move this to ServerNpc either as a constructor or a factory function
    private fun spawnBody(player: Player): ServerNpc {
        // Create NPC
        val serverPlayer = (player as CraftPlayer).handle
        val server = serverPlayer.server
        val level = serverPlayer.serverLevel()
        val profile = GameProfile(player.uniqueId, player.name)
        player.profile.properties["textures"].firstOrNull()?.let {
            profile.properties.put("textures", it)
        }
        val cookie: CommonListenerCookie = CommonListenerCookie.createInitial(profile, true)
        val serverNPC = ServerNpc(this, despawnTicksThreshold, server, level, profile, cookie.clientInformation)
        // Place NPC
        val psleHandlerList = PlayerSpawnLocationEvent.getHandlerList()
        val oldPsleListeners = psleHandlerList.registeredListeners
        for (listener in oldPsleListeners)
            psleHandlerList.unregister(listener)

        val pjeHandlerList = PlayerJoinEvent.getHandlerList()
        val oldPjeListeners = pjeHandlerList.registeredListeners
        for (listener in oldPjeListeners)
            pjeHandlerList.unregister(listener)

        val silencer = SilentPlayerJoinListener()
        this.server.pluginManager.registerEvents(silencer, this)

        val connection = FakeConnection()
        server.playerList.placeNewPlayer(connection, serverNPC, cookie)

        pjeHandlerList.unregister(silencer)

        psleHandlerList.registerAll(oldPsleListeners.toList())
        pjeHandlerList.registerAll(oldPjeListeners.toList())

        connection.setupInboundProtocol(
            GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(server.registryAccess())),
            FakeSGPLI(this, server, connection, serverNPC, cookie)
        )

        player.handle.entityData.nonDefaultValues?.let(serverNPC.entityData::assignValues)
        serverNPC.invulnerableTime = 0
        serverNPC.uuid = player.uniqueId
        serverNPC.bukkitPickUpLoot = false

        return serverNPC
    }
}