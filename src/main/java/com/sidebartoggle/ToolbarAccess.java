package com.sidebartoggle;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.TabbedPaneUI;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteTabbedPaneUI;

/** Uses only public Swing accessors and RuneLite's public UI delegate. EDT only. */
final class ToolbarAccess implements AutoCloseable
{
    private final Predicate<JComponent> toolbarMatcher;
    private JTabbedPane sidebar;
    private TabbedPaneUI originalUI;
    private HiddenTabsUI installedUI;
    private final Map<Component, Boolean> originalEnabled = new IdentityHashMap<>();
    private final Map<JButton, Boolean> originalVisibility = new IdentityHashMap<>();
    private final Map<JComponent, LayoutManager> originalLayouts = new IdentityHashMap<>();

    ToolbarAccess()
    {
        this(component -> component.getClass().getName().equals("net.runelite.client.ui.ClientToolbarPanel"));
    }

    ToolbarAccess(Predicate<JComponent> toolbarMatcher) { this.toolbarMatcher = toolbarMatcher; }

    boolean attach(PluginPanel panel)
    {
        if (sidebar == null)
        {
            Component wrapped = panel.getWrappedPanel();
            for (Container parent = wrapped.getParent(); parent != null; parent = parent.getParent())
            {
                if (parent instanceof JTabbedPane && ((JTabbedPane) parent).indexOfComponent(wrapped) >= 0)
                {
                    sidebar = (JTabbedPane) parent;
                    break;
                }
            }
        }
        if (sidebar == null) { return false; }
        if (installedUI != null && sidebar.getUI() == installedUI) { return true; }
        if (!(sidebar.getUI() instanceof RuneLiteTabbedPaneUI))
        {
            throw new IllegalStateException("Unsupported sidebar UI; expected RuneLiteTabbedPaneUI");
        }
        originalUI = sidebar.getUI();
        installedUI = new HiddenTabsUI();
        sidebar.setUI(installedUI);
        return true;
    }

    List<SidebarTogglePlugin.Entry> snapshot(PluginPanel manager)
    {
        List<SidebarTogglePlugin.Entry> entries = new ArrayList<>();
        Map<String, Integer> duplicates = new HashMap<>();
        for (int index = 0; index < sidebar.getTabCount(); index++)
        {
            Component component = sidebar.getComponentAt(index);
            String tooltip = sidebar.getToolTipTextAt(index);
            // Keep RuneLite's configuration available to restore the manager's own hidden icon.
            if (component == manager.getWrappedPanel() || "Configuration".equals(tooltip)) { continue; }
            PluginPanel panel = findPanel(component);
            String name = tooltip == null || tooltip.isEmpty() ? "Sidebar panel" : tooltip;
            String identity = "swing-tab:" + (panel == null ? "panel" : panel.getClass().getName()) + ":" + name;
            entries.add(entry(component, name, identity, duplicates));
        }
        Set<JComponent> scopes = Collections.newSetFromMap(new IdentityHashMap<>());
        Object trailing = sidebar.getClientProperty(FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT);
        if (trailing instanceof Component) { findToolbars((Component) trailing, scopes); }
        JRootPane root = SwingUtilities.getRootPane(sidebar);
        if (root != null && root.getJMenuBar() != null) { findToolbars(root.getJMenuBar(), scopes); }
        for (JComponent scope : scopes)
        {
            for (Component component : scope.getComponents())
            {
                if (!(component instanceof JButton)) { continue; }
                JButton button = (JButton) component;
                String name = button.getToolTipText();
                if (name == null || name.isEmpty() || "Close sidebar".equals(name) || "Open sidebar".equals(name)) { continue; }
                entries.add(entry(button, name, "swing-action:" + name, duplicates));
            }
        }
        return entries;
    }

    private static SidebarTogglePlugin.Entry entry(Component component, String name, String identity, Map<String, Integer> duplicates)
    {
        int ordinal = duplicates.merge(identity, 1, Integer::sum);
        return new SidebarTogglePlugin.Entry(component, ordinal == 1 ? name : name + " (" + ordinal + ")", name, identity + ":" + ordinal);
    }

    void apply(List<SidebarTogglePlugin.Entry> entries, Set<String> hidden)
    {
        apply(entries, hidden, false);
    }

