package fi.dy.masa.litematica.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.Direction;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class QuarkRotationCompat
{
    private static final String MOD_ID = "quark";
    private static final int SECOND_CLEAR_DELAY_TICKS = 2;

    @Nullable private static Constructor<?> lockProfileConstructor;
    @Nullable private static Constructor<?> setLockProfileMessageConstructor;
    @Nullable private static Method sendToServerMethod;
    @Nullable private static Property<?> verticalSlabTypeProperty;
    private static boolean initialized;
    private static boolean initializationFailed;
    private static int delayedClearTicks = -1;

    public static boolean applyLockForState(BlockState state, MinecraftClient mc)
    {
        if (mc.isInSingleplayer() || init() == false)
        {
            return false;
        }

        @Nullable LockProfile profile = getLockProfileForState(state);

        return profile != null && sendProfile(profile);
    }

    public static void clearLockWithRetry()
    {
        if (sendProfile(null))
        {
            delayedClearTicks = SECOND_CLEAR_DELAY_TICKS;
        }
    }

    public static void tick(MinecraftClient mc)
    {
        if (delayedClearTicks >= 0 && --delayedClearTicks <= 0)
        {
            sendProfile(null);
            delayedClearTicks = -1;
        }
    }

    private static boolean init()
    {
        if (initialized)
        {
            return initializationFailed == false;
        }

        initialized = true;

        if (ModList.get().isLoaded(MOD_ID) == false)
        {
            initializationFailed = true;
            return false;
        }

        try
        {
            Class<?> lockProfileClass = Class.forName("vazkii.quark.content.tweaks.module.LockRotationModule$LockProfile");
            Class<?> setLockProfileMessageClass = Class.forName("vazkii.quark.base.network.message.SetLockProfileMessage");
            Class<?> quarkNetworkClass = Class.forName("vazkii.quark.base.network.QuarkNetwork");

            lockProfileConstructor = lockProfileClass.getConstructor(Direction.class, int.class);
            setLockProfileMessageConstructor = setLockProfileMessageClass.getConstructor(lockProfileClass);
            sendToServerMethod = findSendToServerMethod(quarkNetworkClass);
            verticalSlabTypeProperty = findVerticalSlabTypeProperty();
            initializationFailed = sendToServerMethod == null;
        }
        catch (Exception e)
        {
            initializationFailed = true;
        }

        return initializationFailed == false;
    }

    @Nullable
    private static Method findSendToServerMethod(Class<?> quarkNetworkClass)
    {
        for (Method method : quarkNetworkClass.getMethods())
        {
            if (method.getName().equals("sendToServer") && method.getParameterCount() == 1)
            {
                return method;
            }
        }

        return null;
    }

    @Nullable
    private static Property<?> findVerticalSlabTypeProperty()
    {
        try
        {
            Class<?> verticalSlabBlockClass = Class.forName("vazkii.quark.content.building.block.VerticalSlabBlock");
            Field field = verticalSlabBlockClass.getField("TYPE");
            Object value = field.get(null);

            if (value instanceof Property<?> property)
            {
                return property;
            }
        }
        catch (Exception ignore) {}

        return null;
    }

    private static boolean sendProfile(@Nullable LockProfile profile)
    {
        if (init() == false)
        {
            return false;
        }

        try
        {
            Object quarkProfile = profile != null ? lockProfileConstructor.newInstance(profile.facing, profile.half) : null;
            Object message = setLockProfileMessageConstructor.newInstance(new Object[] { quarkProfile });
            sendToServerMethod.invoke(null, message);
            return true;
        }
        catch (Exception e)
        {
            initializationFailed = true;
            delayedClearTicks = -1;
        }

        return false;
    }

    @Nullable
    private static LockProfile getLockProfileForState(BlockState state)
    {
        @Nullable Direction quarkDirection = getQuarkRotationDirection(state);
        int half = getQuarkHalf(state);

        if (quarkDirection == null && half != -1)
        {
            quarkDirection = Direction.DOWN;
        }

        if (quarkDirection == null)
        {
            return null;
        }

        return new LockProfile(quarkDirection.getOpposite(), half);
    }

    @Nullable
    private static Direction getQuarkRotationDirection(BlockState state)
    {
        if (state.contains(Properties.FACING))
        {
            return state.get(Properties.FACING);
        }

        @Nullable Direction verticalSlabDirection = getVerticalSlabDirection(state);

        if (verticalSlabDirection != null)
        {
            return verticalSlabDirection;
        }

        if (state.contains(Properties.HORIZONTAL_FACING))
        {
            Direction direction = state.get(Properties.HORIZONTAL_FACING);
            return state.getBlock() instanceof StairsBlock ? direction.getOpposite() : direction;
        }

        if (state.contains(Properties.AXIS))
        {
            return getDirectionForAxis(state.get(Properties.AXIS));
        }

        return null;
    }

    @Nullable
    private static Direction getVerticalSlabDirection(BlockState state)
    {
        if (verticalSlabTypeProperty != null && state.contains(verticalSlabTypeProperty))
        {
            Object value = state.get(verticalSlabTypeProperty);
            return getDirectionByName(value.toString());
        }

        return null;
    }

    @Nullable
    private static Direction getDirectionByName(String name)
    {
        return switch (name)
        {
            case "down" -> Direction.DOWN;
            case "up" -> Direction.UP;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "east" -> Direction.EAST;
            default -> null;
        };
    }

    private static Direction getDirectionForAxis(Direction.Axis axis)
    {
        return switch (axis)
        {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }

    private static int getQuarkHalf(BlockState state)
    {
        if (state.contains(Properties.SLAB_TYPE))
        {
            SlabType slabType = state.get(Properties.SLAB_TYPE);

            if (slabType == SlabType.DOUBLE)
            {
                return -1;
            }

            return slabType == SlabType.TOP ? 1 : 0;
        }

        if (state.contains(Properties.BLOCK_HALF))
        {
            return state.get(Properties.BLOCK_HALF) == BlockHalf.TOP ? 1 : 0;
        }

        return -1;
    }

    private static class LockProfile
    {
        private final Direction facing;
        private final int half;

        private LockProfile(Direction facing, int half)
        {
            this.facing = facing;
            this.half = half;
        }
    }
}
