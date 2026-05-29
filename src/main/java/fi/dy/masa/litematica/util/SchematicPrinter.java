package fi.dy.masa.litematica.util;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.block.*;




































































































import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
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
import java.util.Locale;
import java.util.Set;

public class SchematicPrinter
{
    private static final Set<String> IGNORED_PLACEMENT_MATCH_PROPERTY_NAMES = Set.of(
            "enabled",
            "extended",
            "lit",
            "locked",
            "powered",
            "triggered"
    );

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
        PlacementTarget target = getPlacementTarget(schematicWorld, pos);

        if (target == null)
        {
            return false;
        }

        BlockState stateSchematic = target.state;
        BlockState stateClient = mc.world.getBlockState(target.pos);

        if (stateSchematic.isAir() || stateSchematic == stateClient)
        {
            return false;
        }

        ItemStack stack = MaterialCache.getInstance().getRequiredBuildItemForState(stateSchematic, schematicWorld, target.pos);

        if (stack.isEmpty())
        {
            return false;
        }

        boolean placeSecondSlab = canPlaceSecondSlab(stateSchematic, stateClient);

        if (stateSchematic.canPlaceAt(mc.world, target.pos) == false && placeSecondSlab == false)
        {
            return false;
        }

        if (canReplaceTarget(mc, target.pos) == false && placeSecondSlab == false)
        {
            return false;
        }

        InventoryUtils.schematicWorldPickBlock(stack, target.pos, schematicWorld, mc);

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
        Vec3d hitPos = getHitPos(target.pos, stateSchematic, protocol);
        PlacementPlan placementPlan = getPlacementPlan(mc, stateSchematic, stack, hand, target.pos, hitPos, side);

        interactWithPlacementPlan(mc, hand, placementPlan, stateSchematic, protocol);

        if (placeSecondSlab)
        {
            BlockState updatedClientState = mc.world.getBlockState(target.pos);

            if (updatedClientState.getBlock() instanceof SlabBlock &&
                updatedClientState.get(SlabBlock.TYPE) != SlabType.DOUBLE)
            {
                Direction secondSide = WorldUtils.applyPlacementFacing(stateSchematic, Direction.UP, updatedClientState);
                BlockHitResult secondHitResult = new BlockHitResult(hitPos, secondSide, target.pos, false);

                interactWithPlacementPlan(mc, hand, new PlacementPlan(secondHitResult, placementPlan.rotation, placementPlan.sneak), stateSchematic, protocol);
            }
        }

