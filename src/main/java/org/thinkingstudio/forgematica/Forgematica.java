package org.thinkingstudio.forgematica;

import fi.dy.masa.litematica.Reference;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.thinkingstudio.forgematica.client.ForgematicaClient;
import org.thinkingstudio.forgematica.network.ServerScopeNetworking;
import org.thinkingstudio.forgematica.network.ServerStorageScopeEvents;

@Mod(Reference.MOD_ID)
public class Forgematica {
    public Forgematica() {
        ServerScopeNetworking.init();
        MinecraftForge.EVENT_BUS.register(ServerStorageScopeEvents.class);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ForgematicaClient::init);
    }
}
