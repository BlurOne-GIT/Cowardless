package code.blurone.cowardless.v1_21_8

import code.blurone.cowardless.Coward
import code.blurone.cowardless.CowardFactory
import code.blurone.cowardless.SilentPlayerJoinListener
import code.blurone.cowardless.SilentPlayerKickListener
import code.blurone.cowardless.SilentPlayerQuitListener
import com.mojang.authlib.GameProfile
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket
import net.minecraft.network.protocol.game.GameProtocols
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.spigotmc.event.player.PlayerSpawnLocationEvent

class ServerNpc(
    private val plugin: Plugin,
    override var remainingTicks: Long,
    server: MinecraftServer,
    world: ServerLevel,
    profile: GameProfile,
    clientOptions: ClientInformation,
) : ServerPlayer(server, world, profile, clientOptions), Coward {
    override val name: String
        get() = gameProfile.name

    companion object : CowardFactory {
        override fun createNpc(plugin: Plugin, player: Player, despawnTicksThreshold: Long, isFolia: Boolean): Coward {
            // Create NPC
            val serverPlayer = (player as CraftPlayer).handle
            val level = serverPlayer.level()
            val server = level.server
            val profile = player.profile
            val cookie: CommonListenerCookie = CommonListenerCookie.createInitial(profile, true)
            // Instantiate ServerNpc
            val serverNPC = ServerNpc(plugin, despawnTicksThreshold, server, level, profile, cookie.clientInformation)

            // Hijack events
            val psleHandlerList = PlayerSpawnLocationEvent.getHandlerList()
            val oldPsleListeners = psleHandlerList.registeredListeners
            for (listener in oldPsleListeners) psleHandlerList.unregister(listener)

            val pjeHandlerList = PlayerJoinEvent.getHandlerList()
            val oldPjeListeners = pjeHandlerList.registeredListeners
            for (listener in oldPjeListeners) pjeHandlerList.unregister(listener)

            // This events auto unhijacks itself
            val pjeSilencer = SilentPlayerJoinListener(oldPjeListeners, plugin.config.getBoolean("chat_message", true))
            plugin.server.pluginManager.registerEvents(pjeSilencer, plugin)

            val connection = FakeConnection(serverNPC)
            val scpli = ServerConfigurationPacketListenerImpl(server, connection, cookie)
            scpli.returnToWorld()
            // Place NPC (this ends up in PlayerList.placeNewPlayer and assigns ServerPlayer.connection to an instance of SGPLI)
            scpli.handleConfigurationFinished(ServerboundFinishConfigurationPacket.INSTANCE)

            psleHandlerList.registerAll(oldPsleListeners.toList())

            if (isFolia) {
                // On folia, calling handleConfigurationFinished doesn't call PlayerList.placeNewPlayer right away
                // It waits for the main thread to execute it, so to hijack the SGPLI we need to schedule a task
                serverNPC.bukkitEntity.scheduler.run(plugin, {
                    val foliaSGPLI = FoliaSGPLI(server, connection, serverNPC, cookie)
                    connection.setupInboundProtocol(
                        GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(server.registryAccess()), foliaSGPLI),
                        foliaSGPLI
                    )
                }, null)
            }

            serverPlayer.entityData.nonDefaultValues?.let(serverNPC.entityData::assignValues)
            serverNPC.setClientLoaded(true)
            serverNPC.bukkitPickUpLoot = false

            return serverNPC
        }
    }

    init {
        Coward.byName[name] = this
    }

    override fun remove(logMessage: String, async: Boolean) {
        Coward.byName.remove(name)
        plugin.logger.info(logMessage)

        val pqeHandlerList = PlayerQuitEvent.getHandlerList()
        val oldPqeListeners = pqeHandlerList.registeredListeners

        for (listener in oldPqeListeners) pqeHandlerList.unregister(listener)

        val pkeHandleList = PlayerKickEvent.getHandlerList()
        val oldPkeListeners = pkeHandleList.registeredListeners
        for (listener in oldPkeListeners) pkeHandleList.unregister(listener)

        val pqeSilencer = SilentPlayerQuitListener(oldPqeListeners)
        plugin.server.pluginManager.registerEvents(pqeSilencer, plugin)

        val pkeSilencer = SilentPlayerKickListener(oldPkeListeners)
        plugin.server.pluginManager.registerEvents(pkeSilencer, plugin)

        val reason = Component.literal("Cowardless")
        val cause = PlayerKickEvent.Cause.PLUGIN
        if (async)
            connection.disconnectAsync(reason, cause)
        else
            connection.disconnect(reason, cause)
    }

    override fun tick() {
        connection.handleMovePlayer(ServerboundMovePlayerPacket.StatusOnly(onGround(), true))
        super.tick()
        doTick()
        if (remainingTicks-- == 0L)
            remove("$name's NPCoward has expired.", false)
    }
}