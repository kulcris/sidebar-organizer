package com.sidebartoggle;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The native toolbar uses a one-dimensional grid that reserves space for invisible buttons. */
final class VisibleToolbarLayout implements LayoutManager
{
    private final boolean horizontal;
    private final int gap;
    private List<Component> order = Collections.emptyList();

    VisibleToolbarLayout(boolean horizontal, int gap) { this.horizontal = horizontal; this.gap = gap; }
    boolean setOrder(List<Component> components)
    {
        if (order.equals(components)) { return false; }
        order = new ArrayList<>(components);
        return true;
    }
    public void addLayoutComponent(String name, Component component) { }
    public void removeLayoutComponent(Component component) { }
    public Dimension preferredLayoutSize(Container parent) { return size(parent, false); }
    public Dimension minimumLayoutSize(Container parent) { return size(parent, true); }

    private Dimension size(Container parent, boolean minimum)
    {
        int along = 0, across = 0, count = 0;
        for (Component component : visibleComponents(parent))
        {
            Dimension size = minimum ? component.getMinimumSize() : component.getPreferredSize();
            along += horizontal ? size.width : size.height;
            across = Math.max(across, horizontal ? size.height : size.width);
            count++;
        }
        along += Math.max(0, count - 1) * gap;
        Insets insets = parent.getInsets();
        return new Dimension((horizontal ? along : across) + insets.left + insets.right,
            (horizontal ? across : along) + insets.top + insets.bottom);
    }

    public void layoutContainer(Container parent)
    {
        Insets insets = parent.getInsets();
        int position = horizontal ? insets.left : insets.top;
        for (Component component : parent.getComponents()) { if (!component.isVisible()) { component.setBounds(0, 0, 0, 0); } }
        for (Component component : visibleComponents(parent))
        {
            Dimension size = component.getPreferredSize();
            component.setBounds(horizontal ? position : insets.left, horizontal ? insets.top : position, size.width, size.height);
            position += (horizontal ? size.width : size.height) + gap;
        }
    }

    private List<Component> visibleComponents(Container parent)
    {
        List<Component> nativeOrder = new ArrayList<>();
        for (Component component : parent.getComponents()) { if (component.isVisible()) { nativeOrder.add(component); } }
        Map<Component, Integer> ranks = new IdentityHashMap<>();
        for (int index = 0; index < order.size(); index++) { ranks.put(order.get(index), index); }
        List<Component> movable = new ArrayList<>();
        for (Component component : nativeOrder) { if (ranks.containsKey(component)) { movable.add(component); } }
        movable.sort(java.util.Comparator.comparingInt(ranks::get));
        int moving = 0;
        for (int index = 0; index < nativeOrder.size(); index++)
        {
            if (ranks.containsKey(nativeOrder.get(index))) { nativeOrder.set(index, movable.get(moving++)); }
        }
        return nativeOrder;
    }
}
