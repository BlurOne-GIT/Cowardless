package code.blurone.cowardless

import com.mojang.authlib.GameProfile
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.EquipmentSlot
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_20_R3.CraftServer
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack
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
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import com.mojang.datafixers.util.Pair
import java.util.*

@Suppress("unused")
class CowardlessPaper : JavaPlugin(), Listener {
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
    private val exemptedReasons: MutableSet<QuitReason> = mutableSetOf()
    private lateinit var fakePlayerListUtil : FakePlayerListUtil

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()
        // Register plugin events
        server.pluginManager.registerEvents(this, this)

        fakePlayerListUtil = FakePlayerListUtil((server as CraftServer).handle, server as CraftServer)

        if (config.getBoolean("exempt_kicked", true))
            exemptedReasons.add(QuitReason.KICKED)
        if (config.getBoolean("exempt_timed_out", true))
            exemptedReasons.add(QuitReason.TIMED_OUT)
        if (config.getBoolean("exempt_erroneous_state", false))
            exemptedReasons.add(QuitReason.ERRONEOUS_STATE)
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
            despawnTaskTimers.remove(event.entity.name)?.cancel()
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
        if (
            (hurtByTickstamps.remove(event.player.name) ?: return) <= event.player.world.gameTime ||
            event.reason in exemptedReasons
        ) return

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
        player.handle.entityData.nonDefaultValues?.let { serverNPC.entityData.assignValues(it) }
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

    // Straight out of DamageTypes.bootstrap(var0)
    // IDK if all this is needed but well, just in case I need it for all damage in the future
    /*
    private fun damageTypeGetter(cause: DamageCause, entity: Entity? = null, pos: Location? = null): DamageType = when (cause)
    {
        DamageCause.BLOCK_EXPLOSION -> DamageType("explosion", DamageScaling.ALWAYS, 0.1f)
        DamageCause.CONTACT ->
            if (pos?.block?.type == Material.SWEET_BERRY_BUSH)
                DamageType("sweetBerryBush", 0.1f, DamageEffects.POKING)
            else if (pos?.subtract(0.0, 1.0, 0.0)?.block?.type == Material.POINTED_DRIPSTONE)
                DamageType("stalagmite", 0.0f)
            else
                DamageType("cactus", 0.1f)
        DamageCause.CRAMMING -> DamageType("cramming", 0.0f)
        DamageCause.CUSTOM -> DamageType("generic", 0.0f)
        DamageCause.DRAGON_BREATH -> DamageType("dragonBreath", 0.0f)
        DamageCause.DROWNING -> DamageType("drown", 0.0f, DamageEffects.DROWNING)
        DamageCause.DRYOUT,
        DamageCause.MELTING -> DamageType("dryout", 0.1f)
        DamageCause.ENTITY_ATTACK,
        DamageCause.ENTITY_SWEEP_ATTACK -> when (entity?.type)
        {
            EntityType.PLAYER -> DamageType("player", 0.1f)
            EntityType.BEE -> DamageType("sting", 0.1f)
            EntityType.GOAT -> DamageType("thrown", 0.1f)
            else -> DamageType("mob", 0.1f)
        }
        DamageCause.ENTITY_EXPLOSION -> DamageType("explosion.player", DamageScaling.ALWAYS, 0.1f)
        DamageCause.FALL -> DamageType("fall", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.0f, DamageEffects.HURT, DeathMessageType.FALL_VARIANTS)
        DamageCause.FALLING_BLOCK ->
            when ((entity as FallingBlock).blockData.material) {
                Material.ANVIL,
                Material.CHIPPED_ANVIL,
                Material.DAMAGED_ANVIL -> DamageType("anvil", 0.1f)
                Material.POINTED_DRIPSTONE -> DamageType("fallingStalactite", 0.1f)
                else -> DamageType("fallingBlock", 0.1f)
            }
        DamageCause.FIRE -> DamageType("inFire", 0.1f, DamageEffects.BURNING)
        DamageCause.FIRE_TICK -> DamageType("onFire", 0.0f, DamageEffects.BURNING)
        DamageCause.FLY_INTO_WALL -> DamageType("flyIntoWall", 0.0f)
        DamageCause.FREEZE -> DamageType("freeze", 0.0f, DamageEffects.FREEZING)
        DamageCause.HOT_FLOOR -> DamageType("hotFloor", 0.1f, DamageEffects.BURNING)
        DamageCause.KILL -> DamageType("genericKill", 0.0f)
        DamageCause.LAVA -> DamageType("lava", 0.1f, DamageEffects.BURNING)
        DamageCause.LIGHTNING -> DamageType("lightningBolt", 0.1f)
        DamageCause.MAGIC,
        DamageCause.POISON -> if (entity == null) DamageType("magic", 0.0f) else DamageType("indirectMagic", 0.0f)
        DamageCause.PROJECTILE -> when (entity?.type)
        {
            EntityType.ARROW,
            EntityType.SPECTRAL_ARROW -> DamageType("arrow", 0.1f)
            EntityType.TRIDENT -> DamageType("trident", 0.1f)
            EntityType.FIREWORK -> DamageType("fireworks", 0.1f)
            EntityType.SMALL_FIREBALL -> DamageType("onFire", 0.1f, DamageEffects.BURNING)
            EntityType.FIREBALL -> DamageType("fireball", 0.1f, DamageEffects.BURNING)
            EntityType.WITHER_SKULL -> DamageType("witherSkull", 0.1f)
            else -> if (entity?.type == EntityType.PLAYER) DamageType("player", 0.1f) else DamageType("mob", 0.1f)
        }
        DamageCause.SONIC_BOOM -> DamageType("sonic_boom", DamageScaling.ALWAYS, 0.0f)
        DamageCause.STARVATION -> DamageType("starve", 0.0f)
        DamageCause.SUFFOCATION -> DamageType("inWall", 0.0f)
        DamageCause.SUICIDE -> DamageType("genericKill", 0.0f)
        DamageCause.THORNS -> DamageType("thorns", 0.1f, DamageEffects.THORNS)
        DamageCause.VOID -> DamageType("outOfWorld", 0.0f)
        DamageCause.WITHER -> DamageType("wither", 0.0f)
        DamageCause.WORLD_BORDER -> DamageType("outsideBorder", 0.0f)
    }
    */
}