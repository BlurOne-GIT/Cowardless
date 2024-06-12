package code.blurone.cowardless

import com.mojang.authlib.GameProfile
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_20_R3.CraftServer
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.*

@Suppress("unused")
class Cowardless : JavaPlugin(), Listener {
    private val hurtByTickstamps: MutableMap<String, Long> = mutableMapOf()
    private val fakePlayerByName: MutableMap<String, ServerPlayer> = mutableMapOf()
    private val despawnTaskTimers: MutableMap<String, BukkitTask> = mutableMapOf()
    private val shallCancelVelocityEvent: MutableSet<String> = mutableSetOf()
    private val shallDisconnectOnUUID: MutableSet<String> = mutableSetOf()
    private val pvpTicksThreshold = config.getLong("pvp_seconds_threshold", 30) * 20L
    private val despawnTicksThreshold = config.getLong("despawn_seconds_threshold", 30) * 20L
    private val resetDespawnThreshold = config.getBoolean("reset_despawn_threshold", true)
    private val shallLog = config.getBoolean("logger", true)
    private val redWarning = config.getBoolean("red_warning", false)
    private val redUnwarnTasks: MutableMap<String, BukkitTask> = mutableMapOf()
    private val redUnwarnRunnables: MutableMap<String, BukkitRunnable> = mutableMapOf()
    private lateinit var fakePlayerListUtil : FakePlayerListUtil

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()
        // Register plugin events
        server.pluginManager.registerEvents(this, this)

        fakePlayerListUtil = FakePlayerListUtil((server as CraftServer).handle, server as CraftServer)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onNpcDamagedByPlayer(event: EntityDamageByEntityEvent)
    {
        if (event.entity.hasMetadata("NPCoward") && event.damager is Player)
            shallCancelVelocityEvent.add(event.entity.name)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onNpcVelocityCanceler(event: PlayerVelocityEvent)
    {
        if (shallCancelVelocityEvent.remove(event.player.name))
            event.isCancelled = true
    }

    @EventHandler
    fun onDamage(event: EntityDamageEvent)
    {
        val player = event.entity as? Player ?: return
        if (player.hasMetadata("NPCoward"))
        {
            if (resetDespawnThreshold && player.health != 0.0)
            {
                // Reset despawn timer
                despawnTaskTimers[player.name]?.cancel()
                setDespawnTask(player.name)
            }
            return
        }

        val inTicks = when (event.cause)
        {
            // Constant damage
            DamageCause.CONTACT,
            DamageCause.DRAGON_BREATH,
            DamageCause.DROWNING,
            DamageCause.FIRE,
            DamageCause.FREEZE,
            DamageCause.HOT_FLOOR,
            DamageCause.LAVA,
            DamageCause.SUFFOCATION -> if ((hurtByTickstamps[player.name] ?: 0L) > player.world.gameTime + 40L) pvpTicksThreshold else 40L

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
    fun onDead(event: PlayerDeathEvent)
    {
        // Get rid of the timestamp
        hurtByTickstamps.remove(event.entity.name)
        redUnwarnTasks.remove(event.entity.name)?.cancel()
        redUnwarnRunnables.remove(event.entity.name)?.run()

        if (!event.entity.hasMetadata("NPCoward")) return

        fakePlayerByName.remove(event.entity.name)?.let {
            // Cancel the despawn task to prevent overlaps
            despawnTaskTimers.remove(event.entity.name)!!.cancel()
            // Remove the NPC
            object : BukkitRunnable()
            {
                override fun run() {
                    if (shallLog) logger.info("${it.name}'s NPCoward has died.")
                    fakePlayerListUtil.removeFake(it)
                }
            }.runTaskLater(this, 20)
        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent)
    {
        if ((hurtByTickstamps.remove(event.player.name) ?: return) <= event.player.world.gameTime) return

        val player = event.player
        object : BukkitRunnable(){
            override fun run() {
                if (shallLog) logger.info("${player.name} is a COWARD!")
                // Create and spawn NPC
                fakePlayerByName[player.name] = spawnBody(player)
                // Set despawn task
                setDespawnTask(player.name)
            }
        }.runTask(this)
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent)
    {
        if (fakePlayerByName.containsKey(event.name))
            shallDisconnectOnUUID.add(event.name)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent)
    {
        event.player.removeMetadata("NPCoward", this)
        event.player.removeMetadata("NPCGonnaBeHurt", this)
    }

    private fun spawnBody(player: Player): ServerPlayer
    {
        // Create NPC
        val serverPlayer = (player as CraftPlayer).handle
        val server = serverPlayer.server
        val level = serverPlayer.serverLevel()
        val profile = GameProfile(player.uniqueId, player.name)
        player.profile.properties["textures"].firstOrNull()?.let {
            profile.properties.put("textures", it)
        }
        val cookie: CommonListenerCookie = CommonListenerCookie.createInitial(profile)
        val playerName = player.name
        val serverNPC = object : ServerPlayer(server, level, profile, cookie.clientInformation) {
            override fun tick() {
                connection.handleMovePlayer(ServerboundMovePlayerPacket.StatusOnly(onGround()))
                doCheckFallDamage(deltaMovement.x, deltaMovement.y, deltaMovement.z, onGround())
                super.tick()
                doTick()
            }

            override fun getUUID(): UUID {
                val realUUID = super.getUUID()

                if (!shallDisconnectOnUUID.remove(playerName))
                    return realUUID

                if (shallLog) logger.info("${playerName}'s NPCoward has been replaced by the real player.")

                despawnTaskTimers.remove(playerName)?.cancel()
                fakePlayerByName.remove(playerName)?.let(fakePlayerListUtil::removeFake)
                return UUID(0L, if (realUUID.leastSignificantBits != 0L) 0L else 1L) // Don't return same UUID
            }
        }
        // Identifier
        serverNPC.bukkitEntity.setMetadata("NPCoward", FixedMetadataValue(this, true))
        // Place NPC
        fakePlayerListUtil.placeNewFakePlayer(FakeConnection(PacketFlow.CLIENTBOUND), serverNPC, cookie)
        serverNPC.entityData.assignValues(player.handle.entityData.nonDefaultValues)
        serverNPC.spawnInvulnerableTime = 0
        serverNPC.uuid = player.uniqueId
        serverNPC.bukkitPickUpLoot = false

        return serverNPC
    }

    private fun setDespawnTask(playerName: String)
    {
        // Set despawn task to remove NPC and update player position and health when joining again
        despawnTaskTimers[playerName] = object : BukkitRunnable()
        {
            override fun run() {
                fakePlayerByName.remove(playerName)?.let {
                    if (shallLog) logger.info("${it.name}'s NPCoward has expired.")
                    fakePlayerListUtil.removeFake(it)
                }
            }
        }.runTaskLater(this, despawnTicksThreshold)
    }
}