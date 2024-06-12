package code.blurone.cowardless

import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.network.Connection
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.PacketListener
import net.minecraft.network.protocol.PacketFlow

class FakeConnection(enumprotocoldirection: PacketFlow) : Connection(enumprotocoldirection) {
    override fun setListener(packetlistener: PacketListener?) {
        val embeddedChannel = EmbeddedChannel(this)
        embeddedChannel.attr(ATTRIBUTE_SERVERBOUND_PROTOCOL).set(ConnectionProtocol.PLAY.codec(PacketFlow.SERVERBOUND))
    }
}