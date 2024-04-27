package code.blurone.cowardless

import com.mojang.authlib.GameProfile
import net.minecraft.core.Holder
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.*
import net.minecraft.world.entity.EquipmentSlot
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftEntity
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.time.LocalTime
import java.util.*

class Cowardless : JavaPlugin(), Listener {
    private val hurtByTimestamps: MutableMap<String, LocalTime> = mutableMapOf()
    private val fakePlayerByName: MutableMap<String, ServerPlayer> = mutableMapOf()
    private val cowards: MutableMap<String, Player> = mutableMapOf()
    private val despawnTaskTimers: MutableMap<String, BukkitTask> = mutableMapOf()
    private val pvpSecondsThreshold = config.getLong("pvp_seconds_threshold", 30)
    private val despawnSecondsThreshold = config.getLong("despawn_seconds_threshold", 30)
    private val resetDespawnThreshold = config.getBoolean("reset_despawn_threshold", true)
    private val cowardeadNamespacedKey = NamespacedKey(this, "Cowardead")
    private val cowardataNamespacedKey = NamespacedKey(this, "Cowardata")

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()
        // Register plugin events
        server.pluginManager.registerEvents(this, this)

        object : BukkitRunnable() {
            override fun run() {
                //Tick NPCs
                for (npc: ServerPlayer in fakePlayerByName.values)
                {
                    npc.doTick()
                    npc.doCheckFallDamage(npc.deltaMovement.x, npc.deltaMovement.y, npc.deltaMovement.z, npc.onGround)
                    for (player: Player in Bukkit.getOnlinePlayers()) {
                        //Update position packet
                        (player as CraftPlayer).handle.connection.send(ClientboundTeleportEntityPacket(npc))
                    }
                }
            }
        }.runTaskTimer(this, 0, 1)
    }

    override fun onDisable() {
        // Plugin shutdown logic
        // Remove NPCs
        for (npc: ServerPlayer in fakePlayerByName.values)
            removePlayerPackets(npc)
    }

    @EventHandler
    fun onNpcDamage(event: EntityDamageByEntityEvent)
    {
        if (event.entity.hasMetadata("NPCGonnaBeHurt") || event.damager.type != EntityType.PLAYER || !event.entity.hasMetadata("NPCoward") || event.cause == DamageCause.ENTITY_EXPLOSION) return
        // Prevent infinite loop
        event.entity.setMetadata("NPCGonnaBeHurt", FixedMetadataValue(this, true))
        // Real attack
        (event.entity as CraftPlayer).handle.hurt(DamageSource(Holder.direct(damageTypeGetter(event.cause, event.damager)), (event.damager as CraftEntity).handle) /*DamageSource.playerAttack((event.damager as CraftPlayer).handle)*/, event.finalDamage.toFloat())
        event.entity.removeMetadata("NPCGonnaBeHurt", this)
        // Set npc on fire if the player is holding an item with fire aspect
        if ((event.damager as Player).inventory.itemInMainHand.containsEnchantment(Enchantment.FIRE_ASPECT))
            event.entity.fireTicks = 80 * (event.damager as Player).inventory.itemInMainHand.getEnchantmentLevel(Enchantment.FIRE_ASPECT)
        // Extra knockback NPC if player is holding an item with knockback
        if ((event.damager as Player).inventory.itemInMainHand.containsEnchantment(Enchantment.KNOCKBACK)) {
            /*
            Knockback formula:
            x = enchantment level
            2.6x+1 is the distance in blocks pushed when being hit
            If scaling by 0.4 equals 2 blocks, 0.2 should be 1 block, then (2.6x+1)*0.2 = 0.52x+0.2
            Then we subtract 2 blocks from the formula because they are already applied by default when running hurt()
            That gives us 0.52x-0.2, it's an approximation, but it works
            Knockback caps at 20.2 blocks which is 4.04 in scale, since it's always the same, we can just use whole value
            */
            // Ok if you're reading this, the approximation wasn't enough for someone with OCD and I had to use a lookup table with the values I got from multiplying the exact distances by 0.2...
            val km: Double = when ((event.damager as Player).inventory.itemInMainHand.getEnchantmentLevel(Enchantment.KNOCKBACK)) //if (x < 8) 0.52*x-0.1 else 3.8556418588
            {
                0 -> 0.0
                1 -> 0.52606883
                2 -> 1.0463414
                3 -> 1.566311
                4 -> 2.0868937
                5 -> 2.6069022
                6 -> 3.1269107
                7 -> 3.6476016
                else -> 3.8556418588
            }
            var d0: Double = event.damager.location.x - event.entity.location.x
            var d1: Double = event.damager.location.z - event.entity.location.z

            while (d0 * d0 + d1 * d1 < 1.0E-4) {
                d0 = (Math.random() - Math.random()) * 0.01
                d1 = (Math.random() - Math.random()) * 0.01
            }
            (event.entity as CraftPlayer).handle.animateHurt((Mth.atan2(d1, d0) * 57.2957763671875 - event.entity.location.yaw.toDouble()).toFloat())
            (event.entity as CraftPlayer).handle.knockback(km, d0, d1)
        }
        // Cancel event
        event.isCancelled = true
    }

    @EventHandler
    fun onDamage(event: EntityDamageEvent)
    {
        if (event.entityType != EntityType.PLAYER) return
        if (event.entity.hasMetadata("NPCoward"))
        {
            if (resetDespawnThreshold && (event.entity as Player).health != 0.0)
            {
                // Reset despawn timer
                despawnTaskTimers[event.entity.name]?.cancel()
                setDespawnTask(cowards[event.entity.name]!!)
            }
            return
        }

        // Set timestamp for cowards
        hurtByTimestamps[event.entity.name] = when (event.cause)
        {
            // Constant damage
            DamageCause.CONTACT,
            DamageCause.DRAGON_BREATH,
            DamageCause.DROWNING,
            DamageCause.FIRE,
            DamageCause.FREEZE,
            DamageCause.HOT_FLOOR,
            DamageCause.LAVA,
            DamageCause.SUFFOCATION -> LocalTime.now().plusSeconds(if (hurtByTimestamps[event.entity.name]?.isAfter(LocalTime.now().plusSeconds(2)) != true) 2 else pvpSecondsThreshold)

            // Pvp damage
            DamageCause.ENTITY_ATTACK,
            DamageCause.ENTITY_EXPLOSION,
            DamageCause.ENTITY_SWEEP_ATTACK,
            DamageCause.MAGIC,
            DamageCause.PROJECTILE,
            DamageCause.SONIC_BOOM,
            DamageCause.THORNS -> LocalTime.now().plusSeconds(pvpSecondsThreshold)

            else -> return
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onDead(event: PlayerDeathEvent)
    {
        // Get rid of the timestamp
        hurtByTimestamps.remove(event.entity.name)

        if (!event.entity.hasMetadata("NPCoward")) return

        fakePlayerByName.remove(event.entity.name)?.let {
            // Cancel the despawn task to prevent overlaps
            despawnTaskTimers.remove(event.entity.name)!!.let(BukkitTask::cancel)
            // Remove the NPC
            object : BukkitRunnable()
            {
                override fun run() {
                    removePlayerPackets(it)
                }
            }.runTaskLater(this, 20)
            /*
            // Drop player items
            event.drops.clear()
            event.drops.addAll(player.inventory.contents.toList())
            */
            // Set player location to the npc location when joining again
            cowards.remove(event.entity.name)!!.persistentDataContainer.set(cowardeadNamespacedKey, LocationDataType(), it.bukkitEntity.location)
        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent)
    {
        if (hurtByTimestamps.remove(event.player.name)?.isAfter(LocalTime.now()) != true) return

        // Create and spawn NPC
        fakePlayerByName[event.player.name] = spawnBody(event.player)
        // Add to cowards
        cowards[event.player.name] = event.player
        // Set despawn task
        setDespawnTask(event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent)
    {
        // Despawn NPC, stop despawn task to avoid overlaps and update player position and health
        fakePlayerByName.remove(event.player.name)?.let{
            despawnTaskTimers.remove(event.player.name)?.let(BukkitTask::cancel)
            (event.player as CraftPlayer).handle.load(it.saveWithoutId(CompoundTag(), true))
            removePlayerPackets(it)
            cowards.remove(event.player.name)
        }

        // Update player position and health if dead
        event.player.persistentDataContainer.get(cowardeadNamespacedKey, LocationDataType())?.let {
            event.player.inventory.clear()
            event.player.teleport(it)
            event.player.exp = 0f
            event.player.level = 0
            event.player.health = 0.0
            (event.player as CraftPlayer).handle.deathTime = 20
            event.player.persistentDataContainer.remove(cowardeadNamespacedKey)
            event.player.persistentDataContainer.remove(cowardataNamespacedKey)
        }

        // Update player position, health and attributes if despawned
        event.player.persistentDataContainer.get(cowardataNamespacedKey, CompoundTagDataType())?.let {
            (event.player as CraftPlayer).handle.load(it)
            event.player.persistentDataContainer.remove(cowardataNamespacedKey)
        }

        // Show NPCs to player
        val ps: ServerGamePacketListenerImpl = (event.player as CraftPlayer).handle.connection
        for (npc: ServerPlayer in fakePlayerByName.values)
        {
            ps.send(ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, npc))
            ps.send(ClientboundAddEntityPacket(npc)) //ps.send(ClientboundAddPlayerPacket(npc))
        }
    }

    private fun spawnBody(player: Player): ServerPlayer
    {
        // Create NPC
        val serverPlayer = (player as CraftPlayer).handle
        val server = serverPlayer.server
        val level = serverPlayer.serverLevel()
        val profile = GameProfile(UUID.randomUUID(), player.name)
        player.profile.properties["textures"].firstOrNull()?.let {
            profile.properties.put("textures", it)
        }
        val cookie: CommonListenerCookie = CommonListenerCookie.createInitial(profile)
        val serverNPC = ServerPlayer(server, level, profile, cookie.clientInformation)
        val bukkitNPC = serverNPC.bukkitEntity
        serverNPC.noPhysics = false
        serverNPC.entityData.assignValues(player.handle.entityData.nonDefaultValues)
        serverNPC.load(serverPlayer.saveWithoutId(CompoundTag(), true))
        serverNPC.bukkitPickUpLoot = false
        // Give NPC fake connection
        serverNPC.connection = ServerGamePacketListenerImpl(server, FakeConnection(PacketFlow.CLIENTBOUND), serverNPC, cookie)
        // Identifier
        bukkitNPC.setMetadata("NPCoward", FixedMetadataValue(this, true))

        // Add as player and entity
        level.addFreshEntity(serverNPC, CreatureSpawnEvent.SpawnReason.CUSTOM)
        level.players().add(serverNPC)

        addPlayerPackets(serverNPC)

        return serverNPC
    }

    private fun setDespawnTask(p: Player)
    {
        // Set despawn task to remove NPC and update player position and health when joining again
        despawnTaskTimers[p.name] = object : BukkitRunnable()
        {
            override fun run() {
                fakePlayerByName.remove(p.name)?.let {
                    p.persistentDataContainer.set(
                        cowardataNamespacedKey, CompoundTagDataType(),
                        it.saveWithoutId(CompoundTag(), true)
                    )
                    removePlayerPackets(it)
                    cowards.remove(p.name)
                }
            }
        }.runTaskLater(this, despawnSecondsThreshold * 20)
    }

    private fun addPlayerPackets(npc: ServerPlayer)
    {
        // Get list of visual items
        val itemList = mutableListOf(
            npc.bukkitEntity.inventory.itemInMainHand.let { com.mojang.datafixers.util.Pair(EquipmentSlot.MAINHAND, CraftItemStack.asNMSCopy(it)) },
            npc.bukkitEntity.inventory.itemInOffHand.let { com.mojang.datafixers.util.Pair(EquipmentSlot.OFFHAND, CraftItemStack.asNMSCopy(it)) }
        )
        npc.bukkitEntity.inventory.helmet?.let { itemList.add(com.mojang.datafixers.util.Pair(EquipmentSlot.CHEST, CraftItemStack.asNMSCopy(it))) }
        npc.bukkitEntity.inventory.chestplate?.let { itemList.add(com.mojang.datafixers.util.Pair(EquipmentSlot.CHEST, CraftItemStack.asNMSCopy(it))) }
        npc.bukkitEntity.inventory.leggings?.let { itemList.add(com.mojang.datafixers.util.Pair(EquipmentSlot.LEGS, CraftItemStack.asNMSCopy(it))) }
        npc.bukkitEntity.inventory.boots?.let { itemList.add(com.mojang.datafixers.util.Pair(EquipmentSlot.FEET, CraftItemStack.asNMSCopy(it))) }

        // Send packets to players to add, rotate, skin and equip NPC
        for (player: Player in Bukkit.getOnlinePlayers())
        {
            val ps: ServerGamePacketListenerImpl = (player as CraftPlayer).handle.connection
            ps.send(ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, npc))
            ps.send(ClientboundAddEntityPacket(npc)) //ps.send(ClientboundAddPlayerPacket(npc))
            ps.send(ClientboundRotateHeadPacket(npc, ((npc.yRot%360)*256/360).toInt().toByte()))
            ps.send(ClientboundMoveEntityPacket.Rot(npc.id, ((npc.yRot%360)*256/360).toInt().toByte(), ((npc.xRot%360)*256/360).toInt().toByte(), npc.onGround))
            ps.send(ClientboundSetEquipmentPacket(npc.id, itemList))
            ps.send(ClientboundSetEntityDataPacket(npc.id, npc.entityData.nonDefaultValues))
        }
    }

    private fun removePlayerPackets(npc: ServerPlayer)
    {
        // Remove NPC as player and entity
        //(npc.bukkitEntity.world as CraftWorld).handle.players().remove(npc)
        npc.serverLevel().let {
            it.players().remove(npc)
            it.removePlayerImmediately(npc, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)
        }

        // Send packets to players to remove NPC
        for (player: Player in Bukkit.getOnlinePlayers())
        {
            val ps: ServerGamePacketListenerImpl = (player as CraftPlayer).handle.connection
            ps.send(ClientboundPlayerInfoRemovePacket(listOf(npc.uuid)))
            ps.send(ClientboundRemoveEntitiesPacket(npc.id))
        }
    }

    //private val damageTypeGetter: (cause: DamageCause) ->

    // Straight out of DamageTypes.bootstrap(var0)
    // IDK if all this is needed but well, just in case I need it for all damage in the future
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
}