# RuneLite requirements review

Reviewed 2026-11-15. **The known reflection violation has been removed. Plugin Hub approval is still pending; no submission has been made.** Automated tests are not evidence of RuneLite approval or a substitute for the user's live test.

## Agent-direction audit

Inspected complete, non-truncated recursive Git trees on 2026-11-15:

| Repository | Master snapshot | Agent directions found |
| --- | --- | --- |
| `runelite/runelite` | `bb0fa42b3d9329ffe31115f1f20042c455def2e3` | No `AGENTS.md`, `CLAUDE.md`, Copilot instruction file, or equivalent published agent rules found. `.github/CONTRIBUTING.md` links to the Developer Guide. |
| `runelite/plugin-hub` | `96b2d2a1bed8c53f239e94c4382d8417c9c3ce3e` | No separate agent instruction files found. Checked README and build workflow; the workflow uses the official tooling v5 bundle. |
| `runelite/example-plugin` | `46e14f2c448fab6cbc599df1015b9631dcf1fc52` | `AGENTS.md`; `CLAUDE.md` contains only `@AGENTS.md`. |
| `runelite/plugin-hub-tooling` | `034eaffd5bce7bb678d35287b547874f3dba6b3b` | `templateplugin/AGENTS.md` and redirecting `CLAUDE.md`. The AGENTS file has the same Git blob hash as the example plugin's copy: `81ce6ae8e878f05756fd927605f38542d31eb498`. |

Also reviewed the tooling's [disallowed API list](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/disallowed-apis.txt) and [standard build template](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/standard-build.gradle). The current plugin does not use the listed account/session APIs, obsolete widget constants, or prohibited custom HTTP/JSON constructors. No additional blocking code issue was identified by this audit; it was not an official Hub packager run or review.

The agent guidance requires Java 11, no reflection/native/process/code-loading bypasses, nonblocking lifecycle methods, cleanup, config migrations, and manual live testing by the user. The local `AGENTS.md` now records the relevant requirements and upstream links. A public repository search does not reveal private review-bot prompts or permissions; no claim is made about those.

Manual verification remains pending: hide/restore several icons; toggle the manager's own icon from its actual RuneLite gear settings; disable/re-enable another plugin while its icon is hidden; disable this manager and confirm native icons/layout return. No RuneScape UI automation will be used for that verification.

## Public API implementation

The missing `ClientToolbar` enumeration method does not make a no-reflection implementation impossible. The plugin now uses the existing public component hierarchy:

1. Temporarily register its own navigation button through `ClientToolbar.addNavigation()`.
2. Start at `PluginPanel.getWrappedPanel()` and follow public parent accessors to the enclosing `JTabbedPane`.
3. Enumerate attached tab components and tooltips using public Swing methods. Discover action buttons only within the native toolbar under the sidebar's trailing component or root menu bar.
4. Extend public `RuneLiteTabbedPaneUI`. Hidden tabs have zero width/height, are not painted or hit-tested, and are disabled for keyboard traversal. Their content components and RuneLite's registration/index order remain intact.
5. Restore the previous UI delegate, enabled states, button visibility and toolbar layout on shutdown. Never reinsert an item removed by its originating plugin.

Drag reordering changes layout rectangles through the public/protected tab UI layout hooks, not the underlying tab/component list. This preserves RuneLite's native navigation indices and panel ownership. Toolbar reordering similarly changes layout bounds without removing or reinserting buttons. User drag gestures are observed by ordinary mouse listeners on list grips; no mouse/keyboard events are generated, no dragboard serialization is used, and no reflection or code loading is involved. Order is saved separately under the existing config group's new `order` key, so visibility choices remain unchanged. The two native regions have separate order groups; Configuration and manager icons remain anchored.

`ToolbarAccess`, `HiddenTabsUI`, and ownership discovery contain no reflective field access, reflective invocation, private-field access, Unsafe, native code, code loading, or input injection. The former ownership-inspection implementation was removed entirely. A component's public runtime class name is used as a stable identity or to narrow toolbar discovery; no declared members or private state are inspected.

The own-icon setting remains in the plugin's actual RuneLite gear configuration. RuneLite's Configuration icon stays available even when the manager's own icon is hidden. Normal plugin lifecycle changes remove/add native components naturally, so unknown ownership no longer causes stale icons to be resurrected.

## Requirements checked

| Area | Result |
| --- | --- |
| Forbidden reflection | Removed from production and test source; no private RuneLite state is read. |
| Java compatibility | Java 11 compilation target. |
| Development version | Defaults to `latest.release`; validated against RuneLite 1.13.6. |
| Packaging | Explicit `build=standard`; metadata, plugin class and config group are named for this plugin. |
| License | BSD 2-Clause. |
| Production dependencies | Only RuneLite and its existing transitive libraries; no added runtime dependencies. |
| Configuration | Normal RuneLite `ConfigItem`; existing group/keys preserved, older hidden-icon identifiers migrated by matching tooltips. |
| Swing lifecycle | UI work runs on EDT; startup/shutdown do not block waiting for EDT; polling timer stops on shutdown. |
| Network/files/credentials | No custom requests, credential access, native code, processes, or direct file I/O. Settings use `ConfigManager`. |
| Game input | No synthetic keyboard/mouse input or gameplay automation. |
| Restoration | Retains native registration and indices; restores changed properties and the previous UI delegate. |
| Verification | Headless tests exercise real RuneLite tab UI, layout wrapping, native grid layout, migration, settings, removal/replacement, order persistence/reset, reordered click targets/keyboard traversal and restoration. Live RuneLite interaction was not automated. |
| Hub acceptance | Not reviewed or approved. Shared sidebar UI customization must still be assessed by maintainers. |

## Compatibility limits to disclose

- This depends on RuneLite's public `RuneLiteTabbedPaneUI` subclass hooks and its current sidebar component structure. Public access does not make that structure a guaranteed stable extension contract.
- Action discovery recognizes the native toolbar's runtime class name. A redesign or rename requires updating that selector. The implementation does not access that class's private fields or methods.
- A plugin's icon can only be discovered after its tab/button is attached. Plugins which create icons after login appear then.
- Labels come from icon tooltips, not a private plugin-to-button map. Multiple indistinguishable tooltips are separated by ordinal; rearranging duplicate items may affect matching.
- UI replacement by another plugin is respected during shutdown. An unsupported UI delegate produces an error and restores changed properties.

References: [official development rules](https://github.com/runelite/example-plugin/blob/master/AGENTS.md), [Hub setup/build/licensing](https://github.com/runelite/plugin-hub/blob/master/README.md), [review process](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review), [public RuneLite tab UI source](https://github.com/runelite/runelite/blob/runelite-parent-1.13.6/runelite-client/src/main/java/net/runelite/client/ui/laf/RuneLiteTabbedPaneUI.java), [PluginPanel API](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/ui/PluginPanel.html).
