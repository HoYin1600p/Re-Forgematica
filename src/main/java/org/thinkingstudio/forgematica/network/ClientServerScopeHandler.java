package org.thinkingstudio.forgematica.network;

import fi.dy.masa.litematica.util.NetworkServerStorageScope;

public class ClientServerScopeHandler
{
    public static void handle(String scope)
    {
        NetworkServerStorageScope.setServerProvidedScope(scope);
    }
}
