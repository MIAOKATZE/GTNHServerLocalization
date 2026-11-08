# Server Localization 开发说明

维护目标是 Minecraft 1.7.10 / Forge 10.13.4.1614 / GTNH 2.9.0 RC。Gradle Wrapper 为 9.4.0，GTNH convention 为 2.0.26，构建 JVM 需要 Java 25 或更新版本；本轮构建和运行测试使用 Java 26。编译产物目标仍为 Java 8。

## 构建与验证

Windows 下在仓库根目录执行 `.\gradlew.bat build`。`check` 包含 `localizationRegressionTest`，使用 Java 26 执行语言解析、注入、格式串和聊天组件回归，不启动 Minecraft 客户端。仅运行此回归可执行 `.\gradlew.bat localizationRegressionTest`。

`gradle.properties` 的 `RELEASE_VERSION` 记录交付目标版本；实际构建版本保留 Git Tag、分支和 dirty 信息，仅去掉 Tag 的 `v` 前缀。版本常量由生成的 `Tags.VERSION` 提供，`mcmod.info` 使用相同构建版本。

提交本轮文件、打 `v<RELEASE_VERSION>` Tag 后，确认 `git status --porcelain` 为空，再执行 `.\gradlew.bat build --no-configuration-cache`。只有此时产生的当前版本、不含 dirty / 分支后缀的运行包才用于交付。常规流程无需 clean，不使用环境变量伪造版本状态。提交、Tag、推送和 Release 按各自授权执行；本地开发分支保持不变，PR 使用远端版本分支。

## 语言加载约定

- 服务端执行 preInit / postInit 双阶段注入主表与 fallback 表，客户端保留原生语言选择。
- 加载优先级见 README。只读取指定语言，所有资源 namespace 均可包含该语言。
- Minecraft `.lang` 不使用 Java Properties 解析；IC2 的 `.properties` 保留原语义。
- 普通注入遵循原版简单数值占位符规范化。已知中文语序错误只匹配坏模板修正，不能按键永久覆盖新翻译。
- `LanguageFormatCorrections` 在每轮所有加载器之后读取 `config/serverlocalization/overrides/*.json`。默认六条放在 JAR 的 JSON 资源中，仅在首次创建目录时导出；已有目录及文件由服主管理，升级不覆盖。
- 规则必须提供 locale/key/expected/replacement。文件排序后，以覆盖前的统一语言表快照匹配，后命中者优先；替换走保留原文和数值精度的双表注入。规则加载回归使用临时目录，不写工作区实际 config。
- 聊天组件解析须保留嵌套文本、兄弟节点、样式、点击和悬浮事件；未知格式失败时保留原组件。
- 下载设置超时，只在下载完整后替换本地缓存，失败不得留下会被当作有效缓存的空文件。

玩家反馈包揭示的冠军名称异常发生在实体加入世界事件内；只验证服务器启动不足以证明此问题已修复。原生测试应实际调用 Thaumcraft `EntityUtils.makeChampion` 并检查中文名称，同时分别验证普通 `String.format` 与 `ChatComponentTranslation` 的格式契约。

本项目当前没有测试维度实现或 `plan/测试维度指南.md`。临时测试探针和整包运行目录放在 `build/`，不进入发布 JAR；后台运行 nogui 服务端，不操作前台或系统鼠标。
