# GreenChunk 2.0.0-1 validation — 2026-10-17

Target: the supplied Shiroha 26.2-DEV-28fe077, Java 25.0.3, protocol 776.
Bundler SHA-256: `ea152171a3b8bac185056077f9c8674b3b8fa12420ebc0c5ea279fc38eb7345e`.
Expanded core SHA-256: `7fe4526f202c0781de4b3a50027d7e32d903ffc4072973c68bc19401f9623341`.
The server was isolated, bound to loopback, and used two Folia tick threads for the final player scenarios.

## Build and regression checks

- Gradle Wrapper 9.1.0: `clean check jar sourcesJar` completed successfully.
- 6 JUnit tests passed: legacy disabled item/material settings (including attached Bukkit defaults),
  Chinese legacy colors and coordinate placeholders, modern-setting precedence/idempotency,
  missing-default comments, literal legacy markup, and backend capacity after caller timeout.
- The timeout-capacity test failed before the fix: 1100 unfinished backend calls instead of the
  limit of 1024. After the fix, completing one backend call releases exactly one slot.
- `verifyPluginJar` passed; independent ZIP/CRC, descriptor, main-class, version and Folia checks passed.
- No known injected-loader markers or remote URLClassLoader reference were found in release classes.
- Exact target API compilation was also checked using libraries extracted from the supplied bundler.
- On this Windows machine, the Gradle test worker could not load test classes from the original
  Unicode build directory. An external init script relocated only `layout.buildDirectory` to
  an ASCII directory; no production/test source was changed for that workaround.

## Runtime regressions

The unmodified upstream 2.0.0 was built from source as the baseline; the untrusted production JAR
was never executed. With a legacy `enableSlimeMapItem: false` configuration the baseline enabled
the item. Under the core's all-slime override it disagreed with native checks at 22 of 25 coordinates.

The fixed release preserved the production item disable setting and migrated messages/title/colors
and coordinate placeholders. A byte-for-byte legacy configuration backup was created before migration.
Native parity was checked at 25 coordinates (including negatives) in each of these modes:

| Core mode | Compared | Mismatches |
| --- | ---: | ---: |
| `all-chunks-are-slime-chunks: true` | 25 | 0 |
| Custom `seed-slime: 123456`, all-slime override off | 25 | 0 |
| SecureSeed enabled, version 2, fresh isolated world | 25 | 0 |

Two protocol clients exercised real login and command packets. Observations:

- `/slime` delivered the migrated Chinese result with coordinates; no dialog packet.
- `/slimemap` delivered one dialog to the requester and none to the observer.
- An explicit legacy `greenchunk.map.item` denial denied `greenchunk.item.map`.
- Radius 4 with the all-slime override produced 81 BlockDisplays for the requesting player;
  the observer received zero BlockDisplays.
- Toggle off and ten rapid toggles ended with zero client-side BlockDisplays.
- Actual async teleport to `(4096.5, -59, 4096.5)` completed; the overlay followed to chunk `(256,256)`.
- Configuration reload rebuilt an 81-display overlay without accumulating duplicates.
- Actual async teleport to the Nether completed and produced the expected 81-display overlay there.
- After quit, plugin display/task/view-state maps and pending-query set were empty.
- Startup and normal shutdown completed; final scenarios had no plugin exceptions or thread-access violations.

Early test attempts omitted movement acknowledgement or used characters that died to mobs;
their rejected teleports were excluded. Final assertions require successful teleport completion,
changed client coordinates/world packets, and the corresponding changed server view state.

## Limits and reproduction

These are protocol and server-state checks, not a graphical Minecraft screenshot review or a
large-player-count performance test. Hot unloading while the server keeps running is unsupported.
Chunk scans can asynchronously load/generate terrain; the query and backend limits are described
in the README. Replacing this plugin does not establish that a previously compromised server's
other files or running processes are clean.

`probe/GreenChunkProbe.java` is an isolated-server test plugin. Compile it with Java 25 against
the target server libraries, package its accompanying `plugin.yml`, and install it only in a test
server alongside GreenChunk. Start from a legacy config with the item disabled. It logs
`GC_RESULT` for migration/native parity; `gcprobe` exposes test state, and `gcprobe move`,
`gcprobe nether`, `gcprobe legacy` exercise the corresponding paths. Never ship the probe to production.

Release JAR SHA-256: `ec5a85072dea084498cd4f606bfa93f46546d54e03176ecc7e391f604a994277`.
