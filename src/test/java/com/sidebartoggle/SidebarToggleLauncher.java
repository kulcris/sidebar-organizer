package com.sidebartoggle;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class SidebarToggleLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(SidebarTogglePlugin.class);
        RuneLite.main(args);
    }
}
