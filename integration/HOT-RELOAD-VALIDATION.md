# GreenChunk 2.0.0-4 热加载验证

日期：2026-10-17；Java 25；Gradle 9.1.0；Folia API 26.2.build.6-beta。

准确测试对象：

- 用户提供的 Shiroha 26.2 bundler 所含核心，启动日志标识 mizuki 26.2-DEV-28fe077。
  展开核心 SHA256：`7fe4526f202c0781de4b3a50027d7e32d903ffc4072973c68bc19401f9623341`。
- PlugManX-3.1.0-Beta.2.jar SHA256：`15a978faaf3cd48166743aa67d31132c566844b8c583cdb7cbb526932abe429c`。
- 最终 GreenChunk JAR SHA256：`981f58b66b81f797b2db9653f467d363702f448feda46870a8674b8c71391f0a`。

修复路径：HotReloadSupport 在准确 PlugManX 命令停用插件前排空工作。
CommandMap / PluginIdentifiableCommand 同时识别 Bukkit 与该核心的 Paper 命令包装，
按实际命令及操作权限检查；玩家、控制台、RemoteServerCommandEvent 各自覆盖。
GreenChunkPlugin 先停止新请求，等待在线玩家所属线程的屏障，再等待显示实体移除、
SlimeChunkService 实际后端查询完成和 VisualizationStateStore 最后一次保存。
ChunkVisualizationService 在加载时恢复在线玩家显示，保存失败阻止停用。
SlimeMapService 不再注册服务端点击回调，地图采用可由客户端关闭的静态快照。

验证：

- `gradlew.bat clean check jar sourcesJar --init-script ../ascii-build.init.gradle --no-daemon` 成功。
  init script 只把 build 目录设为 `D:/CodexBuild/GreenChunk-20261017`，规避本机 Unicode 路径问题。
- 18 项测试通过：11 项配置、2 项查询容量/排空、5 项 owner 回调成功/失败/退休终态。
- Gradle 已知注入标记检查和独立 `verify-plugin-jar.py` 均通过。
- 隔离核心两个 Folia tick 线程，两个真实协议 776 测试客户端；最终 JAR 与被测 JAR 字节一致。
- 玩家显示 6 个 BlockDisplay，旁观客户端 0 个；卸载后旧 ID 全部消失；加载恢复 6 个新 ID。
- 玩家 `plugman reload`、`plm restart`、`plugmanx:plugman reload`，控制台带前导 `/` 的 reload，
  disable/enable 和真实 TCP RCON reload 全部通过；每次恢复不保留旧 ID，旁观者始终不可见。
- `all` 和 `*` 批量操作被拒绝，现有显示 ID 不变。
- 取消 OP 后无权限 unload 不改变显示；显式 `plugman.help=false`、`plugman.unload=true`
  仍先清理再卸载，证明 Paper 路径不会因缺少 help 权限绕过清理。
- 地图网络包含静态 `greenchunk:close` 动作；重载后的中文查询和显示开关正常。
- 独占锁住偏好文件，实际保存失败：清理后显示 6 → 0，卸载未执行；再次卸载被拒绝，
  toggle 不再生成显示。完整重启后保存的开启偏好恢复为 6 个显示。
- 最终核心原生判定对照每次 25/25 一致。

测试中发现并修复过 Paper 命令类型不匹配：早期版本跳过拦截，卸载留下显示，
最终版本重新启动隔离服并跑完全部断言。保存失败测试首个取日志步骤遇到 Windows 文件共享限制，
改为可共享读取后核对实际异常、未停用和客户端实体结果，未把测试脚本错误算作插件失败。

原始证据位于工作区审计目录 `.codex-audit-greenchunk-20261017`：
`runtime-hotreload-final.log`、`hotreload-client-evidence.json`、`hotreload-failure-log.txt`、
`failure-before.json`、`failure-after.json`、`runtime-hotreload-recovery.log`、`recovery-client-evidence.json`。
`integration/probe` 仅用于隔离测试，不打入插件 JAR。

边界：首次由旧版本升级必须停服替换；只承诺上述指定 PlugManX 的命令入口。
不承诺第三方直接调用插件管理 API、自动文件监控重载、服务器 `/reload`，或类加载器即时回收。
20 秒超时有代码保护，本次没有强制制造整整 20 秒的在途查询。
未做大量玩家压力、图形客户端人工验收、整套生产地形包测试或生产部署。
已打开地图保留静态快照，避免误关其他插件的窗口；关闭动作不持有旧插件回调。
