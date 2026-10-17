GreenChunk 2.0.0-6 — Folia / Shiroha 26.2 maintenance fork.

2.0.0-6 减少重复查询和三维显示重建：同世界同区块共享查询结果，最多缓存 8192 项、
每项从创建起有效 30 秒；失败结果不缓存，单个请求取消不会取消其他玩家的共享查询。
移动时保留仍在范围内的显示，每移动两格区块重新设置实体锚点，保证追踪范围。
默认扫描半径、刷新间隔、中文提示、权限和操作方式不变，无新增配置或运行依赖。
配置重载、世界卸载和插件卸载会清除相应缓存。若管理员在运行中改变核心的史莱姆区块
判定规则，执行 `/greenchunkreload` 可立即清缓存并刷新已开启的三维显示；
已经打开的地图仍是静态快照，需要重新打开。
相对 2.0.0-5 的八客户端隔离测试：重复开图实际后台查询减少 99.38%，
移动查询减少 99.25%，显示创建/移除各减少 44.79%。这些是特定工作量的减少比例，
不代表整服 CPU、TPS 或玩家帧率提升同样比例。实测与限制见
`integration/OPTIMIZATION-VALIDATION.md`。
从 2.0.0-5 升级可先 `/plugman unload GreenChunk`，等待卸载成功后替换 JAR，
再按实际文件名 `/plugman load GreenChunk-2.0.0-6`（保留原文件名时使用原名）。
不要同时保留两个 GreenChunk JAR。需要回滚时按相同流程换回 2.0.0-5；配置无需迁移。

默认使用简体中文：查询消息、地图标题/按钮/坐标提示、命令帮助和插件日志。
首次从配置版本 3 或更早版本升级时，将原配置按字节备份为
`config-before-zh-v4-backup.yml`，将与 1.4 / 2.0 默认英文显示文本一致的内容替换为中文。
识别颜色、粗体和序列化标签差异，包括 `Slime Maps`、`Not Slime Chunk` 及旧版查询句式。
已经使用 2.0.0-2 的配置也会自动修正，无需删除配置；旧备份不会被覆盖。
已有自定义中文/英文、物品开关、材质和扫描设置均保留；新增项自动补齐。
完成迁移后不会在每次重载时再次替换英文。命令名、权限节点和材质 ID 保持不变。

地图标记只表示核心判定的史莱姆区块，不保证当前位置能自然生成史莱姆。
仍需满足群系、维度和生成条件；沼泽地表刷怪不以此标记为准。
该说明默认出现在聊天查询、地图和开启三维显示时，可通过
`messages.spawn-notice` 修改。不会因为地形包而改变核心的区块判定。

安装：停服替换旧版 JAR，保留 `plugins/GreenChunk` 配置目录，然后正常开服。
回滚时停服恢复旧 JAR，并恢复对应配置备份。
本版目标仍为 Java 25 / Folia、用户提供的 Shiroha 26.2 核心。
该测试核心启动日志实际标识为 mizuki 26.2-DEV-28fe077；此结论仅针对该准确产物，
不代表其他 Mizuki 构建或整套生产地形包均已通过测试。

