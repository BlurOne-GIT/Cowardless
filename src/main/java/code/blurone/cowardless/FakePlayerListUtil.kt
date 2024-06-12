@file:Suppress("DEPRECATION", "removal")

package code.blurone.cowardless

import com.google.common.collect.Lists
import com.mojang.logging.LogUtils
import com.mojang.serialization.Dynamic
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.*
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.players.PlayerList
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.npc.AbstractVillager
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.chunk.EmptyLevelChunk
import net.minecraft.world.level.dimension.DimensionType
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_20_R3.CraftServer
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityRemoveEvent
import java.util.*
import kotlin.collections.set

class FakePlayerListUtil(
    private val playerList: PlayerList,
    private val cserver: CraftServer)
{
    companion object {
        private val LOGGER = LogUtils.getLogger()
    }

    @Suppress("UNCHECKED_CAST")
    private val playersByName: MutableMap<String, ServerPlayer> =
        PlayerList::class.java.getDeclaredField("playersByName").apply { isAccessible = true }.get(playerList) as MutableMap<String, ServerPlayer>

    @Suppress("UNCHECKED_CAST")
    private val playersByUUID: MutableMap<UUID, ServerPlayer> =
        PlayerList::class.java.getDeclaredField("m").apply { isAccessible = true }.get(playerList) as MutableMap<UUID, ServerPlayer>

    private val mountSavedVehicle =
        PlayerList::class.java.getDeclaredMethod("mountSavedVehicle",
            ServerPlayer::class.java, ServerLevel::class.java, CompoundTag::class.java)
            .apply { isAccessible = true }

    fun placeNewFakePlayer(
        connection: Connection,
        player: ServerPlayer,
        clientData: CommonListenerCookie
    ) {
        player.isRealPlayer = true // Paper
        player.loginTime = System.currentTimeMillis() // Paper - Replace OfflinePlayer#getLastPlayed
        /*val gameprofile: GameProfile = player.getGameProfile()
        val usercache: GameProfileCache? = playerList.server.profileCache
        var s: String?

        if (usercache != null) {
            val optional = usercache[gameprofile.id]

            s = optional.map { obj: GameProfile -> obj.name }.orElse(gameprofile.name)
            usercache.add(gameprofile)
        } else
            s = gameprofile.name*/

        val nbttagcompound: CompoundTag? = playerList.load(player)
        var resourcekey: ResourceKey<Level>? = null // Paper

        // CraftBukkit start - Better rename detection
        /*if (nbttagcompound != null && nbttagcompound.contains("bukkit")) {
            val bukkit = nbttagcompound.getCompound("bukkit")
            s = if (bukkit.contains("lastKnownName", 8)) bukkit.getString("lastKnownName") else s
        }*/


        // CraftBukkit end

        // Paper start - move logic in Entity to here, to use bukkit supplied world UUID & reset to main world spawn if no valid world is found
        var invalidPlayerWorld = false
        if (nbttagcompound != null)
            run {
                // The main way for bukkit worlds to store the world is the world UUID despite mojang adding custom worlds
                val bWorld =
                    if (nbttagcompound.contains("WorldUUIDMost") && nbttagcompound.contains("WorldUUIDLeast"))
                        Bukkit.getServer().getWorld(UUID(
                            nbttagcompound.getLong("WorldUUIDMost"),
                            nbttagcompound.getLong("WorldUUIDLeast")
                        ))
                    else if (nbttagcompound.contains("world", Tag.TAG_STRING.toInt())) // Paper - legacy bukkit world name
                        Bukkit.getServer().getWorld(nbttagcompound.getString("world"))
                    else
                        return@run  // if neither of the bukkit data points exist, proceed to the vanilla migration section

                resourcekey = (bWorld as? CraftWorld)?.handle?.dimension() ?: run {
                    invalidPlayerWorld = true
                    Level.OVERWORLD
                }
            }
        if (resourcekey == null) // only run the vanilla logic if we haven't found a world from the bukkit data
            // Below is the vanilla way of getting the dimension, this is for migration from vanilla servers
            // Paper end
            resourcekey = if (nbttagcompound != null) {
                val dataresult = DimensionType.parseLegacy(Dynamic(
                    NbtOps.INSTANCE,
                    nbttagcompound["Dimension"]
                )) // CraftBukkit - decompile error

                Objects.requireNonNull(LOGGER)
                // Paper start - reset to main world spawn if no valid world is found
                val result = dataresult.resultOrPartial(LOGGER::error)
                invalidPlayerWorld = result.isEmpty
                result.orElse(Level.OVERWORLD)
                // Paper end
            } else
               Level.OVERWORLD // Paper - revert to vanilla default main world, this isn't an "invalid world" since no player data existed // Paper

        var worldserver1: ServerLevel = playerList.server.getLevel(resourcekey!!) ?: run {
            LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", resourcekey!!)
            invalidPlayerWorld = true // Paper - reset to main world if no world with parsed value is found
            playerList.server.overworld()
        }

        // Paper start - Entity#getEntitySpawnReason
        if (nbttagcompound == null)
            player.spawnReason =
                CreatureSpawnEvent.SpawnReason.DEFAULT // set Player SpawnReason to DEFAULT on first login
            // Paper start - reset to main world spawn if first spawn or invalid world
        if (nbttagcompound == null || invalidPlayerWorld)
            // Paper end - reset to main world spawn if first spawn or invalid world
            player.fudgeSpawnLocation(worldserver1) // Paper - Don't move existing players to world spawn

        // Paper end - Entity#getEntitySpawnReason
        player.setServerLevel(worldserver1)
        val s1: String = connection.getLoggableAddress(playerList.server.logIPs())


        // Spigot start - spawn location event
        //@Suppress("UnstableApiUsage")
        /*val ev: PlayerSpawnLocationEvent =
            PlayerInitialSpawnEvent(spawnPlayer, spawnPlayer.location) // Paper use our duplicate event
        cserver.pluginManager.callEvent(ev)*/

        val loc = player.bukkitEntity.location //ev.spawnLocation
        worldserver1 = (loc.world as CraftWorld).handle

        player.spawnIn(worldserver1)
        player.gameMode.setLevel(player.level() as ServerLevel)

        // Paper start - set raw so we aren't fully joined to the world (not added to chunk or world)
        player.setPosRaw(loc.x, loc.y, loc.z)
        player.setRot(loc.yaw, loc.pitch)


        // Paper end - set raw so we aren't fully joined to the world
        // Spigot end

        // CraftBukkit - Moved message to after join
        // PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ({}, {}, {})", new Object[]{entityplayer.getName().getString(), s1, entityplayer.getId(), entityplayer.getX(), entityplayer.getY(), entityplayer.getZ()});
        val worlddata = worldserver1.getLevelData()

        player.loadGameTypes(nbttagcompound)
        val playerconnection = //ServerGamePacketListenerImpl(this.server, connection, player, clientData)
            FakeSGPLI(this, playerList.server, connection, player, clientData)
        val gamerules = worldserver1.gameRules
        val flag = gamerules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN)
        val flag1 = gamerules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO)
        val flag2 = gamerules.getBoolean(GameRules.RULE_LIMITED_CRAFTING)


        // Spigot - view distance
        playerconnection.send(
            ClientboundLoginPacket(
                player.id,
                worlddata.isHardcore,
                playerList.server.levelKeys(),
                playerList.getMaxPlayers(),
                worldserver1.world.sendViewDistance,
                worldserver1.world.simulationDistance,
                flag1,
                !flag,
                flag2,
                player.createCommonSpawnInfo(worldserver1)
            )
        ) // Paper - replace old player chunk management
        player.bukkitEntity.sendSupportedChannels() // CraftBukkit
        playerconnection.send(ClientboundChangeDifficultyPacket(worlddata.difficulty, worlddata.isDifficultyLocked))
        playerconnection.send(ClientboundPlayerAbilitiesPacket(player.abilities))
        playerconnection.send(ClientboundSetCarriedItemPacket(player.inventory.selected))
        playerconnection.send(ClientboundUpdateRecipesPacket(playerList.server.recipeManager.getRecipes()))
        playerList.sendPlayerPermissionLevel(player)
        player.stats.markAllDirty()
        player.recipeBook.sendInitialRecipeBook(player)
        playerList.updateEntireScoreboard(worldserver1.scoreboard, player)
        playerList.server.invalidateStatus()

        /*val ichatmutablecomponent = if (player.getGameProfile().name.equals(s, ignoreCase = true)) {
            Component.translatable("multiplayer.player.joined", player.getDisplayName())
        } else {
            Component.translatable("multiplayer.player.joined.renamed", player.getDisplayName(), s)
        }

        // CraftBukkit start
        ichatmutablecomponent.withStyle(ChatFormatting.YELLOW)
        var joinMessage: Component? = ichatmutablecomponent // Paper - Adventure*/

        playerconnection.teleport(player.x, player.y, player.z, player.yRot, player.xRot)

        playerList.server.status?.let(player::sendServerStatus)


        // entityplayer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players)); // CraftBukkit - replaced with loop below
        playerList.players.add(player)
        playersByName[player.scoreboardName.lowercase()] = player // Spigot
        playersByUUID[player.uuid] = player


        // this.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(entityplayer))); // CraftBukkit - replaced with loop below

        // Paper start - Fire PlayerJoinEvent when Player is actually ready; correctly register player BEFORE PlayerJoinEvent, so the entity is valid and doesn't require tick delay hacks
        player.supressTrackerForLogin = true
        worldserver1.addNewPlayer(player)
        playerList.server.customBossEvents
            .onPlayerConnect(player) // see commented out section below worldserver.addPlayerJoin(entityplayer);
        mountSavedVehicle.invoke(playerList, player, worldserver1, nbttagcompound) //mountSavedVehicle(player, worldserver1, nbttagcompound)


        // Paper end - Fire PlayerJoinEvent when Player is actually ready
        // CraftBukkit start
        val bukkitPlayer: CraftPlayer = player.bukkitEntity


        // Ensure that player inventory is populated with its viewer
        player.containerMenu.transferTo(player.containerMenu, bukkitPlayer)

        /*val playerJoinEvent =
            PlayerJoinEvent(bukkitPlayer, PaperAdventure.asAdventure(ichatmutablecomponent)) // Paper - Adventure
        cserver.pluginManager.callEvent(playerJoinEvent)*/

        if (!player.connection.isAcceptingMessages)
            return

        /*val jm = playerJoinEvent.joinMessage()

        if (jm != null && jm != net.kyori.adventure.text.Component.empty()) { // Paper - Adventure
            joinMessage = PaperAdventure.asVanilla(jm) // Paper - Adventure
            playerList.server.playerList.broadcastSystemMessage(joinMessage!!, false) // Paper - Adventure
        }

         */


        // CraftBukkit end

        // CraftBukkit start - sendAll above replaced with this loop
        val packet =
            ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(listOf(player)) // Paper - Add Listing API for Player

        val onlinePlayers: MutableList<ServerPlayer> = Lists.newArrayListWithExpectedSize<ServerPlayer>(
            playerList.players.size - 1
        ) // Paper - Use single player info update packet on join
        for (entityplayer1 in playerList.players) {
            if (entityplayer1.bukkitEntity.canSee(bukkitPlayer))
                // Paper start - Add Listing API for Player
                if (entityplayer1.bukkitEntity.isListed(bukkitPlayer))
                    // Paper end - Add Listing API for Player
                    entityplayer1.connection.send(packet)
                    // Paper start - Add Listing API for Player
                else
                    entityplayer1.connection.send(
                        ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(
                            player,
                            false
                        )
                    )
                // Paper end - Add Listing API for Player

            if (entityplayer1 === player || !bukkitPlayer.canSee(entityplayer1.bukkitEntity)) // Paper - Use single player info update packet on join; Don't include joining player
                continue

            onlinePlayers.add(entityplayer1) // Paper - Use single player info update packet on join
        }

        // Paper start - Use single player info update packet on join
        if (onlinePlayers.isNotEmpty())
            player.connection.send(
                ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(
                    onlinePlayers,
                    player
                )
            ) // Paper - Add Listing API for Player

        // Paper end - Use single player info update packet on join
        player.sentListPacket = true
        player.supressTrackerForLogin = false // Paper - Fire PlayerJoinEvent when Player is actually ready
        (player.level() as ServerLevel).getChunkSource().chunkMap.addEntity(player) // Paper - Fire PlayerJoinEvent when Player is actually ready; track entity now


        // CraftBukkit end

        // player.getEntityData().refresh(player); // CraftBukkit - BungeeCord#2321, send complete data to self on spawn Paper - THIS IS NOT NEEDED ANYMORE
        playerList.sendLevelInfo(player, worldserver1)


        // CraftBukkit start - Only add if the player wasn't moved in the event
        if (player.level() === worldserver1 && !worldserver1.players().contains(player)) {
            worldserver1.addNewPlayer(player)
            playerList.server.customBossEvents.onPlayerConnect(player)
        }

        worldserver1 = player.serverLevel() // CraftBukkit - Update in case join event changed it

        // CraftBukkit end
        for (mobeffect in player.getActiveEffects())
            playerconnection.send(ClientboundUpdateMobEffectPacket(player.id, mobeffect))


        // Paper start - Fire PlayerJoinEvent when Player is actually ready; move vehicle into method so it can be called above - short circuit around that code
        playerList.onPlayerJoinFinish(player, worldserver1, s1)

        // Paper start - Send empty chunk, so players aren't stuck in the world loading screen with our chunk system not sending chunks when dead
        if (!player.isDeadOrDying) return

        val plains: Holder<Biome> = worldserver1.registryAccess().registryOrThrow(Registries.BIOME)
            .getHolderOrThrow(Biomes.PLAINS)
        player.connection.send(
            ClientboundLevelChunkWithLightPacket(
                EmptyLevelChunk(worldserver1, player.chunkPosition(), plains),
                worldserver1.lightEngine, null as BitSet?, null as BitSet?, true
            )
        )

    }

    fun removeFake(entityplayer: ServerPlayer) { //: String? {
        // Paper end - Fix kick event leave message not being sent
        val worldserver = entityplayer.serverLevel()

        //entityplayer.awardStat(Stats.LEAVE_GAME)

        // CraftBukkit start - Quitting must be before we do final save of data, in case plugins need to modify it
        // See SPIGOT-5799, SPIGOT-6145
        if (entityplayer.containerMenu != entityplayer.inventoryMenu)
            entityplayer.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DISCONNECT) // Paper - Inventory close reason

        //PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(entityplayer.getBukkitEntity(), net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? entityplayer.getBukkitEntity().displayName() : io.papermc.paper.adventure.PaperAdventure.asAdventure(entityplayer.getDisplayName())), entityplayer.quitReason); // Paper - Adventure & Add API for quit reason
        //this.cserver.getPluginManager().callEvent(playerQuitEvent);
        entityplayer.bukkitEntity.disconnect(null)

        if (playerList.server.isSameThread) entityplayer.doTick() // SPIGOT-924 // Paper - don't tick during emergency shutdowns (Watchdog)
        // CraftBukkit end

        // Paper start - Configurable player collision; Remove from collideRule team if needed
        playerList.collideRuleTeamName?.let {
            val scoreBoard = playerList.server.getLevel(Level.OVERWORLD)!!.scoreboard
            val team = scoreBoard.getPlayersTeam(it)
            if (entityplayer.getTeam() == team && team != null)
                scoreBoard.removePlayerFromTeam(entityplayer.scoreboardName, team)
        }
        // Paper end - Configurable player collision

        // Paper - Drop carried item when player has disconnected
        if (!entityplayer.containerMenu.getCarried().isEmpty) {
            val carried = entityplayer.containerMenu.getCarried()
            entityplayer.containerMenu.carried = net.minecraft.world.item.ItemStack.EMPTY
            entityplayer.drop(carried, false)
        }
        // Paper end - Drop carried item when player has disconnected

        this.save(entityplayer)
        if (entityplayer.isPassenger) {
            val entity = entityplayer.rootVehicle

            if (entity.hasExactlyOnePlayerPassenger()) {
                LOGGER.debug("Removing player mount")
                entityplayer.stopRiding()

                for (entity1 in entity.passengersAndSelf) {
                    // Paper start - Fix villager boat exploit
                    if (entity1 is AbstractVillager) {
                        val human = entity1.tradingPlayer
                        if (human != null) entity1.tradingPlayer = null
                    }
                    // Paper end - Fix villager boat exploit
                    entity1.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, EntityRemoveEvent.Cause.PLAYER_QUIT) // CraftBukkit - add Bukkit remove cause
                }
            }
        }

        entityplayer.unRide()
        worldserver.removePlayerImmediately(entityplayer, Entity.RemovalReason.UNLOADED_WITH_PLAYER)
        try {
            entityplayer.retireScheduler() // Paper - Folia schedulers
        } catch (_: Exception) {}
        entityplayer.advancements.stopListening()
        playerList.players.remove(entityplayer)
        playersByName.remove(entityplayer.scoreboardName.lowercase(Locale.ROOT)) // Spigot
        playerList.server.customBossEvents.onPlayerDisconnect(entityplayer)
        val uuid = entityplayer.uuid
        val entityplayer1 = playersByUUID[uuid] as ServerPlayer

        if (entityplayer1 == entityplayer)
            this.playersByUUID.remove(uuid)
            // CraftBukkit start
            // this.stats.remove(uuid);
            // this.advancements.remove(uuid);
            // CraftBukkit end

        // CraftBukkit start
        // this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(entityplayer.getUUID())));
        val packet = ClientboundPlayerInfoRemovePacket(listOf(entityplayer.uuid))

        for (entityplayer2 in playerList.players)
            if (entityplayer2.bukkitEntity.canSee(entityplayer.bukkitEntity))
                entityplayer2.connection.send(packet)
            else
                entityplayer2.bukkitEntity.onEntityRemove(entityplayer)

        // This removes the scoreboard (and player reference) for the specific player in the manager
        this.cserver.getScoreboardManager().removePlayer(entityplayer.bukkitEntity)
        // CraftBukkit end

        //return playerQuitEvent.quitMessage(); // Paper - Adventure
    }

    private fun save(player: ServerPlayer) {
        if (!player.bukkitEntity.isPersistent) return  // CraftBukkit
        player.lastSave = MinecraftServer.currentTick.toLong() // Paper - Incremental chunk and player saving
        playerList.playerIo.save(player)

        player.stats.save()

        player.advancements.save()
    }
}