    void apply(List<SidebarTogglePlugin.Entry> entries, Set<String> hidden, boolean customOrder)
    {
        Set<Component> hiddenTabs = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Component> currentTabs = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int index = 0; index < sidebar.getTabCount(); index++) { currentTabs.add(sidebar.getComponentAt(index)); }
        originalEnabled.keySet().removeIf(component -> !currentTabs.contains(component));
        Set<JButton> currentButtons = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<JComponent> dirtyScopes = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean changed = false;
        List<Component> tabOrder = new ArrayList<>();
        Map<JComponent, List<Component>> actionOrder = new IdentityHashMap<>();
        for (SidebarTogglePlugin.Entry entry : entries)
        {
            boolean hide = hidden.contains(entry.key());
            int index = sidebar.indexOfComponent(entry.component);
            if (index >= 0)
            {
                if (customOrder) { tabOrder.add(entry.component); }
                if (hide)
                {
                    hiddenTabs.add(entry.component);
                    boolean newlyHidden = !originalEnabled.containsKey(entry.component);
                    originalEnabled.putIfAbsent(entry.component, sidebar.isEnabledAt(index));
                    if (sidebar.isEnabledAt(index)) { sidebar.setEnabledAt(index, false); changed = true; }
                    if (newlyHidden && sidebar.getSelectedIndex() == index) { sidebar.setSelectedIndex(-1); }
                }
                else if (originalEnabled.containsKey(entry.component))
                {
                    sidebar.setEnabledAt(index, originalEnabled.remove(entry.component));
                    changed = true;
                }
            }
            else if (entry.component instanceof JButton)
            {
                JButton button = (JButton) entry.component;
                currentButtons.add(button);
                JComponent scope = (JComponent) button.getParent();
                actionOrder.computeIfAbsent(scope, unused -> new ArrayList<>()).add(button);
                if ((hide || customOrder) && !originalLayouts.containsKey(scope) && scope.getLayout() instanceof GridLayout)
                {
                    GridLayout layout = (GridLayout) scope.getLayout();
                    originalLayouts.put(scope, layout);
                    scope.setLayout(new VisibleToolbarLayout(layout.getRows() == 1,
                        layout.getRows() == 1 ? layout.getHgap() : layout.getVgap()));
                    dirtyScopes.add(scope);
                }
                if (hide)
                {
                    originalVisibility.putIfAbsent(button, button.isVisible());
                    if (button.isVisible()) { button.setVisible(false); dirtyScopes.add(scope); }
                }
                else if (originalVisibility.containsKey(button))
                {
                    boolean visible = originalVisibility.remove(button);
                    if (button.isVisible() != visible)
                    {
                        button.setVisible(visible);
                        dirtyScopes.add((JComponent) button.getParent());
                    }
                }
            }
        }
        originalVisibility.entrySet().removeIf(item -> {
            if (currentButtons.contains(item.getKey())) { return false; }
            item.getKey().setVisible(item.getValue());
            return true;
        });
        originalLayouts.entrySet().removeIf(item -> {
            JComponent scope = item.getKey();
            boolean hasHiddenButton = originalVisibility.keySet().stream().anyMatch(button -> button.getParent() == scope);
            if (hasHiddenButton || (customOrder && actionOrder.containsKey(scope))) { return false; }
            if (scope.getLayout() instanceof VisibleToolbarLayout) { scope.setLayout(item.getValue()); dirtyScopes.add(scope); }
            return true;
        });
        // Refresh layout only when the hidden set changes; avoid a repaint every polling interval.
        for (int index = 0; index < sidebar.getTabCount(); index++)
        {
            if (installedUI.hidden(index) != hiddenTabs.contains(sidebar.getComponentAt(index))) { changed = true; break; }
        }
        installedUI.setHidden(hiddenTabs);
        if (installedUI.setOrder(tabOrder)) { changed = true; }
        for (Map.Entry<JComponent, List<Component>> item : actionOrder.entrySet())
        {
            if (item.getKey().getLayout() instanceof VisibleToolbarLayout)
            {
                VisibleToolbarLayout layout = (VisibleToolbarLayout) item.getKey().getLayout();
                if (layout.setOrder(customOrder ? item.getValue() : Collections.emptyList())) { dirtyScopes.add(item.getKey()); }
            }
        }
        if (changed) { sidebar.revalidate(); sidebar.repaint(); }
        for (JComponent scope : dirtyScopes)
        {
            scope.setMaximumSize(scope.getPreferredSize());
            scope.revalidate(); scope.repaint();
        }
    }

    @Override
    public void close()
    {
        if (sidebar == null) { return; }
        for (Map.Entry<Component, Boolean> entry : originalEnabled.entrySet())
        {
            int index = sidebar.indexOfComponent(entry.getKey());
            if (index >= 0) { sidebar.setEnabledAt(index, entry.getValue()); }
        }
        for (Map.Entry<JButton, Boolean> entry : originalVisibility.entrySet())
        {
            // Restore properties, never insert a button removed by its originating plugin.
            entry.getKey().setVisible(entry.getValue());
        }
        for (Map.Entry<JComponent, LayoutManager> entry : originalLayouts.entrySet())
        {
            if (entry.getKey().getLayout() instanceof VisibleToolbarLayout)
            {
                entry.getKey().setLayout(entry.getValue());
                entry.getKey().setMaximumSize(entry.getKey().getPreferredSize());
                entry.getKey().revalidate(); entry.getKey().repaint();
            }
        }
        if (sidebar.getUI() == installedUI) { sidebar.setUI(originalUI); }
        sidebar.revalidate(); sidebar.repaint();
        originalEnabled.clear(); originalVisibility.clear(); originalLayouts.clear();
        sidebar = null; installedUI = null; originalUI = null;
    }

    private static PluginPanel findPanel(Component component)
    {
        if (component instanceof PluginPanel) { return (PluginPanel) component; }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                PluginPanel panel = findPanel(child);
                if (panel != null) { return panel; }
            }
        }
        return null;
    }

    private void findToolbars(Component component, Set<JComponent> result)
    {
        // Restrict action discovery to RuneLite's native toolbar, never a plugin's inner buttons.
        if (component instanceof JComponent && toolbarMatcher.test((JComponent) component))
        {
            result.add((JComponent) component);
            return;
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents()) { findToolbars(child, result); }
        }
    }
}
