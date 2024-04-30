package code.blurone.cowardless

import com.mojang.authlib.GameProfile
import com.mojang.logging.LogUtils
import com.mojang.serialization.Dynamic
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.status.ServerStatus
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.server.players.GameProfileCache
import net.minecraft.server.players.PlayerList
import net.minecraft.stats.Stats
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Entity.RemovalReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.dimension.DimensionType
import org.bukkit.craftbukkit.v1_20_R3.CraftServer
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld
import org.bukkit.entity.Player
import java.util.*

class FakePlayerListUtil(
    private val playerList: PlayerList,
    private val cserver: CraftServer)
{
    companion object {
        private val LOGGER = LogUtils.getLogger()
    }

    @Suppress("UNCHECKED_CAST")
    private val playersByName: java.util.Map<String, ServerPlayer> =
        //playerList.javaClass.getDeclaredField("C").apply { isAccessible = true }.get(playerList) as java.util.Map<String, ServerPlayer>
        playerList.javaClass.declaredFields.first{
            it.isAccessible = true
            val value = it.get(playerList)
            value is Map<*, *> && value.keys.first() is String && value.values.first() is ServerPlayer
        }.get(playerList)
                as java.util.Map<String, ServerPlayer>

    @Suppress("UNCHECKED_CAST")
    private val playersByUUID: java.util.Map<UUID, ServerPlayer> =
        playerList.javaClass.getDeclaredField("m").apply { isAccessible = true }.get(playerList) as java.util.Map<UUID, ServerPlayer>

    fun placeNewFakePlayer(
        networkmanager: Connection,
        entityplayer: ServerPlayer,
        commonlistenercookie: CommonListenerCookie?
    ) {
        val gameprofile = entityplayer.gameProfile
        val usercache: GameProfileCache? = playerList.server.profileCache
        var s: String?
        if (usercache != null) {
            val optional = usercache[gameprofile.id]
            s = optional.map { obj: GameProfile -> obj.name }.orElse(gameprofile.name)
            usercache.add(gameprofile)
        } else {
            s = gameprofile.name
        }

        val nbttagcompound: CompoundTag? = playerList.load(entityplayer)
        if (nbttagcompound != null && nbttagcompound.contains("bukkit")) {
            val bukkit = nbttagcompound.getCompound("bukkit")
            s = if (bukkit.contains("lastKnownName", 8)) bukkit.getString("lastKnownName") else s
        }

        val resourcekey: ResourceKey<Level>
        if (nbttagcompound != null) {
            @Suppress("DEPRECATION")
            val dataresult = DimensionType.parseLegacy(
                Dynamic(
                    NbtOps.INSTANCE,
                    nbttagcompound["Dimension"]
                )
            )
            Objects.requireNonNull(LOGGER)
            LOGGER.javaClass
            resourcekey = dataresult.resultOrPartial { s1: String? ->
                LOGGER.error(
                    s1
                )
            }.orElse(entityplayer.serverLevel().dimension())
        } else {
            resourcekey = entityplayer.serverLevel().dimension()
        }

        val worldserver: ServerLevel? = playerList.server.getLevel(resourcekey)
        var worldserver1: ServerLevel
        if (worldserver == null) {
            LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", resourcekey)
            worldserver1 = playerList.server.overworld()
        } else {
            worldserver1 = worldserver
        }

        entityplayer.setServerLevel(worldserver1)
        val s1 = networkmanager.getLoggableAddress(playerList.server.logIPs())
        val spawnPlayer: Player = entityplayer.bukkitEntity
        //val ev = PlayerSpawnLocationEvent(spawnPlayer, spawnPlayer.location)
        //this.cserver.pluginManager.callEvent(ev)
        val loc = spawnPlayer.location //ev.spawnLocation
        worldserver1 = (loc.world as CraftWorld?)!!.handle
        entityplayer.spawnIn(worldserver1)
        entityplayer.gameMode.setLevel(entityplayer.level() as ServerLevel)
        entityplayer.absMoveTo(loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
        val worlddata = worldserver1.getLevelData()
        entityplayer.loadGameTypes(nbttagcompound)
        val playerconnection =
            ServerGamePacketListenerImpl(playerList.server, networkmanager, entityplayer, commonlistenercookie)
        val gamerules = worldserver1.gameRules
        val flag = gamerules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN)
        val flag1 = gamerules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO)
        val flag2 = gamerules.getBoolean(GameRules.RULE_LIMITED_CRAFTING)
        playerconnection.send(
            ClientboundLoginPacket(
                entityplayer.id,
                worlddata.isHardcore,
                playerList.server.levelKeys(),
                playerList.getMaxPlayers(),
                worldserver1.spigotConfig.viewDistance,
                worldserver1.spigotConfig.simulationDistance,
                flag1,
                !flag,
                flag2,
                entityplayer.createCommonSpawnInfo(worldserver1)
            )
        )
        entityplayer.bukkitEntity.sendSupportedChannels()
        playerconnection.send(ClientboundChangeDifficultyPacket(worlddata.difficulty, worlddata.isDifficultyLocked))
        playerconnection.send(ClientboundPlayerAbilitiesPacket(entityplayer.abilities))
        playerconnection.send(ClientboundSetCarriedItemPacket(entityplayer.inventory.selected))
        playerconnection.send(ClientboundUpdateRecipesPacket(playerList.server.recipeManager.getRecipes()))
        playerList.sendPlayerPermissionLevel(entityplayer)
        entityplayer.stats.markAllDirty()
        entityplayer.recipeBook.sendInitialRecipeBook(entityplayer)
        playerList.updateEntireScoreboard(worldserver1.scoreboard, entityplayer)
        playerList.server.invalidateStatus()
        /*
        val ichatmutablecomponent = if (entityplayer.gameProfile.name.equals(s, ignoreCase = true)) {
            Component.translatable("multiplayer.player.joined", *arrayOf<Any?>(entityplayer.getDisplayName()))
        } else {
            Component.translatable("multiplayer.player.joined.renamed", *arrayOf(entityplayer.getDisplayName(), s))
        }

        ichatmutablecomponent.withStyle(ChatFormatting.YELLOW)
        var joinMessage = CraftChatMessage.fromComponent(ichatmutablecomponent)
        */
        playerconnection.teleport(entityplayer.x, entityplayer.y, entityplayer.z, entityplayer.yRot, entityplayer.xRot)
        val serverping: ServerStatus? = playerList.server.status
        if (serverping != null) {
            entityplayer.sendServerStatus(serverping)
        }

        playerList.players.add(entityplayer)
        playersByName.put(entityplayer.scoreboardName.lowercase(), entityplayer)
        playersByUUID.put(entityplayer.uuid, entityplayer)
        val bukkitPlayer = entityplayer.bukkitEntity
        entityplayer.containerMenu.transferTo(entityplayer.containerMenu, bukkitPlayer)
        //val playerJoinEvent = PlayerJoinEvent(bukkitPlayer, joinMessage)
        //this.cserver.pluginManager.callEvent(playerJoinEvent)
        if (entityplayer.connection.isAcceptingMessages) {
            /*
            joinMessage = playerJoinEvent.joinMessage
            if (joinMessage != null && joinMessage.isNotEmpty()) {
                var var30: Array<Component?>
                val var29 = CraftChatMessage.fromString(joinMessage).also { var30 = it }.size

                i = 0
                while (i < var29) {
                    val line = var30[i]
                    playerList.server.playerList.broadcastSystemMessage(line, false)
                    ++i
                }
            }
            */

            val packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(listOf(entityplayer))

            var i: Int = 0
            while (i < playerList.players.size) {
                val entityplayer1 = playerList.players[i]
                if (entityplayer1.bukkitEntity.canSee(bukkitPlayer)) {
                    entityplayer1.connection.send(packet)
                }

                if (bukkitPlayer.canSee(entityplayer1.bukkitEntity)) {
                    entityplayer.connection.send(
                        ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(
                            listOf(
                                entityplayer1
                            )
                        )
                    )
                }
                ++i
            }

            entityplayer.sentListPacket = true
            entityplayer.entityData.refresh(entityplayer)
            playerList.sendLevelInfo(entityplayer, worldserver1)
            if (entityplayer.level() === worldserver1 && !worldserver1.players().contains(entityplayer)) {
                worldserver1.addNewPlayer(entityplayer)
                playerList.server.customBossEvents.onPlayerConnect(entityplayer)
            }

            worldserver1 = entityplayer.serverLevel()
            val iterator: Iterator<*> = entityplayer.getActiveEffects().iterator()

            while (iterator.hasNext()) {
                val mobeffect = iterator.next() as MobEffectInstance
                playerconnection.send(ClientboundUpdateMobEffectPacket(entityplayer.id, mobeffect))
            }

            if (nbttagcompound != null && nbttagcompound.contains("RootVehicle", 10)) {
                val nbttagcompound1 = nbttagcompound.getCompound("RootVehicle")
                val entity = EntityType.loadEntityRecursive(
                    nbttagcompound1.getCompound("Entity"), worldserver1
                ) { entity1x: Entity? ->
                    if (!worldserver1.addWithUUID(
                            entity1x
                        )
                    ) null else entity1x
                }
                if (entity != null) {
                    val uuid = if (nbttagcompound1.hasUUID("Attach")) {
                        nbttagcompound1.getUUID("Attach")
                    } else {
                        null
                    }

                    var iterator1: Iterator<*>
                    var entity1: Entity
                    if (entity.uuid == uuid) {
                        entityplayer.startRiding(entity, true)
                    } else {
                        iterator1 = entity.indirectPassengers.iterator()

                        while (iterator1.hasNext()) {
                            entity1 = iterator1.next() as Entity
                            if (entity1.uuid == uuid) {
                                entityplayer.startRiding(entity1, true)
                                break
                            }
                        }
                    }

                    if (!entityplayer.isPassenger) {
                        LOGGER.warn("Couldn't reattach entity to player")
                        entity.discard()
                        iterator1 = entity.indirectPassengers.iterator()

                        while (iterator1.hasNext()) {
                            entity1 = iterator1.next() as Entity
                            entity1.discard()
                        }
                    }
                }
            }

            entityplayer.initInventoryMenu()
            LOGGER.info(
                "{}[{}] logged in with entity id {} at ([{}]{}, {}, {})",
                *arrayOf<Any>(
                    entityplayer.name.string,
                    s1,
                    entityplayer.id,
                    worldserver1.K.levelName,
                    entityplayer.x,
                    entityplayer.y,
                    entityplayer.z
                )
            )
        }
    }

    fun removeFake(entityplayer: ServerPlayer) { //: String? {
        val worldserver = entityplayer.serverLevel()
        entityplayer.awardStat(Stats.LEAVE_GAME)
        if (entityplayer.containerMenu !== entityplayer.inventoryMenu) {
            entityplayer.closeContainer()
        }

        /*
        val playerQuitEvent = PlayerQuitEvent(
            entityplayer.bukkitEntity,
            if (entityplayer.kickLeaveMessage != null) entityplayer.kickLeaveMessage else "§e" + entityplayer.scoreboardName + " left the game"
        )
        //cserver.pluginManager.callEvent(playerQuitEvent)
     */
        entityplayer.bukkitEntity.disconnect(null) //entityplayer.bukkitEntity.disconnect(playerQuitEvent.quitMessage)
        entityplayer.doTick()
        save(entityplayer) //playerList.save(entityplayer)
        if (entityplayer.isPassenger) {
            val entity = entityplayer.rootVehicle
            if (entity.hasExactlyOnePlayerPassenger()) {
                LOGGER.debug("Removing player mount")
                entityplayer.stopRiding()
                entity.passengersAndSelf.forEach { entity1: Entity ->
                    entity1.setRemoved(RemovalReason.UNLOADED_WITH_PLAYER)
                }
            }
        }

        entityplayer.unRide()
        worldserver.removePlayerImmediately(entityplayer, RemovalReason.UNLOADED_WITH_PLAYER)
        entityplayer.advancements.stopListening()
        playerList.players.remove(entityplayer)
        playersByName.remove(entityplayer.scoreboardName.lowercase())
        playerList.server.customBossEvents.onPlayerDisconnect(entityplayer)
        val uuid = entityplayer.uuid
        val entityplayer1 = playersByUUID[uuid] as ServerPlayer
        if (entityplayer1 === entityplayer) {
            playersByUUID.remove(uuid)
        }

        val packet = ClientboundPlayerInfoRemovePacket(listOf(entityplayer.uuid))

        for (i in playerList.players.indices) {
            val entityplayer2 = playerList.players[i]
            if (entityplayer2.bukkitEntity.canSee(entityplayer.bukkitEntity)) {
                entityplayer2.connection.send(packet)
            } else {
                entityplayer2.bukkitEntity.onEntityRemove(entityplayer)
            }
        }

        cserver.getScoreboardManager()!!.removePlayer(entityplayer.bukkitEntity)
        //return playerQuitEvent.quitMessage
    }

    private fun save(entityplayer: ServerPlayer) {
        if (entityplayer.bukkitEntity.isPersistent) {
            playerList.playerIo.save(entityplayer)
            val serverstatisticmanager = entityplayer.stats
            serverstatisticmanager?.save()

            val advancementdataplayer = entityplayer.advancements
            advancementdataplayer?.save()
        }
    }
}