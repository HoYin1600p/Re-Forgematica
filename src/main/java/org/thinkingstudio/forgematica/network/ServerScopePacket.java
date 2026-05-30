package org.thinkingstudio.forgematica.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerScopePacket
{
    private final String scope;

    public ServerScopePacket(String scope)
    {
        this.scope = scope;
    }

    public static void encode(ServerScopePacket packet, PacketByteBuf buf)
    {
        buf.writeString(packet.scope);
    }

    public static ServerScopePacket decode(PacketByteBuf buf)
    {
        return new ServerScopePacket(buf.readString(128));
    }

    public static void handle(ServerScopePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientServerScopeHandler.handle(packet.scope)));
        context.setPacketHandled(true);
    }
}
