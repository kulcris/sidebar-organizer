# Disable Sidebar Icons

A local RuneLite plugin that discovers registered plugin sidebar icons and lets you hide them without stopping their plugins.

**No reflection or custom RuneLite build required.** Discovery uses public Swing component accessors; visibility uses a subclass of RuneLite's public tab UI. This implementation has not been submitted to or approved by the Plugin Hub. See [the requirements review](docs/RUNELITE_REQUIREMENTS.md).

## Test in IntelliJ IDEA

1. Open this folder as a project and let the Gradle import finish.
2. Select **Run RuneLite** in the run configuration dropdown and click **Run**.
3. In the launched client, click the orange **Sidebar Icons** icon. If necessary, enable **Disable Sidebar Icons** in RuneLite's plugin list.
4. Uncheck any icon to hide it; check it to restore it. **Show all** restores all managed icons.
5. Drag the grip beside an icon up or down the list to change its position. An orange line marks the drop location. The sidebar updates when you release it, and your order is saved automatically.

Reorder within **Sidebar panels** or **Toolbar actions**; RuneLite displays those groups in separate areas. Search can narrow the list while moving an item. **Reset order** restores native ordering without changing hidden-icon choices. RuneLite's Configuration icon and this manager's own icon retain their native positions.

To hide this plugin's own icon, open **Disable Sidebar Icons** settings in RuneLite's plugin list and enable **Hide own sidebar icon**. Turn that option off in the same settings to restore the management panel's icon. The option is off by default and persists across restarts.

The project is configured for the installed IntelliJ SDK **11** (Temurin 11.0.30), with a checked-in Gradle wrapper and shared run configuration. No separate Gradle installation is required.

You can also double-click `run-test-client.bat`. It selects the installed Java 11 automatically, rather than the system's Java 8. From a terminal with Java 11 selected:

```powershell
.\gradlew.bat build
.\gradlew.bat run
```

## Behavior

- Lists attached sidebar tabs and native toolbar actions, including Plugin Hub plugins, by their icon tooltips.
- Discovers new icons automatically every second; **Refresh** scans immediately.
- Supports search and plugins with multiple sidebar items.
- Saves visibility choices in RuneLite's configuration, per profile.
- Saves custom order per profile, retains positions for temporarily disabled plugins, and appends newly discovered icons. Hidden icons can also be reordered before showing them again.
- Disabling this plugin restores the native tab UI and changed button properties. Removed tabs and buttons are never reinserted.
- Its own management icon can be hidden through its RuneLite settings. RuneLite's Configuration icon and sidebar collapse control remain available for recovery.
- A plugin must be enabled and create its icon before it can be discovered. Plugins that only create a panel after login will appear then.
- Hiding a currently selected icon closes its panel; the plugin continues running. A plugin can still open its hidden panel programmatically.

## Compatibility

The development build resolves RuneLite's **latest release**, as recommended by the Hub setup guide. Last validated version: **1.13.6**. For a repeatable build, use `-PruneliteVersion=1.13.6`. The launcher uses your normal RuneLite configuration, installed Hub plugins, and selected profile. It opens another client; it does not modify your installed RuneLite executable.

RuneLite's public `ClientToolbar` API cannot enumerate other plugins' navigation buttons. Instead, `ToolbarAccess` reaches the enclosing `JTabbedPane` from this plugin's attached `PluginPanel.getWrappedPanel()` and reads its public tab/component accessors. `HiddenTabsUI` extends the public `RuneLiteTabbedPaneUI`, gives hidden tabs zero size, skips painting and hit testing, and maps icon layout slots into the saved visual order. It leaves every registered tab and component index intact. Click targets and keyboard traversal follow that visual order. All UI changes run on the Swing event thread. No private fields, reflective calls, injected input, or modified RuneLite classes are used.

Toolbar actions are discovered only inside the native toolbar in the sidebar's trailing component or root menu bar; plugin-internal buttons are excluded. Their public visibility properties and a compact layout hide them without removing them from their owner. The native toolbar's runtime class name identifies this narrow scope; this is an implementation dependency that may need updating after a RuneLite UI redesign.

Sidebar items are labeled by their tooltips, which can differ from plugin names. Duplicate labels get an ordinal suffix. Saved choices from the earlier implementation migrate by tooltip when matching icons are discovered. Normal settings stay in the existing config group.

The UI delegate is restored on shutdown. An unsupported UI delegate triggers a compatibility error and restoration rather than private access. This uses public methods but still customizes the shared sidebar; only RuneLite's maintainers can determine Plugin Hub acceptance. Live behavior must be tested by the user.

Official API references: [ClientToolbar](https://github.com/runelite/runelite/blob/runelite-parent-1.13.6/runelite-client/src/main/java/net/runelite/client/ui/ClientToolbar.java), [ClientUI](https://github.com/runelite/runelite/blob/runelite-parent-1.13.6/runelite-client/src/main/java/net/runelite/client/ui/ClientUI.java), [example plugin](https://github.com/runelite/example-plugin).

## Files

- `src/main/java/com/sidebartoggle/SidebarTogglePlugin.java`: configuration, persistence, migration, and lifecycle.
- `src/main/java/com/sidebartoggle/ToolbarAccess.java`: public component discovery, visibility, and restoration.
- `src/main/java/com/sidebartoggle/HiddenTabsUI.java`: zero-size hidden tab presentation using RuneLite's public UI subclass hooks.
- `src/main/java/com/sidebartoggle/IconOrder.java`: stable ordering, group boundaries, and drag move operations.
- `src/main/java/com/sidebartoggle/SidebarTogglePanel.java`: searchable checkbox list.
- `src/test/java/com/sidebartoggle/SidebarToggleLauncher.java`: local RuneLite entry point.
- `.run/Run RuneLite.run.xml`: shared IntelliJ run configuration.

Build output: `build/libs/disable-sidebar-icons-1.0.0.jar`. Use the launcher above to test; copying this jar into RuneLite's folders does not install it automatically.

## Manual drag test

Restart the development client to load this build. In **Sidebar Icons**, drag an icon above another, below another, and to the end of its group. Confirm the matching sidebar icons move and each opens its correct panel. Repeat with a filtered list and with a hidden icon, then restart the client and verify the order remains. Disable/re-enable an affected plugin to check its position returns. **Reset order** should restore native order without showing hidden icons. Disabling this manager should restore native layout. Live testing is pending the user's confirmation; headless tests do not simulate mouse/keyboard input.
