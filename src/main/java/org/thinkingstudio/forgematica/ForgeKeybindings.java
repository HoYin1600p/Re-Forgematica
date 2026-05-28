package org.thinkingstudio.forgematica;

import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ForgeKeybindings
{
    private static final String CATEGORY = "key.categories.forgematica";

    private static final KeyBinding OPEN_MENU = new KeyBinding(
            "key.forgematica.open_menu",
            KeyConflictContext.IN_GAME,
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_L,
            CATEGORY);

    private static boolean initialized;

    public static void init()
    {
        if (initialized == false)
        {
            ClientRegistry.registerKeyBinding(OPEN_MENU);
            MinecraftForge.EVENT_BUS.register(ForgeKeybindings.class);
            initialized = true;
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        while (OPEN_MENU.wasPressed())
        {
            if (mc.player != null && mc.world != null && mc.currentScreen == null)
            {
                GuiBase.openGui(new GuiMainMenu());
            }
        }
    }
}
