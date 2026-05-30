package fi.dy.masa.litematica.util;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.util.FileUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class NetworkServerStorageScope
{
    private static final String[] BUILT_IN_SERVER_COMMANDS = {
            "millennium", "vaulthalla", "eon", "century", "asgard", "pog", "lobby", "omega", "echo", "vault"
    };

    @Nullable private static String activeScope;
    @Nullable private static String pendingScope;
    private static boolean keepScopeDuringTransfer;

    public static void onClientChatMessage(String message)
    {
        if (Configs.Generic.SERVER_STORAGE_SCOPE_FROM_COMMANDS.getBooleanValue() == false ||
            message.length() < 2 || message.charAt(0) != '/')
        {
            return;
        }

        String[] parts = message.substring(1).trim().split("\\s+");

        if (parts.length == 0 || parts[0].isEmpty())
        {
            return;
        }

        String command = parts[0].toLowerCase(Locale.ROOT);
        Set<String> commands = getConfiguredCommands();

        if (commands.contains(command) == false && usesConfiguredCommandArgument(command, parts, commands) == false)
        {
            return;
        }

        if (parts.length < 2 && commandRequiresArgument(command))
        {
            return;
        }

        String target = parts.length >= 2 ? parts[1] : command;
        setPendingScopeFromCommand(target);
    }

    public static void onWorldLoadPost(@Nullable ClientWorld worldAfter)
    {
        if (worldAfter != null)
        {
            if (pendingScope != null)
            {
                activeScope = pendingScope;
                pendingScope = null;
            }

            keepScopeDuringTransfer = false;

            if (activeScope == null)
            {
                activeScope = getInitialScope();
            }
        }
        else if (keepScopeDuringTransfer == false)
        {
            activeScope = null;
            pendingScope = null;
        }
    }

    @Nullable
    public static String getActiveStorageScope()
    {
        return Configs.Generic.SERVER_STORAGE_SCOPE_FROM_COMMANDS.getBooleanValue() ? activeScope : null;
    }

    public static void setServerProvidedScope(String scope)
    {
        if (Configs.Generic.SERVER_STORAGE_SCOPE_FROM_COMMANDS.getBooleanValue() == false)
        {
            return;
        }

        String safeName = getSafeScopeName(scope);

        if (safeName == null || safeName.equals(activeScope))
        {
            pendingScope = null;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null)
        {
            DataManager.saveForServerTransfer();
        }

        activeScope = safeName;
        pendingScope = null;
        keepScopeDuringTransfer = false;

        if (mc.world != null)
        {
            DataManager.load();
        }
    }

    private static void setPendingScopeFromCommand(String target)
    {
        String safeName = getSafeScopeName(target);

        if (safeName == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null)
        {
            DataManager.saveForServerTransfer();
        }

        pendingScope = safeName;
        keepScopeDuringTransfer = true;
    }

    private static Set<String> getConfiguredCommands()
    {
        Set<String> commands = new HashSet<>();

        for (String command : BUILT_IN_SERVER_COMMANDS)
        {
            commands.add(command);
        }

        String[] parts = Configs.Generic.SERVER_STORAGE_SCOPE_COMMANDS.getStringValue().split(",");

        for (String part : parts)
        {
            String command = part.trim().toLowerCase(Locale.ROOT);

            if (command.startsWith("/"))
            {
                command = command.substring(1);
            }

            if (command.isEmpty() == false)
            {
                commands.add(command);
            }
        }

        return commands;
    }

    private static boolean usesConfiguredCommandArgument(String command, String[] parts, Set<String> commands)
    {
        return commandRequiresArgument(command) &&
               parts.length >= 2 &&
               commands.contains(parts[1].toLowerCase(Locale.ROOT));
    }

    @Nullable
    private static String getInitialScope()
    {
        String value = Configs.Generic.SERVER_STORAGE_SCOPE_INITIAL.getStringValue().trim();

        if (value.isEmpty())
        {
            return null;
        }

        return FileUtils.generateSimpleSafeFileName(value.toLowerCase(Locale.ROOT));
    }

    @Nullable
    private static String getSafeScopeName(String value)
    {
        String safeName = FileUtils.generateSimpleSafeFileName(value.toLowerCase(Locale.ROOT));
        return safeName != null && safeName.isEmpty() == false ? safeName : null;
    }

    private static boolean commandRequiresArgument(String command)
    {
        return "server".equals(command) || "join".equals(command);
    }

}
