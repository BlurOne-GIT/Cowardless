package code.blurone.cowardless

import net.minecraft.network.Connection
import net.minecraft.network.PacketListener
import net.minecraft.network.protocol.PacketFlow

class FakeConnection(enumprotocoldirection: PacketFlow) : Connection(enumprotocoldirection) {
    override fun setListener(packetlistener: PacketListener?) {
        /*
        Validate.notNull(packetlistener, "packetListener", *arrayOfNulls(0))
        val enumprotocoldirection = packetlistener!!.flow()
        check(enumprotocoldirection == this.receiving) { "Trying to set listener for wrong side: connection is " + this.receiving + ", but listener is " + enumprotocoldirection }
        val enumprotocol = packetlistener!!.protocol()
        check(this.packetListener!!.protocol() == enumprotocol) { "Trying to set listener for protocol " + enumprotocol.id() + ", but current " + enumprotocoldirection + " protocol is " + enumprotocol1.id() }
        this.packetListener = packetlistener
        this.disconnectListener = null*/
    }
}