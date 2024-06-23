package code.blurone.cowardless.nms.v1_20_R4

import io.netty.channel.ChannelHandler
import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.network.Connection
import net.minecraft.network.PacketListener
import net.minecraft.network.protocol.PacketFlow

@ChannelHandler.Sharable
class FakeConnection : Connection(PacketFlow.CLIENTBOUND) {
    override fun setListenerForServerboundHandshake(packetlistener: PacketListener?) {
        EmbeddedChannel(this)
    }
}