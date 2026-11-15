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
    public void hidingMiddleIconsKeepsWrappedColumnsCompactAndHitTargetsValid() throws Exception
    {
        onEdt(() -> {
            // RuneLiteLAF.properties defaults: evenlyWrapTabs=true, expandWrappedTabs=false.
            // Exercise those defaults rather than forcing a friendlier tab layout in this fixture.
            for (int height : new int[] {120, 180, 300, 600})
            {
                for (boolean reverse : new boolean[] {false, true})
                {
                    for (int selected : new int[] {-1, 0, 28, 57})
                    {
                        Fixture fixture = new Fixture();
                        fixture.tabs.setSize(300, height);
                        for (int index = 0; index < 58; index++) { fixture.add("Icon " + index); }
                        fixture.access.attach(fixture.manager);
                        List<SidebarTogglePlugin.Entry> entries = fixture.access.snapshot(fixture.manager);
                        if (reverse) { java.util.Collections.reverse(entries); }
                        if (selected >= 0) { fixture.tabs.setSelectedComponent(entries.get(selected).component); }
                        fixture.tabs.doLayout();
                        Set<String> hidden = new HashSet<>();
                        for (int index = 12; index < 46; index++) { hidden.add(entries.get(index).key()); }
                        fixture.access.apply(entries, hidden, reverse);
                        fixture.tabs.setSize(fixture.tabs.getPreferredSize().width, height);
                        fixture.tabs.doLayout();

                        java.awt.Insets insets = fixture.tabs.getInsets();
                        Rectangle paneArea = new Rectangle(insets.left, insets.top,
                            fixture.tabs.getWidth() - insets.left - insets.right,
                            fixture.tabs.getHeight() - insets.top - insets.bottom);
                        int visibleCount = 0;
                        java.util.Map<Integer, java.util.List<Rectangle>> columns = new java.util.TreeMap<>();
                        for (int index = 0; index < fixture.tabs.getTabCount(); index++)
                        {
                            Rectangle bounds = fixture.tabs.getBoundsAt(index);
                            SidebarTogglePlugin.Entry matching = null;
                            for (SidebarTogglePlugin.Entry entry : entries)
                            {
                                if (entry.component == fixture.tabs.getComponentAt(index)) { matching = entry; break; }
                            }
                            if (matching != null && hidden.contains(matching.key()))
                            {
                                assertEquals("hidden tab height", 0, bounds.height);
                                continue;
                            }
                            visibleCount++;
                            columns.computeIfAbsent(bounds.x, unused -> new java.util.ArrayList<>()).add(new Rectangle(bounds));
                            assertTrue("visible tab has positive bounds: " + bounds, bounds.width > 0 && bounds.height > 0);
                            assertTrue("visible tab stays inside pane: " + bounds + " area=" + paneArea, paneArea.contains(bounds));
                            assertEquals("hit target resolves to its native index", index,
                                fixture.tabs.indexAtLocation(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2));
                            for (int other = index + 1; other < fixture.tabs.getTabCount(); other++)
                            {
                                Rectangle otherBounds = fixture.tabs.getBoundsAt(other);
                                if (otherBounds.width > 0 && otherBounds.height > 0)
                                {
                                    assertFalse("visible tabs overlap: " + bounds + " / " + otherBounds,
                                        bounds.intersects(otherBounds));
                                }
                            }
                        }
                        assertEquals(25, visibleCount);
                        assertTrue("remaining tabs should occupy wrapped columns", fixture.tabs.getTabRunCount() > 1);
                        int lastColumn = columns.keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
                        for (java.util.Map.Entry<Integer, java.util.List<Rectangle>> column : columns.entrySet())
                        {
                            java.util.List<Rectangle> cells = column.getValue();
                            cells.sort(java.util.Comparator.comparingInt(cell -> cell.y));
                            for (int row = 1; row < cells.size(); row++)
                            {
                                assertEquals("no gaps inside a column", cells.get(row - 1).y + cells.get(row - 1).height, cells.get(row).y);
                            }
                            Rectangle last = cells.get(cells.size() - 1);
                            if (column.getKey() != lastColumn)
                            {
                                assertTrue("earlier columns fill before wrapping", last.y + 2 * last.height > paneArea.y + paneArea.height);
                            }
                        }
                        fixture.access.close();
                    }
                }
            }
        });
    }

    @Test
    public void reorderedSingleColumnDoesNotLetTrailingToolbarCoverIcons() throws Exception
    {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.tabs.setSize(300, 600);
            for (int index = 0; index < 18; index++) { fixture.add("Icon " + index); }
            JPanel toolbar = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
            addButton(toolbar, "Action"); addButton(toolbar, "Close sidebar");
            JPanel trailing = new JPanel(new java.awt.BorderLayout());
            trailing.add(toolbar, java.awt.BorderLayout.SOUTH);
            fixture.tabs.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT, trailing);
            ToolbarAccess access = new ToolbarAccess(component -> component == toolbar);
            access.attach(fixture.manager);
            List<SidebarTogglePlugin.Entry> entries = access.snapshot(fixture.manager);
            Set<String> hidden = new HashSet<>();
            for (int index = 4; index < 13; index++) { hidden.add(entries.get(index).key()); }
            java.util.Collections.reverse(entries.subList(0, 18));
            access.apply(entries, hidden, true);
            fixture.tabs.setSize(fixture.tabs.getPreferredSize().width, 600);
            fixture.tabs.doLayout();
            assertEquals(1, fixture.tabs.getTabRunCount());
            java.awt.Container host = trailing.getParent();
            Rectangle trailerBounds = SwingUtilities.convertRectangle(host.getParent(), host.getBounds(), fixture.tabs);
            assertTrue(trailerBounds.height >= toolbar.getPreferredSize().height);
            for (int index = 0; index < fixture.tabs.getTabCount(); index++)
            {
                Rectangle bounds = fixture.tabs.getBoundsAt(index);
                if (bounds.height > 0)
                {
                    assertFalse("toolbar allocation covers reordered icons: " + trailerBounds + " / " + bounds,
                        trailerBounds.intersects(bounds));
                }
            }
            access.close();
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
