package org.thinkingstudio.forgematica.network;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerStorageScopeEvents
{
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getPlayer() instanceof ServerPlayerEntity)
        {
            ServerScopeNetworking.sendServerScope((ServerPlayerEntity) event.getPlayer());
        }
    }
}
