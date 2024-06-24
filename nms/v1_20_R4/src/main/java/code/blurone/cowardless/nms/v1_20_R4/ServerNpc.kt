package code.blurone.cowardless.nms.v1_20_R4

import code.blurone.cowardless.nms.common.NonPlayableCoward
import code.blurone.cowardless.nms.common.SilentPlayerQuitListener
import com.mojang.authlib.GameProfile
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.*

class ServerNpc(
    private val plugin: Plugin,
    override var remainingTicks: Long,
    minecraftserver: MinecraftServer,
    worldserver: ServerLevel,
    gameprofile: GameProfile,
    clientinformation: ClientInformation
) : ServerPlayer(minecraftserver, worldserver, gameprofile, clientinformation), NonPlayableCoward {
    override val name: String
        get() = gameProfile.name

    private var isFlaggedForRemoval = false

    init {
        NonPlayableCoward.byName[name] = this
    }

    override fun flagForRemoval() {
        isFlaggedForRemoval = true
    }

    override fun remove(logMessage: String) {
        NonPlayableCoward.byName.remove(name)
        plugin.logger.info(logMessage)
        val pqeHandlerList = PlayerQuitEvent.getHandlerList()
        val oldPqeListeners = pqeHandlerList.registeredListeners
        for (listener in oldPqeListeners)
            pqeHandlerList.unregister(listener)

        val silencer = SilentPlayerQuitListener()
        plugin.server.pluginManager.registerEvents(silencer, plugin)

        server.playerList.remove(this)

        pqeHandlerList.unregister(silencer)

        pqeHandlerList.registerAll(oldPqeListeners.toList())
    }

    override fun tick() {
        connection.handleMovePlayer(ServerboundMovePlayerPacket.StatusOnly(onGround()))
        doCheckFallDamage(deltaMovement.x, deltaMovement.y, deltaMovement.z, onGround())
        super.tick()
        doTick()
        if (remainingTicks-- == 0L)
            remove("$name's NPCoward has expired.")
    }

    override fun getUUID(): UUID {
        val realUUID = super.getUUID()

        if (!isFlaggedForRemoval)
            return realUUID

        remove("$name's NPCoward has been replaced by the real player.")
        return UUID(0L, if (realUUID.leastSignificantBits != 0L) 0L else 1L) // Don't return same UUID
    }
}