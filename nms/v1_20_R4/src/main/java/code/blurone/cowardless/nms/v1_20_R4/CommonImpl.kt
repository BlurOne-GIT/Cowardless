package code.blurone.cowardless.nms.v1_20_R4

import code.blurone.cowardless.nms.common.Common
import code.blurone.cowardless.nms.common.ServerNpc
import code.blurone.cowardless.nms.common.SilentPlayerJoinListener
import com.mojang.authlib.GameProfile
import net.minecraft.server.network.CommonListenerCookie
import org.bukkit.craftbukkit.v1_20_R4.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import org.spigotmc.event.player.PlayerSpawnLocationEvent

class CommonImpl(plugin: Plugin, despawnTicksThreshold: Long) : Common(plugin, despawnTicksThreshold) {
    override fun spawnBody(player: Player): ServerNpc {
        // Create NPC
        val serverPlayer = (player as CraftPlayer).handle
        val server = serverPlayer.server
        val level = serverPlayer.serverLevel()
        val profile = GameProfile(player.uniqueId, player.name)
        player.profile.properties["textures"].firstOrNull()?.let {
            profile.properties.put("textures", it)
        }
        val cookie: CommonListenerCookie = CommonListenerCookie.createInitial(profile, true)
        val serverNPC = ServerNpcImpl(plugin, despawnTicksThreshold, server, level, profile, cookie.clientInformation)
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
        plugin.server.pluginManager.registerEvents(silencer, plugin)

        //fakePlayerListUtil.placeNewFakePlayer(code.blurone.cowardless.nms.v1_20_R3.FakeConnection(PacketFlow.CLIENTBOUND), serverNPC, cookie)
        val connection = FakeConnection()
        server.playerList.placeNewPlayer(connection, serverNPC, cookie)

        pjeHandlerList.unregister(silencer)

        psleHandlerList.registerAll(oldPsleListeners.toList())
        pjeHandlerList.registerAll(oldPjeListeners.toList())

        FakeSGPLI(plugin, server, connection, serverNPC, cookie)

        serverNPC.entityData.assignValues(player.handle.entityData.nonDefaultValues)
        serverNPC.spawnInvulnerableTime = 0
        serverNPC.uuid = player.uniqueId
        serverNPC.bukkitPickUpLoot = false

        return serverNPC
    }
}