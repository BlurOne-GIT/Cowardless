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
import java.lang.reflect.Field

class FakeSGPLI(private val fakePlayerListUtil: FakePlayerListUtil, minecraftserver: MinecraftServer,
                networkmanager: Connection, entityplayer: ServerPlayer, commonlistenercookie: CommonListenerCookie
) : ServerGamePacketListenerImpl(minecraftserver, networkmanager, entityplayer, commonlistenercookie) {

    companion object {
        val LOGGER: Logger = LogUtils.getLogger()
        val CHAT_MESSAGE_CHAIN_FIELD: Field = ServerGamePacketListenerImpl::class.java.getDeclaredField("O")
            .apply { isAccessible = true }
    }

    private val chatMessageChain = CHAT_MESSAGE_CHAIN_FIELD.get(this) as FutureChain

    override fun onDisconnect(reason: Component, quitMessage: net.kyori.adventure.text.Component?) {
        // Paper end - Fix kick event leave message not being sent
        // CraftBukkit start - Rarely it would send a disconnect line twice
        if (this.processedDisconnect)
            return
        else
            this.processedDisconnect = true

        // CraftBukkit end
        LOGGER.info("{} lost connection: {}", player.name.string, reason.string)
        removeFakePlayerFromWorld() // Paper - Fix kick event leave message not being sent
    }

    private fun removeFakePlayerFromWorld() {
        // Paper end - Fix kick event leave message not being sent
        this.chatMessageChain.close()

        // CraftBukkit start - Replace vanilla quit message handling with our own.
        /*
        this.server.invalidateStatus();
        this.server.getPlayerList().broadcastSystemMessage(IChatBaseComponent.translatable("multiplayer.player.left", this.player.getDisplayName()).withStyle(EnumChatFormat.YELLOW), false);
        */
        player.disconnect()

        // Paper start - Adventure
        fakePlayerListUtil.removeFake(player)// Paper - pass in quitMessage to fix kick message not being used

        // CraftBukkit end
        player.textFilter.leave()
    }
}