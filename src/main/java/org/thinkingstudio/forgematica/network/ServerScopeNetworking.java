package org.thinkingstudio.forgematica.network;

import fi.dy.masa.litematica.Reference;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ServerScopeNetworking
{
    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new Identifier(Reference.MOD_ID, "server_scope"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
    );

    private static int packetId;
    private static boolean initialized;

    public static void init()
    {
        if (initialized)
        {
            return;
        }

        CHANNEL.registerMessage(
                packetId++,
                ServerScopePacket.class,
                ServerScopePacket::encode,
                ServerScopePacket::decode,
                ServerScopePacket::handle
        );
        initialized = true;
    }

    public static void sendServerScope(ServerPlayerEntity player)
    {
        String scope = ServerStorageScopeIdentity.getScope();

        if (scope.isEmpty() == false)
        {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ServerScopePacket(scope));
        }
    }
}
