package fi.dy.masa.litematica.scheduler;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.conversion.SchematicConversionMaps;
import fi.dy.masa.litematica.selection.SelectionManager;
import fi.dy.masa.litematica.util.QuarkRotationCompat;
import fi.dy.masa.litematica.util.SchematicPrinter;
import fi.dy.masa.litematica.util.SophisticatedBackpacksCompat;
import fi.dy.masa.litematica.util.WorldUtils;
import fi.dy.masa.litematica.util.NetworkServerStorageScope;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.util.EntityUtils;
import net.minecraft.client.MinecraftClient;

public class ClientTickHandler implements IClientTickHandler
{
    @Override
    public void onClientTick(MinecraftClient mc)
    {
        if (mc.world != null && mc.player != null)
        {
            if (SchematicWorldHandler.getSchematicWorld() == null)
            {
                SchematicWorldHandler.recreateSchematicWorld(false, mc.world);
                NetworkServerStorageScope.onWorldLoadPost(mc.world);
                DataManager.load();
                SchematicConversionMaps.computeMaps();
            }

            SelectionManager sm = DataManager.getSelectionManager();

            if (sm.hasGrabbedElement())
            {
                sm.moveGrabbedElement(mc.player);
            }

            SophisticatedBackpacksCompat.tick(mc);
            QuarkRotationCompat.tick(mc);
            SchematicPrinter.tick(mc);
            WorldUtils.easyPlaceOnUseTick(mc);

            if (Configs.Generic.LAYER_MODE_DYNAMIC.getBooleanValue())
            {
                DataManager.getRenderLayerRange().setSingleBoundaryToPosition(EntityUtils.getCameraEntity());
            }

            DataManager.getSchematicPlacementManager().processQueuedChunks();
            TaskScheduler.getInstanceClient().runTasks();
        }
    }
}
