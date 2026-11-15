package com.sidebartoggle;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(name = "Sidebar Organizer", description = "Hide and reorder plugin sidebar icons while keeping their plugins running", tags = {"sidebar", "icons", "hide", "reorder", "organize"})
public class SidebarTogglePlugin extends Plugin
{
    static final String GROUP = "disablesidebaricons";
    private static final Logger LOG = Logger.getLogger(SidebarTogglePlugin.class.getName());
    @Inject private ClientToolbar toolbar;
    @Inject private ConfigManager configManager;
    private final Set<String> hidden = new HashSet<>();
    private final List<String> order = new ArrayList<>();
    private List<Entry> currentEntries = java.util.Collections.emptyList();
    private ToolbarAccess access;
    private SidebarTogglePanel panel;
    private NavigationButton navigation;
    private Timer timer;
    private boolean running;
    private boolean navigationVisible;
    private String saved;
    private String savedOrder;

    @Provides
    SidebarToggleConfig provideConfig(ConfigManager manager) { return manager.getConfig(SidebarToggleConfig.class); }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (GROUP.equals(event.getGroup())) { SwingUtilities.invokeLater(() -> { if (running) { scan(); } }); }
    }

    @Override
    protected void startUp()
    {
        onEdt(() -> {
            running = true;
            panel = new SidebarTogglePanel(this::setHidden, this::showAll, this::scan, this::moveIcon, this::resetOrder);
            navigation = NavigationButton.builder().tooltip("Sidebar Organizer").icon(icon()).priority(20).panel(panel).build();
            access = new ToolbarAccess();
            // Attach once to locate the enclosing sidebar even when the saved preference hides us.
            toolbar.addNavigation(navigation);
            navigationVisible = true;
            timer = new Timer(1000, event -> scan());
            timer.start();
            SwingUtilities.invokeLater(this::scan);
        });
    }

    @Override
    protected void shutDown()
    {
        onEdt(() -> {
            running = false;
            if (timer != null) { timer.stop(); }
            if (access != null) { access.close(); }
            toolbar.removeNavigation(navigation);
            navigationVisible = false;
            hidden.clear();
            order.clear();
            currentEntries = java.util.Collections.emptyList();
            saved = null;
            savedOrder = null;
        });
    }

    private static void onEdt(Runnable action)
    {
        if (SwingUtilities.isEventDispatchThread()) { action.run(); }
        else { SwingUtilities.invokeLater(action); }
    }

    void scan()
    {
        if (!running || access == null) { return; }
        try
        {
            // Recovery must also work after a UI compatibility failure detached our adapter.
            if (!navigationVisible && !Boolean.parseBoolean(configManager.getConfiguration(GROUP, "hideOwnSidebar")))
            {
                updateOwnNavigation();
            }
            if (!access.attach(panel)) { return; }
            updateOwnNavigation();
            String value = configManager.getConfiguration(GROUP, "hidden");
            if (!Objects.equals(value, saved))
            {
                saved = value;
                hidden.clear();
                if (value != null && !value.isEmpty()) { java.util.Collections.addAll(hidden, value.split(",")); }
            }
            String orderValue = configManager.getConfiguration(GROUP, "order");
            if (!Objects.equals(orderValue, savedOrder))
            {
                savedOrder = orderValue;
                order.clear();
                if (orderValue != null && !orderValue.isEmpty())
                {
                    order.addAll(new java.util.LinkedHashSet<>(java.util.Arrays.asList(orderValue.split(","))));
                }
            }
            List<Entry> entries = access.snapshot(panel);
            if (migrateLegacy(hidden, entries)) { persist(); }
            currentEntries = IconOrder.arrange(entries, order);
            access.apply(currentEntries, hidden, !order.isEmpty());
            panel.update(currentEntries, hidden);
        }
        catch (RuntimeException ex)
        {
            if (timer != null) { timer.stop(); }
            access.close();
            LOG.log(Level.WARNING, "Sidebar presentation could not be installed", ex);
            panel.error("Unsupported sidebar UI. Icons have been restored. See the client log.");
        }
    }

    void updateOwnNavigation()
    {
        boolean visible = !Boolean.parseBoolean(configManager.getConfiguration(GROUP, "hideOwnSidebar"));
        if (visible != navigationVisible)
        {
            if (visible) { toolbar.addNavigation(navigation); }
            else { toolbar.removeNavigation(navigation); }
            navigationVisible = visible;
        }
    }

    private void setHidden(String key, boolean hide)
    {
        if (hide) { hidden.add(key); } else { hidden.remove(key); }
        persist(); scan();
    }

    private void showAll() { hidden.clear(); persist(); scan(); }

    void moveIcon(String source, String before)
    {
        List<String> next = IconOrder.moveBefore(order, currentEntries, source, before);
        if (next.equals(order)) { return; }
        order.clear(); order.addAll(next);
        persistOrder(); scan();
    }

    private void resetOrder() { order.clear(); persistOrder(); scan(); }

    private void persistOrder()
    {
        savedOrder = String.join(",", order);
        configManager.setConfiguration(GROUP, "order", savedOrder);
    }

    private void persist()
    {
        saved = String.join(",", new TreeSet<>(hidden));
        configManager.setConfiguration(GROUP, "hidden", saved);
    }

    /** Preserve settings from the earlier navigation-handle implementation by matching icon tooltips. */
    static boolean migrateLegacy(Set<String> hidden, List<Entry> entries)
    {
        boolean changed = false;
        for (String key : new ArrayList<>(hidden))
        {
            String decoded;
            try { decoded = new String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8); }
            catch (IllegalArgumentException ex) { continue; }
            if (decoded.startsWith("swing-tab:") || decoded.startsWith("swing-action:")) { continue; }
            int first = decoded.indexOf(':'), last = decoded.lastIndexOf(':');
            if (first < 0) { continue; }
            String tooltip = last > first ? decoded.substring(first + 1, last) : decoded.substring(first + 1);
            boolean matched = false;
            for (Entry entry : entries)
            {
                if (entry.tooltip.equals(tooltip)) { hidden.add(entry.key()); matched = true; }
            }
            if (matched) { hidden.remove(key); changed = true; }
        }
        return changed;
    }

    static final class Entry
    {
        final Component component;
        final String tooltip;
        private final String label;
        private final String key;
        Entry(Component component, String label, String tooltip, String identity)
        {
            this.component = component; this.label = label; this.tooltip = tooltip;
            key = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.getBytes(StandardCharsets.UTF_8));
        }
        String label() { return label; }
        String key() { return key; }
        boolean isAction() { return component instanceof javax.swing.JButton; }
    }

    private static BufferedImage icon()
    {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(255, 183, 64));
        g.drawRoundRect(3, 3, 17, 17, 3, 3); g.fillRect(5, 5, 4, 13);
        g.setColor(Color.WHITE);
        g.fillRect(12, 6, 5, 2); g.fillRect(12, 11, 5, 2); g.fillRect(12, 16, 5, 2);
        g.dispose();
        return image;
    }
}
