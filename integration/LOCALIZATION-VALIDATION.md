# GreenChunk 2.0.0-3 中文化补修验证

2.0.0-3：旧版 1.4 的内置配置含 `Slime Maps`、`Not Slime Chunk` 和
`You are not in a slime chunk (&e%d&7,&e%d&c)!` 等文本。
2.0.0-2 只匹配 2.0 的完整 MiniMessage 默认串，且配置版本 3 会跳过迁移，导致旧英文保留。

修复：配置版本升至 4，对已知默认英文按可见文本比较，忽略颜色/粗体序列化差异；
只匹配完整句式，保留不同的自定义文本。迁移前按字节保存
`config-before-zh-v4-backup.yml`，不会覆盖先前备份。

- 12 项测试通过（11 项配置测试、1 项查询容量测试）。新增复现测试先失败后通过。
- 干净构建 `clean check jar sourcesJar`、项目 JAR 检查及独立 JAR 检查通过。
- 在原隔离 Shiroha 26.2 中放入配置版本 3、截图对应的英文文本及不同颜色/粗体标签。
- 启动后升级为配置版本 4；备份与输入字节一致。
- 实际协议客户端验证聊天查询、地图标题、非史莱姆区块标签全部中文，地图包中无这组旧英文；重载通过。
- 证据：审计目录 `zh-v4-build.log`、`screenshot-english-v3-input.yml`、
  `runtime-zh-v4.log`、`zh-v4-client-evidence.json`。没有操作生产服务器。

以下为上一版验证记录。

## GreenChunk 2.0.0-2 中文化验证

日期：2026-10-17（Asia/Tokyo）。基于本仓库 2.0.0-1，目标 Java 25 / Shiroha 26.2。

变更：默认配置消息、地图坐标提示、命令描述、命令权限拒绝回退消息和插件日志使用中文。
查询、地图、开启三维显示时附带可配置的刷怪条件说明；区块判定和调度逻辑不变。

配置版本升至 3。升级前保存 `config-before-zh-backup.yml`，只替换与旧默认英文
完全相同的值；保留自定义消息及其他配置。新键及注释自动补齐，迁移只执行一次。
普通客户端无权限的命令仍受核心命令树控制；不能将核心的“未知命令”文本当成插件可翻译文本。

构建命令：`gradlew.bat clean check jar sourcesJar --init-script ../ascii-build.init.gradle --no-daemon`。
使用 Gradle Wrapper 9.1.0、Java 25；外部 init script 仅将构建目录设为
`D:/CodexBuild/GreenChunk-20261017`，避免 Windows Unicode 路径的测试进程问题。

- 9 项测试通过：8 项配置迁移/格式测试及 1 项原有查询容量测试。
- 新增 3 项回归测试先在旧实现上失败，实施后通过。
- 项目 `verifyPluginJar` 及独立 `verify-plugin-jar.py` 均通过。
- 复用本地隔离 Shiroha 26.2-DEV-28fe077，2 个 Folia tick 线程，监听 127.0.0.1。
- 用旧版英文默认配置启动：自动迁移为中文和配置版本 3，备份与原文件逐字节一致。
- 实际协议 776 客户端执行 `/slime`、`/slimemap`、`/slimevisual`、
  `/greenchunkreload`、`/help slime`：核对中文消息、地图标题/按钮/坐标提示、刷怪说明及命令帮助。
- 三维显示开关及正常重载通过；原生史莱姆区块查询对照 25/25 一致。
- 测试服正常关闭，未修改生产文件或启动生产服务器。

证据保存在工作区审计目录：`runtime-localization.log`、`localization-client-evidence.json`，
以及本次 Gradle XML 测试报告。客户端验证检查实际网络包中的文本，未做图形客户端截图检查。
未宣称通过 Mizuki、整套生产地形数据包或多人压力测试；上一版本的测试结果见 `VALIDATION.md`。
