package fi.dy.masa.litematica.util;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class SchematicPrinter
{
    private static int ticksUntilNextPlacement;

    public static void tick(MinecraftClient mc)
    {
        if (mc.player == null || mc.world == null)
        {
            ticksUntilNextPlacement = 0;
            return;
        }

        if (isActive() == false)
        {
            ticksUntilNextPlacement = 0;
            return;
        }

        if (mc.currentScreen != null || mc.interactionManager == null)
        {
            return;
        }

        if (SophisticatedBackpacksCompat.hasPendingTransfer())
        {
            return;
        }

        if (ticksUntilNextPlacement > 0)
        {
            --ticksUntilNextPlacement;
            return;
        }

        if (tryPlaceOne(mc))
        {
            ticksUntilNextPlacement = Math.max(1, Configs.Generic.PRINTER_INTERVAL.getIntegerValue());
        }
        else
        {
            ticksUntilNextPlacement = 1;
        }
    }

    private static boolean isActive()
    {
        return Configs.Generic.PRINTER_MODE.getBooleanValue() ||
               Hotkeys.PRINTER_ACTIVATION.getKeybind().isKeybindHeld();
    }

    private static boolean tryPlaceOne(MinecraftClient mc)
    {
        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();

        if (schematicWorld == null)
        {
            return false;
        }

        for (BlockPos pos : getReachablePositions(mc))
        {
            if (tryPlaceAt(mc, schematicWorld, pos))
            {
                return true;
            }
        }

        return false;
    }

    private static List<BlockPos> getReachablePositions(MinecraftClient mc)
    {
        double range = Configs.Generic.PRINTER_RANGE.getDoubleValue();
        double rangeSquared = range * range;
        int maxReach = (int) Math.ceil(range);
        BlockPos playerPos = mc.player.getBlockPos();
        Vec3d eyePos = mc.player.getEyePos();
        List<BlockPos> positions = new ArrayList<>();

        for (int y = -maxReach; y <= maxReach; ++y)
        {
            for (int x = -maxReach; x <= maxReach; ++x)
            {
                for (int z = -maxReach; z <= maxReach; ++z)
                {
                    BlockPos pos = playerPos.add(x, y, z);

                    if (DataManager.getRenderLayerRange().isPositionWithinRange(pos) == false)
                    {
                        continue;
                    }

                    Vec3d center = Vec3d.ofCenter(pos);

                    if (eyePos.squaredDistanceTo(center) > rangeSquared)
                    {
                        continue;
                    }

                    if (mc.player.getPos().squaredDistanceTo(center) <= 1.0D || eyePos.squaredDistanceTo(center) <= 1.0D)
                    {
                        continue;
                    }

                    positions.add(pos.toImmutable());
                }
            }
        }

        positions.sort(Comparator.comparingDouble(pos -> mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(pos))));
        return positions;
    }

    private static boolean tryPlaceAt(MinecraftClient mc, WorldSchematic schematicWorld, BlockPos pos)
    {
        BlockState stateSchematic = schematicWorld.getBlockState(pos);
        BlockState stateClient = mc.world.getBlockState(pos);

        if (stateSchematic.isAir() || stateSchematic == stateClient)
        {
            return false;
        }

        ItemStack stack = MaterialCache.getInstance().getRequiredBuildItemForState(stateSchematic, schematicWorld, pos);

        if (stack.isEmpty())
        {
            return false;
        }

        if (canReplaceTarget(mc, pos) == false && canPlaceSecondSlab(stateSchematic, stateClient) == false)
        {
            return false;
        }

        InventoryUtils.schematicWorldPickBlock(stack, pos, schematicWorld, mc);

        if (SophisticatedBackpacksCompat.hasPendingTransfer())
        {
            return true;
        }

        Hand hand = EntityUtils.getUsedHandForItem(mc.player, stack);

        if (hand == null)
        {
            return false;
        }

        EasyPlaceProtocol protocol = PlacementHandler.getEffectiveProtocolVersion();
        Direction side = WorldUtils.applyPlacementFacing(stateSchematic, Direction.UP, stateClient);
        Vec3d hitPos = getHitPos(pos, stateSchematic, protocol);
        BlockHitResult hitResult = new BlockHitResult(hitPos, side, pos, false);
        LookRotation rotation = getPlacementRotation(mc, stateSchematic, stack, hitResult);

        if (rotation != null)
        {
            sendRotation(mc, rotation);
        }

        WorldUtils.interactBlockWithOptionalQuarkRotationLock(mc, hand, hitResult, stateSchematic, protocol);

        if (canPlaceSecondSlab(stateSchematic, stateClient))
        {
            BlockState updatedClientState = mc.world.getBlockState(pos);

            if (updatedClientState.getBlock() instanceof SlabBlock &&
                updatedClientState.get(SlabBlock.TYPE) != SlabType.DOUBLE)
            {
                Direction secondSide = WorldUtils.applyPlacementFacing(stateSchematic, Direction.UP, updatedClientState);
                BlockHitResult secondHitResult = new BlockHitResult(hitPos, secondSide, pos, false);

                if (rotation != null)
                {
                    sendRotation(mc, rotation);
                }

                WorldUtils.interactBlockWithOptionalQuarkRotationLock(mc, hand, secondHitResult, stateSchematic, protocol);
            }
        }

        return true;
    }

    private static boolean canReplaceTarget(MinecraftClient mc, BlockPos pos)
    {
        BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        ItemPlacementContext ctx = new ItemPlacementContext(new ItemUsageContext(mc.player, Hand.MAIN_HAND, hitResult));

        return mc.world.getBlockState(pos).canReplace(ctx);
    }

    private static boolean canPlaceSecondSlab(BlockState stateSchematic, BlockState stateClient)
    {
        return stateSchematic.getBlock() instanceof SlabBlock &&
               stateSchematic.get(SlabBlock.TYPE) == SlabType.DOUBLE &&
               stateClient.getBlock() instanceof SlabBlock &&
               stateClient.get(SlabBlock.TYPE) != SlabType.DOUBLE &&
               stateSchematic.getBlock() == stateClient.getBlock();
    }

    private static Vec3d getHitPos(BlockPos pos, BlockState stateSchematic, EasyPlaceProtocol protocol)
    {
        Vec3d hitPos = Vec3d.ofCenter(pos);

        if (protocol == EasyPlaceProtocol.V3)
        {
            return WorldUtils.applyPlacementProtocolV3(pos, stateSchematic, hitPos);
        }
        else if (protocol == EasyPlaceProtocol.V2)
        {
            return WorldUtils.applyCarpetProtocolHitVec(pos, stateSchematic, hitPos);
        }
        else if (protocol == EasyPlaceProtocol.SLAB_ONLY)
        {
            return WorldUtils.applyBlockSlabProtocol(pos, stateSchematic, hitPos);
        }

        return hitPos;
    }

    private static void sendRotation(MinecraftClient mc, LookRotation rotation)
    {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rotation.yaw, rotation.pitch, mc.player.isOnGround()));
    }

    private static LookRotation getPlacementRotation(MinecraftClient mc, BlockState state, ItemStack stack, BlockHitResult hitResult)
    {
        LookRotation simulatedRotation = getSimulatedPlacementRotation(mc, state, stack, hitResult);

        if (simulatedRotation != null)
        {
            return simulatedRotation;
        }

        Direction lookDirection = getPlacementLookDirection(state);

        if (lookDirection == null)
        {
            return null;
        }

        return LookRotation.fromDirection(lookDirection);
    }

    private static LookRotation getSimulatedPlacementRotation(MinecraftClient mc, BlockState targetState, ItemStack stack,
                                                             BlockHitResult hitResult)
    {
        if ((stack.getItem() instanceof BlockItem) == false)
        {
            return null;
        }

        BlockItem blockItem = (BlockItem) stack.getItem();
        LookRotation bestRotation = null;
        int bestScore = 0;
        float originalYaw = mc.player.getYaw();
        float originalPitch = mc.player.getPitch();

        try
        {
            for (LookRotation rotation : LookRotation.CANDIDATES)
            {
                mc.player.setYaw(rotation.yaw);
                mc.player.setPitch(rotation.pitch);

                ItemPlacementContext ctx = new ItemPlacementContext(new ItemUsageContext(mc.player, Hand.MAIN_HAND, hitResult));
                BlockState resultState = blockItem.getBlock().getPlacementState(ctx);
                int score = getPlacementMatchScore(targetState, resultState);

                if (score > bestScore)
                {
                    bestScore = score;
                    bestRotation = rotation;
                }
            }
        }
        finally
        {
            mc.player.setYaw(originalYaw);
            mc.player.setPitch(originalPitch);
        }

        return bestScore > 0 ? bestRotation : null;
    }

    private static int getPlacementMatchScore(BlockState targetState, BlockState resultState)
    {
        if (resultState == null || resultState.getBlock() != targetState.getBlock())
        {
            return 0;
        }

        int score = 1;

        for (Property<?> property : PlacementMatchProperties.PROPERTIES)
        {
            if (targetState.contains(property) && resultState.contains(property))
            {
                if (propertyValuesEqual(targetState, resultState, property))
                {
                    ++score;
                }
                else
                {
                    --score;
                }
            }
        }

        return score;
    }

    private static <T extends Comparable<T>> boolean propertyValuesEqual(BlockState targetState, BlockState resultState, Property<T> property)
    {
        return targetState.get(property).equals(resultState.get(property));
    }

    private static Direction getPlacementLookDirection(BlockState state)
    {
        if (state.contains(Properties.HORIZONTAL_FACING))
        {
            Direction direction = state.get(Properties.HORIZONTAL_FACING);

            if (state.getBlock() instanceof StairsBlock)
            {
                return direction;
            }

            return direction.getOpposite();
        }

        if (state.contains(Properties.FACING))
        {
            return state.get(Properties.FACING).getOpposite();
        }

        if (state.contains(Properties.AXIS))
        {
            return getDirectionForAxis(state.get(Properties.AXIS));
        }

        if (state.contains(Properties.BLOCK_HALF))
        {
            return state.get(Properties.BLOCK_HALF) == BlockHalf.TOP ? Direction.DOWN : Direction.UP;
        }

        return null;
    }

    private static Direction getDirectionForAxis(Direction.Axis axis)
    {
        switch (axis)
        {
            case X:
                return Direction.EAST;
            case Y:
                return Direction.UP;
            case Z:
                return Direction.SOUTH;
        }

        return Direction.SOUTH;
    }

    private static class LookRotation
    {
        private static final List<LookRotation> CANDIDATES = List.of(
                fromDirection(Direction.SOUTH),
                fromDirection(Direction.WEST),
                fromDirection(Direction.NORTH),
                fromDirection(Direction.EAST),
                fromDirection(Direction.UP),
                fromDirection(Direction.DOWN)
        );

        private final float yaw;
        private final float pitch;

        private LookRotation(float yaw, float pitch)
        {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        private static LookRotation fromDirection(Direction direction)
        {
            switch (direction)
            {
                case DOWN:
                    return new LookRotation(0.0F, 90.0F);
                case UP:
                    return new LookRotation(0.0F, -90.0F);
                case NORTH:
                    return new LookRotation(180.0F, 0.0F);
                case SOUTH:
                    return new LookRotation(0.0F, 0.0F);
                case WEST:
                    return new LookRotation(90.0F, 0.0F);
                case EAST:
                    return new LookRotation(-90.0F, 0.0F);
            }

            return new LookRotation(0.0F, 0.0F);
        }
    }

    private static class PlacementMatchProperties
    {
        private static final Set<Property<?>> PROPERTIES = Set.of(
                Properties.FACING,
                Properties.HORIZONTAL_FACING,
                Properties.AXIS,
                Properties.BLOCK_HALF,
                Properties.SLAB_TYPE,
                Properties.ROTATION,
                Properties.WALL_MOUNT_LOCATION,
                Properties.DOOR_HINGE,
                Properties.CHEST_TYPE,
                Properties.STAIR_SHAPE
        );
    }
}
