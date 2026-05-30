package fi.dy.masa.litematica.mixin;

import fi.dy.masa.litematica.util.NetworkServerStorageScope;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MixinScreen
{
    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"))
    private void onSendMessage(String message, boolean addToHistory, CallbackInfo ci)
    {
        NetworkServerStorageScope.onClientChatMessage(message);
    }
}
