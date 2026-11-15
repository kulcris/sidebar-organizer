package com.sidebartoggle;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.Test;
import static org.junit.Assert.*;

public class IconOrderTest
{
	private static SidebarTogglePlugin.Entry panel(String name)
	{
		return new SidebarTogglePlugin.Entry(new JPanel(), name, name, name);
	}

	private static SidebarTogglePlugin.Entry action(String name)
	{
		return new SidebarTogglePlugin.Entry(new JButton(name), name, name, name);
	}

	private static List<String> keys(SidebarTogglePlugin.Entry... entries)
	{
		java.util.ArrayList<String> result = new java.util.ArrayList<>();
		for (SidebarTogglePlugin.Entry entry : entries) { result.add(entry.key()); }
		return result;
	}

	@Test
	public void movesBeforeTargetInBothDirectionsAndMovesToEnd()
	{
		List<SidebarTogglePlugin.Entry> entries = Arrays.asList(panel("a"), panel("b"), panel("c"));
		List<String> saved = keys(entries.toArray(new SidebarTogglePlugin.Entry[0]));
		assertEquals(Arrays.asList("b", "a", "c"), names(IconOrder.moveBefore(saved, entries, entries.get(1).key(), entries.get(0).key()), entries));
		assertEquals(Arrays.asList("b", "a", "c"), names(IconOrder.moveBefore(saved, entries, entries.get(0).key(), entries.get(2).key()), entries));
		assertEquals(Arrays.asList("a", "c", "b"), names(IconOrder.moveBefore(saved, entries, entries.get(1).key(), null), entries));
	}

	@Test
	public void filteredMoveKeepsUnlistedItemsInRelativeOrder()
	{
		List<SidebarTogglePlugin.Entry> all = Arrays.asList(panel("a"), panel("b"), panel("c"), panel("d"));
		List<SidebarTogglePlugin.Entry> visible = Arrays.asList(all.get(0), all.get(2), all.get(3));
		List<String> moved = IconOrder.moveBefore(keys(all.toArray(new SidebarTogglePlugin.Entry[0])), visible, all.get(3).key(), all.get(0).key());
		assertEquals(Arrays.asList("d", "a", "b", "c"), names(moved, all));
		assertTrue(moved.indexOf(all.get(1).key()) < moved.indexOf(all.get(2).key()));
	}

	@Test
	public void unavailableSavedKeysRemainAndNewIconsAppend()
	{
		List<SidebarTogglePlugin.Entry> live = Arrays.asList(panel("new"), panel("b"));
		List<String> saved = Arrays.asList("unknown", live.get(1).key());
		List<SidebarTogglePlugin.Entry> arranged = IconOrder.arrange(live, saved);
		assertEquals(Arrays.asList("b", "new"), labels(arranged));
		List<String> moved = IconOrder.moveBefore(saved, live, live.get(0).key(), null);
		assertTrue(moved.contains("unknown"));
		assertTrue(moved.contains(live.get(0).key()));
	}

	@Test
	public void orderKeepsSidebarPanelsAndActionsInSeparateRegions()
	{
		List<SidebarTogglePlugin.Entry> entries = Arrays.asList(panel("p1"), action("a1"), panel("p2"), action("a2"));
		List<String> saved = keys(entries.toArray(new SidebarTogglePlugin.Entry[0]));
		assertEquals(saved, IconOrder.moveBefore(saved, entries, entries.get(2).key(), entries.get(1).key()));
		List<SidebarTogglePlugin.Entry> arranged = IconOrder.arrange(Arrays.asList(entries.get(1), entries.get(2), entries.get(3), entries.get(0)), saved);
		assertFalse(arranged.get(0).isAction());
		assertFalse(arranged.get(1).isAction());
		assertTrue(arranged.get(2).isAction());
		assertTrue(arranged.get(3).isAction());
	}

	@Test
	public void unknownTargetDoesNotChangeSavedOrder()
	{
		List<SidebarTogglePlugin.Entry> entries = Collections.singletonList(panel("a"));
		List<String> saved = Collections.singletonList(entries.get(0).key());
		assertEquals(saved, IconOrder.moveBefore(saved, entries, entries.get(0).key(), "missing"));
	}

	private static List<String> labels(List<SidebarTogglePlugin.Entry> entries)
	{
		java.util.ArrayList<String> result = new java.util.ArrayList<>();
		for (SidebarTogglePlugin.Entry entry : entries) { result.add(entry.label()); }
		return result;
	}

	private static List<String> names(List<String> orderedKeys, List<SidebarTogglePlugin.Entry> entries)
	{
		java.util.ArrayList<String> result = new java.util.ArrayList<>();
		for (String key : orderedKeys)
			for (SidebarTogglePlugin.Entry entry : entries)
				if (entry.key().equals(key)) { result.add(entry.label()); break; }
		return result;
	}
}
