package org.thinkingstudio.forgematica.network;

import fi.dy.masa.litematica.Reference;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

public class ServerStorageScopeIdentity
{
    private static final String CONFIG_FILE = Reference.MOD_ID + "-server.properties";
    private static final String KEY_SCOPE_ID = "serverStorageScopeId";
    private static final String KEY_SCOPE_NAME = "serverStorageScopeName";

    private static String scope;

    public static String getScope()
    {
        if (scope == null)
        {
            scope = loadScope();
        }

        return scope;
    }

    private static String loadScope()
    {
        Path path = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        Properties properties = new Properties();

        if (Files.exists(path))
        {
            try (InputStream in = Files.newInputStream(path))
            {
                properties.load(in);
            }
            catch (IOException ignored)
            {
            }
        }

        String configuredName = properties.getProperty(KEY_SCOPE_NAME, "").trim();
        String configuredId = properties.getProperty(KEY_SCOPE_ID, "").trim();

        if (configuredId.isEmpty())
        {
            configuredId = UUID.randomUUID().toString();
            properties.setProperty(KEY_SCOPE_ID, configuredId);
        }

        if (properties.containsKey(KEY_SCOPE_NAME) == false)
        {
            properties.setProperty(KEY_SCOPE_NAME, "");
        }

        save(path, properties);

        String value = configuredName.isEmpty() ? configuredId : configuredName;
        return value.toLowerCase(Locale.ROOT);
    }

    private static void save(Path path, Properties properties)
    {
        try
        {
            Files.createDirectories(path.getParent());

            try (OutputStream out = Files.newOutputStream(path))
            {
                properties.store(out, "Re-Forgematica server storage scope identity. Set serverStorageScopeName to a stable alias if desired.");
            }
        }
        catch (IOException ignored)
        {
        }
    }
}
