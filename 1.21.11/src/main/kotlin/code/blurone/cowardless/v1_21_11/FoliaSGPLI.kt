package code.blurone.cowardless.v1_21_11

import net.minecraft.network.Connection
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl

class FoliaSGPLI(
    server: MinecraftServer,
    connection: Connection,
    player: ServerPlayer,
    clientData: CommonListenerCookie
) : ServerGamePacketListenerImpl(server, connection, player, clientData) {
    // This function is for some reason called in Folia and not in Paper, which makes Folia tick the player twice
    override fun tick() {
        return
    }
}