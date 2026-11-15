# RuneLite plugin development

- NEVER use reflection when creating or modifying RuneLite plugins. This is an explicit user requirement.
- Do not inspect declared fields/methods/constructors, bypass access controls, invoke members reflectively, or substitute Unsafe, private MethodHandles, bytecode generation, or classloading tricks.
- Use public RuneLite APIs and public/protected Swing extension points. If required functionality is unavailable, explain the limitation rather than bypassing private access.
- Verify plugin source and compiled output for reflective access before delivery. Normal compiler-generated lambda bootstrap metadata is not reflective access.
- Do not control the user's desktop without warning them and obtaining their go-ahead. Terminal commands and headless tests are permitted.

## Official RuneLite guidance

Checked 2026-11-15:

- Follow https://github.com/runelite/example-plugin/blob/master/AGENTS.md and https://github.com/runelite/plugin-hub/blob/master/README.md for RuneLite work. Recheck relevant upstream guidance before substantial changes or submission.
- The example plugin's `CLAUDE.md` only redirects to `AGENTS.md`. The same agent guidance is in `runelite/plugin-hub-tooling/templateplugin/AGENTS.md`.
- Do not automate RuneScape interaction or gameplay, including through desktop tools. Headless Swing tests are allowed; only the user can confirm live plugin behavior.
- Do not describe a clean build, JVM launch, or passing unit tests as proof the plugin works in-game or is Hub-approved. Provide a specific manual test checklist and leave live verification pending until the user confirms it.
- Target Java 11; preserve the permissive license and specific config group. Preserve existing config keys or supply a migration.
- Do not block plugin startup/shutdown, sleep on threads, or run blocking network/file operations on the client thread. Cancel timers/tasks and restore changed UI properties on shutdown.
- Keep diagnostic logging low-volume. Use injected RuneLite services for HTTP/JSON if ever needed; do not add RuneLite's transitive dependencies separately.
- If file I/O is added, use RuneLite's `Filepath` utility and the required descriptor identity. Do not add custom filesystem access as a shortcut.
- Do not add native access, external processes, runtime code generation/loading, Java serialization, or synthetic game input.
- Do not commit build artifacts or add a plugin service-loader entry. New production dependencies require Hub dependency verification.
- Before submission, check https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/disallowed-apis.txt and the Hub build/review requirements. Attribute agent contributions truthfully.
