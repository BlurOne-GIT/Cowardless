package code.blurone.cowardless

import io.netty.channel.ChannelHandler
import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import java.net.InetSocketAddress

@ChannelHandler.Sharable
class FakeConnection : Connection(PacketFlow.SERVERBOUND /*PacketFlow.CLIENTBOUND*/) {
    init {
        EmbeddedChannel(this)
    }

    override fun handleDisconnection() {
        val oldAddress = this.address
        this.address = InetSocketAddress(0)
        super.handleDisconnection()
        this.address = oldAddress
    }
}