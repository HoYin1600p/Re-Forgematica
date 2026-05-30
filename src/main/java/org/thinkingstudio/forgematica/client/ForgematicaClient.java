package org.thinkingstudio.forgematica.client;

import fi.dy.masa.litematica.InitHandler;
import fi.dy.masa.litematica.gui.GuiConfigs;
import fi.dy.masa.malilib.event.InitializationHandler;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import org.thinkingstudio.forgematica.ForgeKeybindings;
import org.thinkingstudio.mafglib.util.ForgeUtils;

public class ForgematicaClient
{
    public static void init()
    {
        ModContainer modContainer = ModLoadingContext.get().getActiveContainer();

        ForgeUtils.getInstance().getClientModIgnoredServerOnly(modContainer);
        ForgeKeybindings.init();
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());

        ForgeUtils.getInstance().registerModConfigScreen(modContainer, (screen) -> {
            GuiConfigs gui = new GuiConfigs();
            gui.setParent(screen);
            return gui;
        });
    }
}
