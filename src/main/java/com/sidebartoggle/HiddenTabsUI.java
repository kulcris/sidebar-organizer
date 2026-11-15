package com.sidebartoggle;

import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import net.runelite.client.ui.laf.RuneLiteTabbedPaneUI;

/** Changes presentation only. RuneLite retains every tab, component, and navigation index. */
final class HiddenTabsUI extends RuneLiteTabbedPaneUI
{
    private final Set<Component> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
    private List<Component> order = Collections.emptyList();

    boolean setOrder(List<Component> components)
    {
        if (order.equals(components)) { return false; }
        order = new ArrayList<>(components);
        return true;
    }

    @Override
    protected LayoutManager createLayoutManager()
    {
        if (tabPane.getTabLayoutPolicy() != JTabbedPane.WRAP_TAB_LAYOUT) { return super.createLayoutManager(); }
        return new RuneLiteTabbedPaneLayout()
        {
            @Override
            public void calculateLayoutInfo()
            {
                super.calculateLayoutInfo();
                List<Integer> nativeIndices = visibleIndices();
                List<Integer> visualIndices = visualIndices(nativeIndices);
                List<Rectangle> slots = new ArrayList<>();
                for (int index : nativeIndices) { slots.add(new Rectangle(rects[index])); }
                for (int position = 0; position < visualIndices.size(); position++)
                {
                    rects[visualIndices.get(position)].setBounds(slots.get(position));
                }
            }
        };
    }

    private List<Integer> visibleIndices()
    {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < tabPane.getTabCount(); index++) { if (!hidden(index)) { indices.add(index); } }
        return indices;
    }

    private List<Integer> visualIndices(List<Integer> nativeIndices)
    {
        Map<Component, Integer> ranks = new IdentityHashMap<>();
        for (int position = 0; position < order.size(); position++) { ranks.put(order.get(position), position); }
        List<Integer> movable = new ArrayList<>();
        for (int index : nativeIndices) { if (ranks.containsKey(tabPane.getComponentAt(index))) { movable.add(index); } }
        movable.sort(java.util.Comparator.comparingInt(index -> ranks.get(tabPane.getComponentAt(index))));
        List<Integer> visual = new ArrayList<>(nativeIndices);
        int moving = 0;
        for (int position = 0; position < visual.size(); position++)
        {
            if (ranks.containsKey(tabPane.getComponentAt(visual.get(position)))) { visual.set(position, movable.get(moving++)); }
        }
        return visual;
    }

    @Override
    protected void navigateSelectedTab(int direction)
    {
        boolean previous = direction == SwingConstants.NORTH || direction == SwingConstants.WEST || direction == SwingConstants.PREVIOUS;
        boolean next = direction == SwingConstants.SOUTH || direction == SwingConstants.EAST || direction == SwingConstants.NEXT;
        if (!previous && !next) { super.navigateSelectedTab(direction); return; }
        List<Integer> visual = visualIndices(visibleIndices());
        int selected = visual.indexOf(tabPane.getSelectedIndex());
        for (int step = 1; step <= visual.size(); step++)
        {
            int position = selected < 0 ? (previous ? visual.size() - step : step - 1)
                : Math.floorMod(selected + (previous ? -step : step), visual.size());
            int index = visual.get(position);
            if (tabPane.isEnabledAt(index)) { tabPane.setSelectedIndex(index); return; }
        }
    }

    void setHidden(Set<Component> components)
    {
        hidden.clear();
        hidden.addAll(components);
    }

    boolean hidden(int index)
    {
        return index >= 0 && index < tabPane.getTabCount() && hidden.contains(tabPane.getComponentAt(index));
    }

    @Override
    protected int calculateTabHeight(int placement, int index, int height)
    {
        return hidden(index) ? 0 : super.calculateTabHeight(placement, index, height);
    }

    @Override
    protected int calculateTabWidth(int placement, int index, FontMetrics metrics)
    {
        return hidden(index) ? 0 : super.calculateTabWidth(placement, index, metrics);
    }

    @Override
    protected void paintTab(Graphics graphics, int placement, Rectangle[] bounds, int index, Rectangle icon, Rectangle text)
    {
        if (!hidden(index)) { super.paintTab(graphics, placement, bounds, index, icon, text); }
    }

    @Override
    public int tabForCoordinate(JTabbedPane pane, int x, int y)
    {
        int index = super.tabForCoordinate(pane, x, y);
        return hidden(index) ? -1 : index;
    }
}