        return true;
    }

    private static PlacementTarget getPlacementTarget(WorldSchematic schematicWorld, BlockPos pos)
    {
        BlockState state = schematicWorld.getBlockState(pos);

        if (isLowerHalfOfDoubleBlock(state))
        {
            return null;
        }

        if (isUpperHalfOfDoubleBlock(state))
        {
            BlockPos lowerPos = pos.down();
            BlockState lowerState = schematicWorld.getBlockState(lowerPos);

            if (usesLowerPlacementAnchor(state) && isMatchingLowerHalf(state, lowerState))
            {
                return new PlacementTarget(lowerPos, lowerState);
            }
        }

        return new PlacementTarget(pos, state);
    }

    private static boolean isLowerHalfOfDoubleBlock(BlockState state)
    {
        return state.contains(DoorBlock.HALF) && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean isUpperHalfOfDoubleBlock(BlockState state)
    {
        return state.contains(DoorBlock.HALF) && state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    private static boolean usesLowerPlacementAnchor(BlockState state)
    {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TallPlantBlock;
    }

    private static boolean isMatchingLowerHalf(BlockState upperState, BlockState lowerState)
    {
        return lowerState.getBlock() == upperState.getBlock() &&
               lowerState.contains(DoorBlock.HALF) &&
               lowerState.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
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
            hitPos = WorldUtils.applyPlacementProtocolV3(pos, stateSchematic, hitPos);
        }
        else if (protocol == EasyPlaceProtocol.V2)
        {
            hitPos = WorldUtils.applyCarpetProtocolHitVec(pos, stateSchematic, hitPos);
        }
        else if (protocol == EasyPlaceProtocol.SLAB_ONLY)
        {
            hitPos = WorldUtils.applyBlockSlabProtocol(pos, stateSchematic, hitPos);
        }

        return WorldUtils.applyBlockSlabProtocol(pos, stateSchematic, hitPos);
    }

    private static void sendRotation(MinecraftClient mc, LookRotation rotation)
    {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                rotation.yaw,
                rotation.pitch,
                mc.player.isOnGround()
        ));
    }

    private static void interactWithPlacementPlan(MinecraftClient mc, Hand hand, PlacementPlan placementPlan,
                                                  BlockState stateSchematic, EasyPlaceProtocol protocol)
    {
        if (placementPlan.rotation == null)
        {
            boolean appliedSneak = applySneakState(mc, placementPlan.sneak);

            try
            {
                WorldUtils.interactBlockWithOptionalQuarkRotationLock(mc, hand, placementPlan.hitResult, stateSchematic, protocol);
            }
            finally
            {
                restoreSneakState(mc, appliedSneak);
            }

            return;
        }

        float originalYaw = mc.player.getYaw();
        float originalPitch = mc.player.getPitch();
        boolean appliedSneak = false;

        try
        {
            // Some blocks compute placement state from the local player rotation inside interactBlock.
            // Keep the simulated rotation active until the actual placement interaction has returned.
            mc.player.setYaw(placementPlan.rotation.yaw);
            mc.player.setPitch(placementPlan.rotation.pitch);
            appliedSneak = applySneakState(mc, placementPlan.sneak);
            sendRotation(mc, placementPlan.rotation);
            WorldUtils.interactBlockWithOptionalQuarkRotationLock(mc, hand, placementPlan.hitResult, stateSchematic, protocol);
        }
        finally
        {
            restoreSneakState(mc, appliedSneak);
            mc.player.setYaw(originalYaw);
            mc.player.setPitch(originalPitch);
        }
    }

    private static boolean applySneakState(MinecraftClient mc, boolean sneak)
    {
        if (sneak && mc.player.isSneaking() == false)
        {
            mc.player.setSneaking(true);
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            return true;
        }

        return false;
    }

    private static void restoreSneakState(MinecraftClient mc, boolean appliedSneak)
    {
        if (appliedSneak && mc.player.input.sneaking == false)
        {
            mc.player.setSneaking(false);
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        }
    }

    private static PlacementPlan getPlacementPlan(MinecraftClient mc, BlockState state, ItemStack stack, Hand hand,
                                                  BlockPos pos, Vec3d hitPos, Direction fallbackSide)
    {
        PlacementPlan directPlan = getDirectPlacementPlan(state, pos, hitPos, fallbackSide);

        if (directPlan != null)
        {
            return directPlan;
        }

        PlacementPlan simulatedPlan = getSimulatedPlacementPlan(mc, state, stack, hand, pos, hitPos, fallbackSide);

        if (simulatedPlan != null)
        {
            return simulatedPlan;
        }

        Direction lookDirection = getPlacementLookDirection(state);
        LookRotation rotation = lookDirection != null ? LookRotation.fromDirection(lookDirection) : null;
        return new PlacementPlan(new BlockHitResult(hitPos, fallbackSide, pos, false), rotation, false);
    }

    private static PlacementPlan getDirectPlacementPlan(BlockState state, BlockPos pos, Vec3d hitPos, Direction fallbackSide)
    {
        Direction supportFace = getRequiredSupportFace(state);

        if (supportFace != null)
        {
            BlockHitResult hitResult = getSupportFaceHitResult(pos, supportFace);
            return new PlacementPlan(hitResult, LookRotation.fromDirection(supportFace.getOpposite()), true);
        }

        Direction redstoneLookDirection = getRedstonePlacementLookDirection(state);

        if (redstoneLookDirection != null)
        {
            // Accurate-placement protocol data in hitPos is the primary state override.
            // Rotation is kept as a fallback for protocol modes that cannot encode full state.
            BlockHitResult hitResult = new BlockHitResult(hitPos, fallbackSide, pos, false);
            return new PlacementPlan(hitResult, LookRotation.fromDirection(redstoneLookDirection), false);
        }

        PlacementPlan rotationPropertyPlan = getRotationPropertyPlacementPlan(state, pos, hitPos);

        if (rotationPropertyPlan != null)
        {
            return rotationPropertyPlan;
        }

        return null;
    }

    private static PlacementPlan getRotationPropertyPlacementPlan(BlockState state, BlockPos pos, Vec3d hitPos)
    {
        if (state.contains(Properties.ROTATION) == false)
        {
            return null;
        }

        int rotation = state.get(Properties.ROTATION);
        String className = state.getBlock().getClass().getSimpleName();

        if (className.equals("BannerBlock") || className.equals("SignBlock") || className.equals("StandingSignBlock"))
        {
            rotation = (rotation + 8) & 15;
        }

        BlockHitResult hitResult = getStandingRotationHitResult(pos, hitPos);
        return new PlacementPlan(hitResult, LookRotation.fromYaw(getYawForRotation(rotation)), true);
    }

    private static float getYawForRotation(int rotation)
    {
        int normalized = rotation & 15;
        int distanceToZero = normalized > 8 ? 16 - normalized : normalized;
        float sign = normalized > 8 ? -1.0F : 1.0F;

        return Math.round(distanceToZero / 8.0F * 180.0F * sign);
    }

    private static BlockHitResult getStandingRotationHitResult(BlockPos pos, Vec3d encodedHitPos)
    {
        BlockPos supportPos = pos.down();
        Vec3d supportHitPos = new Vec3d(encodedHitPos.x, pos.getY(), encodedHitPos.z);

        return new BlockHitResult(supportHitPos, Direction.UP, supportPos, false);
    }

    private static Direction getRequiredSupportFace(BlockState state)
    {
        Direction wallMountSupportFace = getWallMountSupportFace(state);

        if (wallMountSupportFace != null)
        {
            return wallMountSupportFace;
        }

        if (isWallAttachedBlock(state))
        {
            return getHorizontalSupportFace(state);
        }

        return null;
    }

    private static Direction getWallMountSupportFace(BlockState state)
    {
        if (state.contains(Properties.WALL_MOUNT_LOCATION) == false)
        {
            return null;
        }

        String location = state.get(Properties.WALL_MOUNT_LOCATION).toString().toLowerCase(Locale.ROOT);

        if (location.contains("floor"))
        {
            return Direction.UP;
        }

        if (location.contains("ceiling"))
        {
            return Direction.DOWN;
        }

        if (location.contains("wall"))
        {
            return getHorizontalSupportFace(state);
        }

        return null;
    }

    private static boolean isWallAttachedBlock(BlockState state)
    {
        Block block = state.getBlock();
        String className = block.getClass().getSimpleName();

        return block instanceof LadderBlock ||
               block instanceof WallBannerBlock ||
               block instanceof WallSignBlock ||
               block instanceof WallSkullBlock ||
               block instanceof WallTorchBlock ||
               (className.startsWith("Wall") || className.contains("WallTorch")) &&
               (state.contains(Properties.HORIZONTAL_FACING) || state.contains(Properties.FACING));
    }

    private static Direction getHorizontalSupportFace(BlockState state)
    {
        if (state.contains(Properties.HORIZONTAL_FACING))
        {
            return state.get(Properties.HORIZONTAL_FACING);
        }

        if (state.contains(Properties.FACING))
        {
            Direction direction = state.get(Properties.FACING);
            return direction.getAxis().isHorizontal() ? direction : null;
        }

        return null;
    }

    private static Direction getRedstonePlacementLookDirection(BlockState state)
    {
        if (isDirectionalRedstoneBlock(state) == false)
        {
            return null;
        }

        if (state.getBlock() instanceof ObserverBlock && state.contains(Properties.FACING))
        {
            return state.get(Properties.FACING);
        }

        return getPlacementLookDirection(state);
    }

    private static boolean isDirectionalRedstoneBlock(BlockState state)
    {
        Block block = state.getBlock();
        String className = block.getClass().getSimpleName();

        return block instanceof ObserverBlock ||
               block instanceof RepeaterBlock ||
               block instanceof ComparatorBlock ||
               className.equals("DispenserBlock") ||
               className.equals("DropperBlock") ||
               className.equals("PistonBlock") ||
               className.equals("StickyPistonBlock");
    }

    private static PlacementPlan getSimulatedPlacementPlan(MinecraftClient mc, BlockState targetState, ItemStack stack,
                                                           Hand hand, BlockPos pos, Vec3d hitPos, Direction fallbackSide)
    {
        if ((stack.getItem() instanceof BlockItem) == false || targetState.getProperties().isEmpty())
        {
            return null;
        }

        BlockItem blockItem = (BlockItem) stack.getItem();
        PlacementPlan bestPlan = null;
        int bestScore = 0;
        float originalYaw = mc.player.getYaw();
        float originalPitch = mc.player.getPitch();
        boolean originalSneaking = mc.player.isSneaking();

        try
        {
            for (HitResultCandidate hitResultCandidate : getHitResultCandidates(targetState, pos, hitPos, fallbackSide))
            {
                for (LookRotation rotation : LookRotation.CANDIDATES)
                {
                    mc.player.setYaw(rotation.yaw);
                    mc.player.setPitch(rotation.pitch);
                    mc.player.setSneaking(originalSneaking || hitResultCandidate.sneak);

                    ItemPlacementContext ctx = new ItemPlacementContext(new ItemUsageContext(mc.player, hand, hitResultCandidate.hitResult));
                    BlockState resultState = blockItem.getBlock().getPlacementState(ctx);
                    int score = getPlacementMatchScore(targetState, resultState);

                    if (score > bestScore)
                    {
                        bestScore = score;
                        bestPlan = new PlacementPlan(hitResultCandidate.hitResult, rotation, hitResultCandidate.sneak);
                    }
                }
            }
        }
        finally
        {
            mc.player.setYaw(originalYaw);
            mc.player.setPitch(originalPitch);
            mc.player.setSneaking(originalSneaking);
        }

        return bestScore > 1 ? bestPlan : null;
    }

    private static List<HitResultCandidate> getHitResultCandidates(BlockState state, BlockPos pos, Vec3d hitPos, Direction fallbackSide)
    {
        List<HitResultCandidate> hitResults = new ArrayList<>();
        List<Direction> sides = getSideCandidates(state, fallbackSide);

        for (Direction side : sides)
        {
            addHitResult(hitResults, new BlockHitResult(hitPos, side, pos, false), false);
        }

        return hitResults;
    }

    private static BlockHitResult getSupportFaceHitResult(BlockPos pos, Direction side)
    {
        BlockPos supportPos = pos.offset(side.getOpposite());
        Vec3d supportHitPos = Vec3d.ofCenter(supportPos).add(
                side.getOffsetX() * 0.5D,
                side.getOffsetY() * 0.5D,
                side.getOffsetZ() * 0.5D
        );

        return new BlockHitResult(supportHitPos, side, supportPos, false);
    }

    private static void addHitResult(List<HitResultCandidate> hitResults, BlockHitResult hitResult, boolean sneak)
    {
        for (HitResultCandidate existing : hitResults)
        {
            if (existing.hitResult.getBlockPos().equals(hitResult.getBlockPos()) &&
                existing.hitResult.getSide() == hitResult.getSide())
            {
                return;
            }
        }

        hitResults.add(new HitResultCandidate(hitResult, sneak));
    }

    private static List<Direction> getSideCandidates(BlockState state, Direction fallbackSide)
    {
        List<Direction> sides = new ArrayList<>();

        if (state.contains(Properties.FACING))
        {
            addSideCandidate(sides, state.get(Properties.FACING));
        }

        if (state.contains(Properties.HORIZONTAL_FACING))
        {
            Direction direction = state.get(Properties.HORIZONTAL_FACING);
            addSideCandidate(sides, direction);
            addSideCandidate(sides, direction.getOpposite());
        }

        addSideCandidate(sides, fallbackSide);

        for (Direction direction : Direction.values())
        {
            addSideCandidate(sides, direction);
        }

        return sides;
    }

    private static void addSideCandidate(List<Direction> sides, Direction side)
    {
        if (sides.contains(side) == false)
        {
            sides.add(side);
        }
    }

    private static int getPlacementMatchScore(BlockState targetState, BlockState resultState)
    {
        if (resultState == null || resultState.getBlock() != targetState.getBlock())
        {
            return 0;
        }

        if (resultState.equals(targetState))
        {
            return 1000 + targetState.getProperties().size();
        }

        int score = 1;

        for (Property<?> property : targetState.getProperties())
        {
            if (IGNORED_PLACEMENT_MATCH_PROPERTY_NAMES.contains(property.getName()))
            {
                continue;
            }

            if (resultState.contains(property))
            {
                if (propertyValuesEqual(targetState, resultState, property))
                {
                    ++score;
                }
                else
                {
                    score -= 2;
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
            if (state.getBlock() instanceof ObserverBlock)
            {
                return state.get(Properties.FACING);
            }

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

        private static LookRotation fromYaw(float yaw)
        {
            return new LookRotation(yaw, 0.0F);
        }
    }

    private static class PlacementPlan
    {
        private final BlockHitResult hitResult;
        private final LookRotation rotation;
        private final boolean sneak;

        private PlacementPlan(BlockHitResult hitResult, LookRotation rotation, boolean sneak)
        {
            this.hitResult = hitResult;
            this.rotation = rotation;
            this.sneak = sneak;
        }
    }

    private static class HitResultCandidate
    {
        private final BlockHitResult hitResult;
        private final boolean sneak;

        private HitResultCandidate(BlockHitResult hitResult, boolean sneak)
        {
            this.hitResult = hitResult;
            this.sneak = sneak;
        }
    }

    private static class PlacementTarget
    {
        private final BlockPos pos;
        private final BlockState state;

        private PlacementTarget(BlockPos pos, BlockState state)
        {
            this.pos = pos;
            this.state = state;
        }
    }
}
