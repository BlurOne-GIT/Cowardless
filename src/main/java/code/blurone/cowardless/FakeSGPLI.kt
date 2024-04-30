package code.blurone.cowardless

import com.mojang.logging.LogUtils
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.util.FutureChain
import org.slf4j.Logger

class FakeSGPLI(private val fakePlayerListUtil: FakePlayerListUtil, minecraftserver: MinecraftServer?,
                networkmanager: Connection?, entityplayer: ServerPlayer?, commonlistenercookie: CommonListenerCookie?
) : ServerGamePacketListenerImpl(minecraftserver, networkmanager, entityplayer, commonlistenercookie) {

    companion object {
        val LOGGER: Logger = LogUtils.getLogger()
    }

    private val chatMessageChain = ServerGamePacketListenerImpl::class.java.getDeclaredField("O")
        .apply { isAccessible = true }.get(this) as FutureChain

    override fun onDisconnect(ichatbasecomponent: Component?) {
        if (!processedDisconnect) {
            processedDisconnect = true
            LOGGER.info(
                "{} lost connection: {}",
                player.name.string, ichatbasecomponent!!.string
            )
            removeFakePlayerFromWorld()
        }
    }

    private fun removeFakePlayerFromWorld()
    {
        chatMessageChain.close()
        player.disconnect()
        fakePlayerListUtil.removeFake(this.player)

        player.textFilter.leave()
    }
}