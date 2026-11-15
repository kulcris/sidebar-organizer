package com.sidebartoggle;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.TabbedPaneUI;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.laf.RuneLiteLAF;
import net.runelite.client.ui.laf.RuneLiteTabbedPaneUI;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class ToolbarAccessTest
{
    @BeforeClass
    public static void lookAndFeel() throws Exception { SwingUtilities.invokeAndWait(RuneLiteLAF::setup); }

    @Test
    public void hidesWithoutRemovingPanelsOrChangingIndicesAndRestoresNativeUi() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            Component first = fixture.add("First"), second = fixture.add("Second");
            fixture.add("Configuration");
            fixture.tabs.setSelectedComponent(first);
            TabbedPaneUI nativeUI = fixture.tabs.getUI();
            assertTrue(fixture.access.attach(fixture.manager));
            List<SidebarTogglePlugin.Entry> entries = fixture.access.snapshot(fixture.manager);
            assertEquals(2, entries.size());
            int firstIndex = fixture.tabs.indexOfComponent(first), secondIndex = fixture.tabs.indexOfComponent(second);
            fixture.tabs.doLayout();
            Rectangle before = fixture.tabs.getBoundsAt(firstIndex);
            fixture.access.apply(entries, Collections.singleton(entries.get(0).key()));
            fixture.tabs.doLayout();
            assertEquals(4, fixture.tabs.getTabCount());
            assertEquals(firstIndex, fixture.tabs.indexOfComponent(first));
            assertEquals(secondIndex, fixture.tabs.indexOfComponent(second));
            assertEquals(-1, fixture.tabs.getSelectedIndex());
            assertFalse(fixture.tabs.isEnabledAt(firstIndex));
            Rectangle hidden = fixture.tabs.getBoundsAt(firstIndex);
            assertEquals("A hidden tab must occupy no vertical space", 0, hidden.height);
            assertTrue(before.height > 0);
            Rectangle visible = fixture.tabs.getBoundsAt(secondIndex);
            assertTrue(visible.height > 0);
            assertEquals(secondIndex, fixture.tabs.indexAtLocation(visible.x + visible.width / 2, visible.y + visible.height / 2));
            fixture.access.close();
            assertSame(nativeUI, fixture.tabs.getUI());
            assertTrue(fixture.tabs.isEnabledAt(firstIndex));
            fixture.tabs.doLayout();
            assertTrue(fixture.tabs.getBoundsAt(firstIndex).height > 0);
        });
    }

    @Test
    public void stoppedPluginIsNeverReinsertedAndReplacementUsesSavedPreference() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            Component first = fixture.add("Dynamic");
            fixture.access.attach(fixture.manager);
            List<SidebarTogglePlugin.Entry> entries = fixture.access.snapshot(fixture.manager);
            String key = entries.get(0).key();
            fixture.access.apply(entries, Collections.singleton(key));
            fixture.tabs.remove(first);
            fixture.access.apply(fixture.access.snapshot(fixture.manager), Collections.singleton(key));
            assertEquals(1, fixture.tabs.getTabCount());
            Component replacement = fixture.add("Dynamic");
            entries = fixture.access.snapshot(fixture.manager);
            assertEquals(key, entries.get(0).key());
            fixture.access.apply(entries, Collections.singleton(key));
            fixture.tabs.doLayout();
            assertEquals(0, fixture.tabs.getBoundsAt(fixture.tabs.indexOfComponent(replacement)).height);
            fixture.access.close();
            assertEquals(-1, fixture.tabs.indexOfComponent(first));
            assertEquals(2, fixture.tabs.getTabCount());
        });
    }

    @Test
    public void hiddenTabsDoNotLeaveBlankColumnsInWrappedSidebar() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            for (int index = 0; index < 30; index++) { fixture.add("Icon " + index); }
            fixture.access.attach(fixture.manager);
            fixture.tabs.doLayout();
            int before = fixture.tabs.getTabRunCount();
            List<SidebarTogglePlugin.Entry> entries = fixture.access.snapshot(fixture.manager);
            Set<String> hidden = new HashSet<>();
            for (SidebarTogglePlugin.Entry entry : entries) { hidden.add(entry.key()); }
            fixture.access.apply(entries, hidden);
            fixture.tabs.doLayout();
            assertTrue(before > 1);
            assertEquals(1, fixture.tabs.getTabRunCount());
            fixture.access.apply(entries, Collections.emptySet());
            fixture.tabs.doLayout();
            assertEquals(before, fixture.tabs.getTabRunCount());
            fixture.access.close();
        });
    }

    @Test
    public void restoresInitiallyDisabledTabsAndDoesNotReplaceAnotherPluginsUi() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            Component component = fixture.add("Disabled");
            fixture.tabs.setEnabledAt(1, false);
            fixture.access.attach(fixture.manager);
            List<SidebarTogglePlugin.Entry> entries = fixture.access.snapshot(fixture.manager);
            fixture.access.apply(entries, Collections.singleton(entries.get(0).key()));
            fixture.access.apply(entries, Collections.emptySet());
            assertFalse(fixture.tabs.isEnabledAt(fixture.tabs.indexOfComponent(component)));
            RuneLiteTabbedPaneUI other = new RuneLiteTabbedPaneUI();
            fixture.tabs.setUI(other);
            fixture.access.close();
            assertSame(other, fixture.tabs.getUI());
        });
    }

    @Test
    public void hidesScopedToolbarActionsWithoutGapsAndRestoresLayout() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            JPanel toolbar = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
            JButton first = addButton(toolbar, "Action one");
            JButton second = addButton(toolbar, "Action two");
            addButton(toolbar, "Close sidebar");
            ToolbarAccess access = new ToolbarAccess(component -> component == toolbar);
            java.awt.LayoutManager original = toolbar.getLayout();
            fixture.tabs.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT, toolbar);
            assertTrue(access.attach(fixture.manager));
            List<SidebarTogglePlugin.Entry> entries = access.snapshot(fixture.manager);
            assertEquals(2, entries.size());
            int before = toolbar.getPreferredSize().height;
            access.apply(entries, Collections.singleton(entries.get(0).key()));
            assertFalse(first.isVisible());
            assertTrue(second.isVisible());
            assertTrue(toolbar.getPreferredSize().height < before);
            toolbar.setSize(toolbar.getPreferredSize()); toolbar.doLayout();
            assertEquals(0, first.getHeight());
            assertEquals(0, second.getY());
            access.apply(entries, Collections.emptySet());
            assertSame(original, toolbar.getLayout());
            assertTrue(first.isVisible());
            assertEquals(before, toolbar.getPreferredSize().height);
            access.apply(entries, Collections.singleton(entries.get(0).key()));
            toolbar.remove(first);
            access.apply(access.snapshot(fixture.manager), Collections.singleton(entries.get(0).key()));
            access.close();
            assertNull(first.getParent());
            assertTrue(first.isVisible());
            assertSame(original, toolbar.getLayout());
        });
    }

    @Test
    public void visualReorderKeepsNativeIndicesSelectionAndHitTargets() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            Component first = fixture.add("First"), second = fixture.add("Second");
            Component config = fixture.add("Configuration");
            fixture.access.attach(fixture.manager);
            List<SidebarTogglePlugin.Entry> nativeEntries = fixture.access.snapshot(fixture.manager);
            fixture.tabs.doLayout();
            Rectangle firstSlot = new Rectangle(fixture.tabs.getBoundsAt(1));
            Rectangle configSlot = new Rectangle(fixture.tabs.getBoundsAt(3));
            fixture.tabs.setSelectedComponent(second);
            fixture.access.apply(java.util.Arrays.asList(nativeEntries.get(1), nativeEntries.get(0)), Collections.emptySet(), true);
            fixture.tabs.doLayout();
            assertEquals(1, fixture.tabs.indexOfComponent(first));
            assertEquals(2, fixture.tabs.indexOfComponent(second));
            assertSame(second, fixture.tabs.getSelectedComponent());
            assertEquals(firstSlot, fixture.tabs.getBoundsAt(2));
            assertEquals(configSlot, fixture.tabs.getBoundsAt(fixture.tabs.indexOfComponent(config)));
            Rectangle moved = fixture.tabs.getBoundsAt(2);
            assertEquals(2, fixture.tabs.indexAtLocation(moved.x + moved.width / 2, moved.y + moved.height / 2));
            fixture.access.apply(nativeEntries, Collections.emptySet(), false);
            fixture.tabs.doLayout();
            assertEquals(firstSlot, fixture.tabs.getBoundsAt(1));
            fixture.access.close();
        });
    }

    @Test
    public void visualOrderWorksAcrossWrappedColumnsWithCorrectHitTargets() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            for (int index = 0; index < 25; index++) { fixture.add("Icon " + index); }
            fixture.access.attach(fixture.manager);
            fixture.tabs.doLayout();
            List<SidebarTogglePlugin.Entry> entries = fixture.access.snapshot(fixture.manager);
            java.util.List<Rectangle> slots = new java.util.ArrayList<>();
            for (SidebarTogglePlugin.Entry entry : entries) { slots.add(new Rectangle(fixture.tabs.getBoundsAt(fixture.tabs.indexOfComponent(entry.component)))); }
            int runs = fixture.tabs.getTabRunCount();
            java.util.Collections.reverse(entries);
            fixture.access.apply(entries, Collections.emptySet(), true);
            fixture.tabs.doLayout();
            assertEquals(runs, fixture.tabs.getTabRunCount());
            for (int position = 0; position < entries.size(); position++)
            {
                int nativeIndex = fixture.tabs.indexOfComponent(entries.get(position).component);
                Rectangle bounds = fixture.tabs.getBoundsAt(nativeIndex);
                assertEquals(slots.get(position), bounds);
                assertEquals(nativeIndex, fixture.tabs.indexAtLocation(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2));
            }
            fixture.access.close();
        });
    }

    @Test
    public void keyboardNavigationFollowsVisualOrderAndSkipsHiddenTabs() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            Component first = fixture.add("First"), second = fixture.add("Second"), third = fixture.add("Third");
            fixture.access.attach(fixture.manager);
            List<SidebarTogglePlugin.Entry> entries = fixture.access.snapshot(fixture.manager);
            List<SidebarTogglePlugin.Entry> arranged = java.util.Arrays.asList(entries.get(2), entries.get(0), entries.get(1));
            fixture.access.apply(arranged, Collections.emptySet(), true);
            fixture.tabs.setSelectedComponent(third);
            ((HiddenTabsUI) fixture.tabs.getUI()).navigateSelectedTab(javax.swing.SwingConstants.SOUTH);
            assertSame(first, fixture.tabs.getSelectedComponent());
            fixture.access.apply(arranged, Collections.singleton(entries.get(0).key()), true);
            fixture.tabs.setSelectedComponent(third);
            ((HiddenTabsUI) fixture.tabs.getUI()).navigateSelectedTab(javax.swing.SwingConstants.SOUTH);
            assertSame(second, fixture.tabs.getSelectedComponent());
            fixture.access.close();
        });
    }

    @Test
    public void toolbarReorderChangesOnlyBoundsAndResetRestoresNativeLayout() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            JPanel toolbar = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
            JButton first = addButton(toolbar, "First"), second = addButton(toolbar, "Second");
            JButton collapse = addButton(toolbar, "Close sidebar");
            java.awt.LayoutManager original = toolbar.getLayout();
            fixture.tabs.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT, toolbar);
            ToolbarAccess access = new ToolbarAccess(component -> component == toolbar);
            access.attach(fixture.manager);
            List<SidebarTogglePlugin.Entry> entries = access.snapshot(fixture.manager);
            access.apply(java.util.Arrays.asList(entries.get(1), entries.get(0)), Collections.emptySet(), true);
            toolbar.setSize(toolbar.getPreferredSize()); toolbar.doLayout();
            assertTrue(second.getY() < first.getY());
            assertTrue(collapse.getY() > first.getY());
            assertSame(first, toolbar.getComponent(0));
            access.apply(entries, Collections.emptySet(), false);
            toolbar.setSize(toolbar.getPreferredSize()); toolbar.doLayout();
            assertSame(original, toolbar.getLayout());
            assertTrue(first.getY() < second.getY());
            access.close();
        });
    }

    @Test
    public void waitsForAttachmentAndNeverAttachesAnUnrelatedNestedTabbedPane() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.tabs.removeAll();
            assertFalse(fixture.access.attach(fixture.manager));
            JTabbedPane nested = new JTabbedPane();
            fixture.manager.add(nested);
            assertFalse(fixture.access.attach(fixture.manager));
        });
    }

    private static void onEdt(Runnable action) throws Exception
    {
        SwingUtilities.invokeAndWait(action);
    }

    private static JButton addButton(JPanel toolbar, String tooltip)
    {
        JButton button = new JButton();
        button.setToolTipText(tooltip);
        button.setPreferredSize(new java.awt.Dimension(23, 23));
        toolbar.add(button);
        return button;
    }

    private static final class TestPanel extends PluginPanel { }

    private static final class Fixture
    {
        final TestPanel manager = new TestPanel();
        final JTabbedPane tabs = new JTabbedPane(JTabbedPane.RIGHT);
        final ToolbarAccess access = new ToolbarAccess();
        Fixture()
        {
            tabs.putClientProperty("FlatLaf.style", "tabInsets: 2,5,2,5; variableSize: true; deselectable: true; tabHeight: 26");
            tabs.setUI(new RuneLiteTabbedPaneUI());
            tabs.setSize(300, 150);
            addTab("Sidebar Organizer", manager);
            tabs.setSelectedIndex(-1);
        }
        Component add(String tooltip)
        {
            TestPanel panel = new TestPanel();
            addTab(tooltip, panel);
            return panel.getWrappedPanel();
        }
        private void addTab(String tooltip, PluginPanel panel)
        {
            tabs.addTab(null, new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)), panel.getWrappedPanel(), tooltip);
        }
    }
}
