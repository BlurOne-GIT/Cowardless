package code.blurone.cowardless

import com.mojang.authlib.GameProfile
import net.minecraft.network.DisconnectionDetails
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

class ServerNpc(
    private val plugin: Plugin,
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
    }

    init {
        byName[name] = this
    }

    fun remove(logMessage: String, async: Boolean) {
        byName.remove(name)
        plugin.logger.info(logMessage)
        val pqeHandlerList = PlayerQuitEvent.getHandlerList()
        val oldPqeListeners = pqeHandlerList.registeredListeners
        for (listener in oldPqeListeners)
            pqeHandlerList.unregister(listener)

        val silencer = SilentPlayerQuitListener()
        plugin.server.pluginManager.registerEvents(silencer, plugin)

        //server.playerList.remove(this)
        val disconnectionDetails = DisconnectionDetails(Component.empty())
        val cause = PlayerKickEvent.Cause.PLUGIN
        if (async)
            connection.disconnectAsync(disconnectionDetails, cause)
        else
            connection.disconnect(disconnectionDetails, cause)

        pqeHandlerList.unregister(silencer)

        pqeHandlerList.registerAll(oldPqeListeners.toList())
    }

    override fun tick() {
        // TODO: check if this manual tick was fixed
        connection.handleMovePlayer(ServerboundMovePlayerPacket.StatusOnly(onGround(), true))
        doCheckFallDamage(deltaMovement.x, deltaMovement.y, deltaMovement.z, onGround())
        super.tick()
        doTick()
        if (remainingTicks-- == 0L)
            remove("$name's NPCoward has expired.", false)
    }
}