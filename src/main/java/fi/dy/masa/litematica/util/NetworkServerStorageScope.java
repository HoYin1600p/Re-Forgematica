package fi.dy.masa.litematica.util;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.util.FileUtils;
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

        if (commands.contains(command) == false)
        {
            return;
        }

        if (parts.length < 2 && commandRequiresArgument(command))
        {
            return;
        }

        String target = parts.length >= 2 ? parts[1] : command;
        setPendingScope(target);

        DataManager.saveForServerTransfer();
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
            else if (activeScope == null)
            {
                activeScope = getInitialScope();
            }
        }
        else if (pendingScope == null)
        {
            activeScope = null;
        }
    }

    @Nullable
    public static String getActiveStorageScope()
    {
        return Configs.Generic.SERVER_STORAGE_SCOPE_FROM_COMMANDS.getBooleanValue() ? activeScope : null;
    }

    private static void setPendingScope(String target)
    {
        String safeName = FileUtils.generateSimpleSafeFileName(target.toLowerCase(Locale.ROOT));

        if (safeName != null && safeName.isEmpty() == false)
        {
            pendingScope = safeName;
        }
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

    private static boolean commandRequiresArgument(String command)
    {
        return "server".equals(command) || "join".equals(command);
    }
}
