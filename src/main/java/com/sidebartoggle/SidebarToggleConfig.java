package com.sidebartoggle;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SidebarTogglePlugin.GROUP)
public interface SidebarToggleConfig extends Config
{
    @ConfigItem(
        keyName = "hideOwnSidebar",
        name = "Hide own sidebar icon",
        description = "Hide the Sidebar Organizer icon. Turn this off here to restore it.",
        position = 0
    )
    default boolean hideOwnSidebar()
    {
        return false;
    }
}
