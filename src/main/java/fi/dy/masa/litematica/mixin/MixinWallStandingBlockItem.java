package fi.dy.masa.litematica.mixin;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.util.PlacementHandler;
import fi.dy.masa.litematica.util.PlacementHandler.UseContext;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.WallStandingBlockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WallStandingBlockItem.class, priority = 980)
public abstract class MixinWallStandingBlockItem
{
    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void applyPlacementProtocol(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir)
    {
        BlockState state = cir.getReturnValue();

        if (state == null)
        {
            return;
        }

        // Wall/standing items override BlockItem#getPlacementState, so they need the
        // same printer/Easy Place protocol decode hook as normal block items.
        if ((Configs.Generic.EASY_PLACE_MODE.getBooleanValue() &&
             Configs.Generic.EASY_PLACE_SP_HANDLING.getBooleanValue()) ||
            PlacementHandler.hasPlacementProtocolData(ctx))
        {
            UseContext context = UseContext.from(ctx, ctx.getHand());
            BlockState modifiedState = PlacementHandler.applyPlacementProtocolToPlacementState(state, context);

            if (modifiedState != null)
            {
                cir.setReturnValue(modifiedState);
            }
        }
    }
}
