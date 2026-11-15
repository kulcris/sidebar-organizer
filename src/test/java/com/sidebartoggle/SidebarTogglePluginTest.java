package com.sidebartoggle;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Binder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteTabbedPaneUI;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SidebarTogglePluginTest
{
	@BeforeClass
	public static void lookAndFeel() throws Exception { SwingUtilities.invokeAndWait(RuneLiteLAF::setup); }

	@Test
	public void migratesMatchedLegacyTooltipAndPreservesUnknownAndModernKeys() throws Exception
	{
		String legacyMatch = legacy("old:Map:identity");
		String legacyUnknown = legacy("old:Missing:identity");
		String modern = legacy("swing-tab:some.plugin.Panel:Other:1");
		Fixture fixture = edt(() -> new Fixture(String.join(",", legacyMatch, legacyUnknown, modern), false));
		edt(() -> { fixture.plugin.start(); return null; });
		flushEdt();
		edt(() -> { fixture.plugin.scan(); return null; });

		String migrated = fixture.savedHidden.get();
		assertNotNull(migrated);
		assertTrue(java.util.Arrays.stream(migrated.split(","))
			.map(key -> new String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8))
			.anyMatch(key -> key.startsWith("swing-tab:") && key.contains(":Map:")));
		assertTrue(migrated.contains(legacyUnknown));
		assertTrue(migrated.contains(modern));
		assertFalse(migrated.contains(legacyMatch));
		assertFalse(fixture.sidebar.isEnabledAt(fixture.sidebar.indexOfComponent(fixture.target.getWrappedPanel())));
		edt(() -> { fixture.plugin.stop(); return null; });
	}

	@Test
	public void reorderedIconsPersistAcrossRestartAndResetWithoutChangingVisibility() throws Exception
	{
		Fixture fixture = edt(() -> new Fixture("", false));
		PluginPanel other = edt(() -> {
			PluginPanel panel = new PluginPanel() { };
			fixture.sidebar.addTab("Other", null, panel.getWrappedPanel(), "Other");
			fixture.sidebar.setSize(300, 300);
			fixture.plugin.start();
			return panel;
		});
		flushEdt();
		String mapKey = new SidebarTogglePlugin.Entry(fixture.target.getWrappedPanel(), "Map", "Map",
			"swing-tab:" + fixture.target.getClass().getName() + ":Map:1").key();
		String otherKey = new SidebarTogglePlugin.Entry(other.getWrappedPanel(), "Other", "Other",
			"swing-tab:" + other.getClass().getName() + ":Other:1").key();
		edt(() -> {
			fixture.plugin.moveIcon(otherKey, mapKey);
			assertEquals(otherKey + "," + mapKey, fixture.savedOrder.get());
			fixture.sidebar.doLayout();
			assertTrue(fixture.sidebar.getBoundsAt(2).y < fixture.sidebar.getBoundsAt(0).y);
			fixture.plugin.stop(); fixture.plugin.start();
			return null;
		});
		flushEdt();
		edt(() -> {
			fixture.sidebar.doLayout();
			assertTrue(fixture.sidebar.getBoundsAt(2).y < fixture.sidebar.getBoundsAt(0).y);
			fixture.savedOrder.set("");
			fixture.plugin.scan(); fixture.sidebar.doLayout();
			assertTrue(fixture.sidebar.getBoundsAt(0).y < fixture.sidebar.getBoundsAt(2).y);
			assertNull(fixture.savedHidden.get());
			fixture.plugin.stop();
			return null;
		});
	}

	@Test
	public void startupAppliesSavedOwnIconSettingAndConfigCanRestoreIt() throws Exception
	{
		Fixture fixture = edt(() -> new Fixture("", true));
		edt(() -> { fixture.plugin.start(); return null; });
		flushEdt();
		assertEquals(1, fixture.addCount);
		assertEquals(1, fixture.removeCount);

		fixture.hideOwn.set(false);
		edt(() -> { fixture.plugin.updateOwnNavigation(); return null; });
		assertEquals(2, fixture.addCount);
		assertEquals(1, fixture.removeCount);

		edt(() -> { fixture.plugin.stop(); return null; });
		assertEquals(2, fixture.removeCount);
	}

	private static final class Fixture
	{
		final JTabbedPane sidebar = new JTabbedPane(JTabbedPane.RIGHT);
		final PluginPanel target = new PluginPanel() { };
		final AtomicReference<String> savedHidden = new AtomicReference<>();
		final AtomicReference<String> savedOrder = new AtomicReference<>();
		final AtomicReference<Boolean> hideOwn;
		final TestPlugin plugin = new TestPlugin();
		int addCount;
		int removeCount;

		Fixture(String hidden, boolean ownHidden)
		{
			sidebar.setUI(new RuneLiteTabbedPaneUI());
			sidebar.addTab("Map", null, target.getWrappedPanel(), "Map");
			sidebar.addTab("Configuration", null, new JPanel(), "Configuration");
			hideOwn = new AtomicReference<>(ownHidden);
			ConfigManager config = mock(ConfigManager.class);
			when(config.getConfiguration(SidebarTogglePlugin.GROUP, "hidden")).thenAnswer(call -> savedHidden.get() == null ? hidden : savedHidden.get());
			when(config.getConfiguration(SidebarTogglePlugin.GROUP, "hideOwnSidebar")).thenAnswer(call -> Boolean.toString(hideOwn.get()));
			when(config.getConfiguration(SidebarTogglePlugin.GROUP, "order")).thenAnswer(call -> savedOrder.get());
			doAnswer(call -> { savedOrder.set((String) call.getArgument(2)); return null; })
				.when(config).setConfiguration(eq(SidebarTogglePlugin.GROUP), eq("order"), anyString());
			doAnswer(call -> { savedHidden.set((String) call.getArgument(2)); return null; })
				.when(config).setConfiguration(eq(SidebarTogglePlugin.GROUP), eq("hidden"), anyString());

			ClientToolbar toolbar = mock(ClientToolbar.class);
			doAnswer(call -> {
				addCount++;
				NavigationButton button = call.getArgument(0);
				if (button.getPanel() != null && sidebar.indexOfComponent(button.getPanel().getWrappedPanel()) < 0)
					sidebar.addTab(button.getTooltip(), null, button.getPanel().getWrappedPanel(), button.getTooltip());
				return null;
			}).when(toolbar).addNavigation(any(NavigationButton.class));
			doAnswer(call -> {
				removeCount++;
				NavigationButton button = call.getArgument(0);
				if (button != null && button.getPanel() != null) { sidebar.remove(button.getPanel().getWrappedPanel()); }
				return null;
			}).when(toolbar).removeNavigation(any(NavigationButton.class));

			Module module = (Binder binder) -> {
				binder.bind(ConfigManager.class).toInstance(config);
				binder.bind(ClientToolbar.class).toInstance(toolbar);
			};
			Injector injector = Guice.createInjector(module);
			injector.injectMembers(plugin);
		}
	}

	private static final class TestPlugin extends SidebarTogglePlugin
	{
		void start() { super.startUp(); }
		void stop() { super.shutDown(); }
	}

	private static String legacy(String decoded)
	{
		return Base64.getUrlEncoder().withoutPadding().encodeToString(decoded.getBytes(StandardCharsets.UTF_8));
	}

	private static void flushEdt() throws Exception { edt(() -> null); }
	private static <T> T edt(Callable<T> action) throws Exception
	{
		if (SwingUtilities.isEventDispatchThread()) { return action.call(); }
		Object[] result = new Object[1];
		Exception[] error = new Exception[1];
		SwingUtilities.invokeAndWait(() -> { try { result[0] = action.call(); } catch (Exception ex) { error[0] = ex; } });
		if (error[0] != null) { throw error[0]; }
		@SuppressWarnings("unchecked") T value = (T) result[0];
		return value;
	}
}
