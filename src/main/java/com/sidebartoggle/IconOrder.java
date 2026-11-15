package com.sidebartoggle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persist stable icon keys, including currently unavailable icons, rather than live component indices. */
final class IconOrder
{
    static List<SidebarTogglePlugin.Entry> arrange(List<SidebarTogglePlugin.Entry> entries, List<String> saved)
    {
        Map<String, Integer> ranks = new HashMap<>();
        for (int index = 0; index < saved.size(); index++) { ranks.putIfAbsent(saved.get(index), index); }
        List<SidebarTogglePlugin.Entry> result = new ArrayList<>(entries);
        // Stable sort retains native order for newly discovered items. Native regions stay separate.
        result.sort(java.util.Comparator.comparing(SidebarTogglePlugin.Entry::isAction)
            .thenComparingInt(entry -> ranks.getOrDefault(entry.key(), Integer.MAX_VALUE)));
        return result;
    }

    static List<String> moveBefore(List<String> saved, List<SidebarTogglePlugin.Entry> current, String source, String before)
    {
        SidebarTogglePlugin.Entry moving = null, target = null;
        for (SidebarTogglePlugin.Entry entry : current)
        {
            if (entry.key().equals(source)) { moving = entry; }
            if (entry.key().equals(before)) { target = entry; }
        }
        if (moving == null || source.equals(before) || (before != null && (target == null || moving.isAction() != target.isAction())))
        {
            return new ArrayList<>(saved);
        }
        List<String> result = new ArrayList<>(new java.util.LinkedHashSet<>(saved));
        for (SidebarTogglePlugin.Entry entry : current) { if (!result.contains(entry.key())) { result.add(entry.key()); } }
        result.remove(source);
        result.add(before == null ? result.size() : result.indexOf(before), source);
        return result;
    }
}
