package code.blurone.cowardless

import com.mojang.authlib.GameProfile
import net.minecraft.network.DisconnectionDetails
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.GameProtocols
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.plugin.Plugin
import org.spigotmc.event.player.PlayerSpawnLocationEvent
import java.util.logging.Logger

class ServerNpc(
    private val logger: Logger,
    var remainingTicks: Long,
    server: MinecraftServer,
    world: ServerLevel,
    profile: GameProfile,
    clientOptions: ClientInformation
) : ServerPlayer(server, world, profile, clientOptions) {
    val name: String
        get() = gameProfile.name

    companion object {
        val byName: MutableMap<String, ServerNpc> = mutableMapOf()

        fun createNpc(plugin: Plugin, player: Player, despawnTicksThreshold: Long): ServerNpc {
            // Create NPC
            val serverPlayer = (player as CraftPlayer).handle
            val server = serverPlayer.server
            val level = serverPlayer.serverLevel()
            val profile = GameProfile(player.uniqueId, player.name)
            player.profile.properties["textures"].firstOrNull()?.let {
                profile.properties.put("textures", it)
            }
            val cookie: CommonListenerCookie = CommonListenerCookie.createInitial(profile, true)
            val serverNPC = ServerNpc(plugin.logger, despawnTicksThreshold, server, level, profile, cookie.clientInformation)
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

            val connection = FakeConnection()
            server.playerList.placeNewPlayer(connection, serverNPC, cookie)

            pjeHandlerList.unregister(silencer)

            psleHandlerList.registerAll(oldPsleListeners.toList())
            pjeHandlerList.registerAll(oldPjeListeners.toList())

            connection.setupInboundProtocol(
                GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(server.registryAccess())),
                FakeSGPLI(plugin, server, connection, serverNPC, cookie)
            )

            serverPlayer.entityData.nonDefaultValues?.let(serverNPC.entityData::assignValues)
            serverNPC.invulnerableTime = 0
            serverNPC.isInvulnerable = false
            serverNPC.setClientLoaded(true)
            serverNPC.uuid = player.uniqueId
            serverNPC.bukkitPickUpLoot = false

            return serverNPC
        }
    }

    init {
        byName[name] = this
    }

    fun remove(logMessage: String, async: Boolean) {
        byName.remove(name)
        logger.info(logMessage)

        val disconnectionDetails = DisconnectionDetails(Component.literal("Cowardless"))
        val cause = PlayerKickEvent.Cause.PLUGIN
        if (async)
            connection.disconnectAsync(disconnectionDetails, cause)
        else
            connection.disconnect(disconnectionDetails, cause)
    }

    override fun tick() {
        connection.handleMovePlayer(ServerboundMovePlayerPacket.StatusOnly(onGround(), true))
        doCheckFallDamage(deltaMovement.x, deltaMovement.y, deltaMovement.z, onGround())
        super.tick()
        doTick()
        if (remainingTicks-- == 0L)
            remove("$name's NPCoward has expired.", false)
    }
}