2.0.0-6 支持提供的 PlugManX 3.1.0-Beta.2 单独操作 GreenChunk：
`/plugman reload GreenChunk`、`/plugman unload GreenChunk`、
`/plugman disable GreenChunk`、`/plugman restart GreenChunk`。
卸载前自动停止接收请求，在玩家/实体所属线程清掉三维显示，等待已有区块查询结束并保存开关偏好，
完成后才交给 PlugManX 执行。清理失败或超过 20 秒会拒绝卸载，插件保持停止服务并提示完整重启。
在线玩家不用重进服，加载/启用后会按保存的偏好和权限恢复显示。
支持玩家、控制台、RCON 入口及 `/plm`、`/plugmanx:plugman` 别名。
为避免绕过清理，启用时会拦截上述操作的 `all` / `*` 批量形式，请单独指定 GreenChunk。
安装文件若命名为 `GreenChunk.jar`，卸载后用 `/plugman load GreenChunk` 加载；
其他文件名请在 load 后填写实际 JAR 文件名（不含 `.jar`），enable 使用插件名 GreenChunk。
卸载、重载或停用前自动关闭当前仍显示的 GreenChunk 地图。
按实际发送的面板身份判断，其他插件后来发送的面板保留；不按标题或“曾经打开过”判断。
关闭等待核心待发送队列和网络写入完成，避免迟到的旧地图在卸载后重新弹出。
跟踪器在卸载与断线时移除，不需要额外安装网络插件。
此桥接绑定上述核心的内部接口；若未来核心接口变化，会在启动时明确报错。
仅修改配置仍用 `/greenchunkreload`。不支持其他插件直接调用停用 API、自动文件监控重载或服务器 `/reload`。
**从 2.0.0-3 或更旧版本首次升级必须停服替换并开服**，旧插件本身没有本次清理逻辑。
本次改动不新增配置键；原有配置和中文提示保留。验证细节见 `integration/HOT-RELOAD-VALIDATION.md`。
从 2.0.0-4 升级可先用旧版支持的单独卸载流程，替换后加载新版；旧版已经打开的地图
仍需玩家手动关闭一次，因为旧实例没有面板跟踪。也可按上面的停服安装步骤更新。
2.0.0-5 的面板专项验证见 `integration/DIALOG-CLOSE-VALIDATION.md`。

Based on [MidnightTale/GreenChunk](https://github.com/MidnightTale/GreenChunk),
commit `f12de3d788f5f1fe4594247544bdded2d8a455ab` (MIT). Requires Java 25.
Built from upstream source; no classes from third-party production JARs are reused.

Shows slime chunks with dialog UI or as a private 3D in-world display.

![Slime chunk dialog](assets/screenshot_20260802_214302-region.png)

Use a slime ball by default:

- right-click to toggle the in-world display
- sneak + right-click to open the map

Commands: `/slime` reports the current chunk in chat; `/slimemap` opens the dialog;
`/slimevisual` toggles the private overlay. `/greenchunkreload` (alias `/slimereload`)
reloads settings and refreshes active overlays.

![In-world slime chunk display](assets/screenshot_20260802_214340-region.png)

Settings and messages are in `plugins/GreenChunk/config.yml`. Run
`/greenchunkreload` after editing it.

Permissions: `greenchunk.map`, `greenchunk.visual`, `greenchunk.item.map`, and
`greenchunk.reload`. `/slime` uses `greenchunk.check`. Legacy permission
`greenchunk.map.item` remains a parent of `greenchunk.item.map`, including explicit denials.

Upgrade from 1.4: stop the server, back up the plugin folder, remove the old
GreenChunk JAR and install the release JAR. Start normally; do not use `/reload`
or a plugin hot loader. The first start backs up legacy `config.yml` as
`config-v1-backup.yml`, migrates item settings and legacy messages/colors/coordinates,
and adds missing defaults with comments. Explicit modern settings and unrelated
keys are retained. In particular, `enableSlimeMapItem: false` becomes
`item.enabled: false`; commands remain available. The old inventory map is now a dialog.

Chunk checks use the server's `Chunk.isSlimeChunk()` on its region scheduler,
including custom slime seeds, all-slime overrides and Shiroha SecureSeed.
Queries asynchronously load/generate the bounded map area (up to 100 chunks for
the map, 81 for the overlay). Each player has one pending query per feature;
the service caps actual in-flight backend work at 1024 even after a caller times out.
Player UI and display creation run on the player scheduler; display removal runs
on each display's scheduler. Stale results after movement, world change, quit,
reload or toggle are discarded. Overlays are non-persistent and cleaned on quit.
Named PlugManX operations drain before disable; see the supported entry points above.
Arbitrary direct API hot-disable is outside that supported lifecycle.

Build with JDK 25: `./gradlew clean check jar sourcesJar` (Windows: `gradlew.bat`).
The Folia API version is pinned. To compile against an exact server's extracted
libraries instead, pass `-PserverLibraries=/absolute/path/to/libraries` (include
JetBrains annotations for compilation). No server libraries are shaded into the plugin.
`check` runs regression tests and scans the built JAR for the known injected-loader markers.
The optional `integration/probe` is for isolated testing only and is not bundled in releases.
