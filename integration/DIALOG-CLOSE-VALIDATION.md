# 2.0.0-5 卸载关闭地图验证

2026-10-17。沿用 HOT-RELOAD-VALIDATION.md 指定的核心和 PlugManX 原始 JAR。
2.0.0-4 的 SlimeMapService.close 只停止请求，没有关闭已打开的 dialog；本版补齐。

实现：DialogSessions 在玩家所属线程取得网络句柄和准确的地图 Holder；
在网络 event loop 观察实际发出的 show/clear，按对象身份识别归属。
卸载前停止新请求并等待玩家屏障，然后通过核心 Connection.runOnceConnected 加入
pendingActions 队列屏障，避免关闭超越已排队但尚未写入网络的地图。
只有最后发出的仍是自己的地图才写入原生 ClearDialog INSTANCE；
等待写入完成并移除 handler 后才允许 PlugManX 继续卸载。
断线释放会话和等待项。网络线程不调用玩家 Bukkit 状态 API。
Netty 4.2.15.Final 为核心已有依赖，仅 compileOnly，不打包、不要求额外安装插件。

验证结果：

- Java 25 / Gradle 9.1.0：`clean check jar sourcesJar --init-script ../ascii-build.init.gradle --no-daemon` 成功。
- 实际报告在 `D:/CodexBuild/GreenChunk-20261017/test-results/test`：20 tests、0 failures、0 errors。
  新增 2 项 EmbeddedChannel 测试，覆盖相同标题不同对象的归属替换、clear 和断线清理。
  仓库内部 build 目录的旧失败报告不是本次构建结果。
- 项目注入标记检查和独立 JAR 结构检查通过。
- 两个协议 776 客户端：两人打开地图，第二人随后打开测试插件的其他面板。
  三轮 unload/load：第一人每次恰好收到一个 clear，第二人没有收到 clear；卸载后无迟到 show。
- 每轮跟踪 handler 数量打开时为 1，卸载再加载后为 0，没有叠加。
- reload 自动关闭地图；玩家断线后 reload 正常；disable 关闭地图，enable 恢复插件可用。
- 故障注入：测试插件将准确核心 flushingQueue 暂时占用 5 秒，地图进入 pendingActions，
  立即请求卸载。阻塞期间未停用 GreenChunk；释放后客户端收到一份地图和一次 clear，随后卸载。
  证明卸载等待实际核心发送队列，不只等待玩家线程。
- 最终源码只读复核已检查精确核心的 Holder、pendingActions 和 Netty pipeline 实现。

证据位于审计目录：`runtime-dialog-v5-final.log`、`dialog-client-evidence.json`、
`dialog-queue-evidence.json`、`dialog-check.cjs`、`dialog-queue-check.cjs`。
测试探针首次用空按钮集合构造其他面板，被核心拒绝；修正探针后完整重跑通过，未修改插件来规避测试。
测试插件不打入正式 JAR；没有操作生产服务器。

边界：这项桥接针对提供的核心内部接口，不承诺其他版本核心；
归属判断针对服务端发送的 dialog，未验证客户端自行构造的本地页面、第三方网络包改写和大量玩家压力。
仍只支持 README 指定的 PlugManX 命令流程，不承诺绕过准备阶段的直接 API 禁用。
旧 2.0.0-4 实例已经打开的面板没有跟踪记录，升级时需要手动关闭一次或正常停服更新。
配置键、默认中文和持久化格式保持不变。
