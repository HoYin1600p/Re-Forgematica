package fi.dy.masa.litematica.util;

import fi.dy.masa.litematica.config.Configs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SophisticatedBackpacksCompat
{
    private static final String MOD_ID = "sophisticatedbackpacks";
    private static final String BACKPACK_CONTAINER_CLASS = "net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer";
    private static final String MAIN_INVENTORY_HANDLER = "main";
    private static final String CONTENTS_UUID_TAG = "contentsUuid";
    private static final long CONTENTS_REQUEST_INTERVAL_MS = 5000L;
    private static final int TRANSFER_TIMEOUT_TICKS = 240;
    private static final int CONTAINER_SYNC_WAIT_TICKS = 60;
    private static final int TRANSFER_RETRY_COOLDOWN_TICKS = 5;

    @Nullable private static Object packetHandler;
    @Nullable private static Method sendToServerMethod;
    @Nullable private static Constructor<?> blockPickMessageConstructor;
    @Nullable private static Constructor<?> backpackOpenMessageConstructor;
    @Nullable private static Constructor<?> requestContentsMessageConstructor;
    private static boolean initialized;
    private static boolean initializationFailed;
    @Nullable private static PendingTransfer pendingTransfer;

    private static final Map<UUID, Long> LAST_CONTENT_REQUESTS = new HashMap<>();

    public static boolean requestItemPull(ItemStack stack, MinecraftClient mc)
    {
        if (pendingTransfer != null)
        {
            return true;
        }

        if (stack.isEmpty() || mc.player == null || mc.currentScreen != null || init() == false || backpackOpenMessageConstructor == null)
        {
            return false;
        }

        PlayerInventory inventory = mc.player.getInventory();
        int targetHotbarSlot = getTargetHotbarSlot(inventory);

        if (PlayerInventory.isValidHotbarIndex(targetHotbarSlot) == false || isSophisticatedBackpack(inventory.getStack(targetHotbarSlot)))
        {
            return false;
        }

        List<Integer> backpackSlots = findCandidateBackpackSlots(inventory, stack);

        if (backpackSlots.isEmpty())
        {
            return false;
        }

        pendingTransfer = new PendingTransfer(stack.copy(), targetHotbarSlot, backpackSlots);
        inventory.selectedSlot = targetHotbarSlot;
        openNextBackpack();
        WorldUtils.setEasyPlaceLastPickBlockTime();
        return true;
    }

    public static boolean tryRequestBlockPick(ItemStack stack)
    {
        if (stack.isEmpty() || init() == false || blockPickMessageConstructor == null)
        {
            return false;
        }

        try
        {
            Object message = blockPickMessageConstructor.newInstance(stack.copy());
            sendToServerMethod.invoke(packetHandler, message);
            WorldUtils.setEasyPlaceLastPickBlockTime();
            return true;
        }
        catch (Exception e)
        {
            initializationFailed = true;
        }

        return false;
    }

    private static int getTargetHotbarSlot(PlayerInventory inventory)
    {
        int selectedSlot = inventory.selectedSlot;

        if (PlayerInventory.isValidHotbarIndex(selectedSlot) == false ||
            Configs.Generic.SOPHISTICATED_BACKPACKS_AVOID_SWAPPING_TOOLS.getBooleanValue() == false ||
            InventoryUtils.isToolOrDamageable(inventory.getStack(selectedSlot)) == false)
        {
            return selectedSlot;
        }

        for (int slotNum : InventoryUtils.getPickBlockableHotbarSlots())
        {
            if (PlayerInventory.isValidHotbarIndex(slotNum))
            {
                ItemStack stack = inventory.getStack(slotNum);

                if (stack.isEmpty() && isSophisticatedBackpack(stack) == false)
                {
                    return slotNum;
                }
            }
        }

        for (int slotNum : InventoryUtils.getPickBlockableHotbarSlots())
        {
            if (PlayerInventory.isValidHotbarIndex(slotNum))
            {
                ItemStack stack = inventory.getStack(slotNum);

                if (stack.getItem() instanceof BlockItem && isSophisticatedBackpack(stack) == false)
                {
                    return slotNum;
                }
            }
        }

        for (int slotNum : InventoryUtils.getPickBlockableHotbarSlots())
        {
            if (PlayerInventory.isValidHotbarIndex(slotNum))
            {
                ItemStack stack = inventory.getStack(slotNum);

                if (InventoryUtils.isToolOrDamageable(stack) == false && isSophisticatedBackpack(stack) == false)
                {
                    return slotNum;
                }
            }
        }

        return selectedSlot;
    }

    public static boolean hasPendingTransfer()
    {
        return pendingTransfer != null;
    }

    public static void tick(MinecraftClient mc)
    {
        if (pendingTransfer == null || mc.player == null)
        {
            return;
        }

        PendingTransfer transfer = pendingTransfer;

        if (fi.dy.masa.malilib.util.InventoryUtils.areStacksEqual(mc.player.getInventory().getStack(transfer.targetHotbarSlot), transfer.stack))
        {
            closeBackpackScreen(mc);
            pendingTransfer = null;
            return;
        }

        if (--transfer.timeoutTicks <= 0)
        {
            closeBackpackScreen(mc);
            pendingTransfer = null;
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;

        if (handler == null || handler == mc.player.playerScreenHandler || isBackpackContainer(handler) == false)
        {
            if (transfer.waitingForClose)
            {
                transfer.waitingForClose = false;
                openNextBackpack();
            }

            return;
        }

        if (transfer.transferRetryCooldownTicks > 0)
        {
            --transfer.transferRetryCooldownTicks;
            return;
        }

        Slot sourceSlot = findMatchingBackpackSlot(handler, mc.player.getInventory(), transfer.stack);
        Slot targetSlot = findPlayerInventorySlot(handler, mc.player.getInventory(), transfer.targetHotbarSlot);

        if (sourceSlot != null && targetSlot != null)
        {
            moveStackToHotbar(mc, handler, sourceSlot, targetSlot);
            transfer.transferRetryCooldownTicks = TRANSFER_RETRY_COOLDOWN_TICKS;
        }
        else if (--transfer.containerSyncWaitTicks <= 0)
        {
            closeBackpackScreen(mc);
            transfer.containerSyncWaitTicks = CONTAINER_SYNC_WAIT_TICKS;
            transfer.waitingForClose = true;
        }
    }

    public static void requestContentsIfNeeded(ItemStack stack)
    {
        UUID uuid = getContentsUuid(stack);

        if (uuid == null || init() == false || requestContentsMessageConstructor == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastRequest = LAST_CONTENT_REQUESTS.get(uuid);

        if (lastRequest != null && now - lastRequest < CONTENTS_REQUEST_INTERVAL_MS)
        {
            return;
        }

        try
        {
            Object message = requestContentsMessageConstructor.newInstance(uuid);
            sendToServerMethod.invoke(packetHandler, message);
            LAST_CONTENT_REQUESTS.put(uuid, now);
        }
        catch (Exception e)
        {
            initializationFailed = true;
        }
    }

    @Nullable
    private static UUID getContentsUuid(ItemStack stack)
    {
        NbtCompound tag = stack.getNbt();

        if (tag != null && tag.containsUuid(CONTENTS_UUID_TAG))
        {
            return tag.getUuid(CONTENTS_UUID_TAG);
        }

        return null;
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
            Class<?> packetHandlerClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.network.SBPPacketHandler");
            packetHandler = packetHandlerClass.getField("INSTANCE").get(null);
            sendToServerMethod = findSendToServerMethod(packetHandlerClass);

            Class<?> blockPickMessageClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.network.BlockPickMessage");
            blockPickMessageConstructor = blockPickMessageClass.getConstructor(ItemStack.class);

            Class<?> backpackOpenMessageClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenMessage");
            backpackOpenMessageConstructor = backpackOpenMessageClass.getConstructor(int.class, String.class, String.class);

            Class<?> requestContentsMessageClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.network.RequestBackpackInventoryContentsMessage");
            requestContentsMessageConstructor = requestContentsMessageClass.getConstructor(UUID.class);

            initializationFailed = sendToServerMethod == null;
            return initializationFailed == false;
        }
        catch (Exception e)
        {
            initializationFailed = true;
        }

        return false;
    }

    @Nullable
    private static Method findSendToServerMethod(Class<?> packetHandlerClass)
    {
        for (Method method : packetHandlerClass.getMethods())
        {
            if (method.getName().equals("sendToServer") && method.getParameterCount() == 1)
            {
                return method;
            }
        }

        return null;
    }

    private static List<Integer> findCandidateBackpackSlots(PlayerInventory inventory, ItemStack stackReference)
    {
        List<Integer> knownMatches = new ArrayList<>();
        List<Integer> fallbackSlots = new ArrayList<>();

        for (int slot = 0; slot < inventory.main.size(); ++slot)
        {
            ItemStack stack = inventory.main.get(slot);

            if (isSophisticatedBackpack(stack))
            {
                requestContentsIfNeeded(stack);

                if (stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
                        .map(itemHandler -> itemHandlerContainsStack(itemHandler, stackReference))
                        .orElse(false))
                {
                    knownMatches.add(slot);
                }
                else
                {
                    fallbackSlots.add(slot);
                }
            }
        }

        knownMatches.addAll(fallbackSlots);
        return knownMatches;
    }

    private static boolean itemHandlerContainsStack(IItemHandler itemHandler, ItemStack stackReference)
    {
        for (int slot = 0; slot < itemHandler.getSlots(); ++slot)
        {
            if (isPlacementStackMatch(itemHandler.getStackInSlot(slot), stackReference))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isSophisticatedBackpack(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return false;
        }

        Item item = stack.getItem();
        Identifier id = Registry.ITEM.getId(item);

        return MOD_ID.equals(id.getNamespace()) && id.getPath().contains("backpack");
    }

    private static void openNextBackpack()
    {
        if (pendingTransfer == null)
        {
            return;
        }

        PendingTransfer transfer = pendingTransfer;

        if (transfer.nextBackpackIndex >= transfer.backpackSlots.size())
        {
            pendingTransfer = null;
            return;
        }

        int backpackSlot = transfer.backpackSlots.get(transfer.nextBackpackIndex++);

        try
        {
            Object message = backpackOpenMessageConstructor.newInstance(backpackSlot, "", MAIN_INVENTORY_HANDLER);
            sendToServerMethod.invoke(packetHandler, message);
        }
        catch (Exception e)
        {
            initializationFailed = true;
            pendingTransfer = null;
        }
    }

    private static boolean isBackpackContainer(ScreenHandler handler)
    {
        return BACKPACK_CONTAINER_CLASS.equals(handler.getClass().getName());
    }

    @Nullable
    private static Slot findMatchingBackpackSlot(ScreenHandler handler, PlayerInventory playerInventory, ItemStack stack)
    {
        for (Slot slot : handler.slots)
        {
            if (slot.inventory != playerInventory && fi.dy.masa.malilib.util.InventoryUtils.areStacksEqual(slot.getStack(), stack))
            {
                return slot;
            }
        }

        for (Slot slot : handler.slots)
        {
            if (slot.inventory != playerInventory && isPlacementStackMatch(slot.getStack(), stack))
            {
                return slot;
            }
        }

        return null;
    }

    private static boolean isPlacementStackMatch(ItemStack stack, ItemStack stackReference)
    {
        if (stack.isEmpty() || stackReference.isEmpty())
        {
            return false;
        }

        if (fi.dy.masa.malilib.util.InventoryUtils.areStacksEqual(stack, stackReference))
        {
            return true;
        }

        return stackReference.hasNbt() == false && ItemStack.areItemsEqual(stack, stackReference);
    }

    @Nullable
    private static Slot findPlayerInventorySlot(ScreenHandler handler, PlayerInventory playerInventory, int inventorySlot)
    {
        for (Slot slot : handler.slots)
        {
            if (slot.inventory == playerInventory && slot.getIndex() == inventorySlot)
            {
                return slot;
            }
        }

        return null;
    }

    private static void moveStackToHotbar(MinecraftClient mc, ScreenHandler handler, Slot sourceSlot, Slot targetSlot)
    {
        mc.interactionManager.clickSlot(handler.syncId, sourceSlot.id, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, targetSlot.id, 0, SlotActionType.PICKUP, mc.player);

        if (handler.getCursorStack().isEmpty() == false)
        {
            mc.interactionManager.clickSlot(handler.syncId, sourceSlot.id, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private static void closeBackpackScreen(MinecraftClient mc)
    {
        if (mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler)
        {
            mc.player.closeHandledScreen();
        }
    }

    private static class PendingTransfer
    {
        private final ItemStack stack;
        private final int targetHotbarSlot;
        private final List<Integer> backpackSlots;
        private int nextBackpackIndex;
        private int timeoutTicks = TRANSFER_TIMEOUT_TICKS;
        private int containerSyncWaitTicks = CONTAINER_SYNC_WAIT_TICKS;
        private int transferRetryCooldownTicks;
        private boolean waitingForClose;

        private PendingTransfer(ItemStack stack, int targetHotbarSlot, List<Integer> backpackSlots)
        {
            this.stack = stack;
            this.stack.setCount(1);
            this.targetHotbarSlot = targetHotbarSlot;
            this.backpackSlots = backpackSlots;
        }
    }
}
