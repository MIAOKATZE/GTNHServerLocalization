<h1 align="center">Server Localization</h1>
<p align="center"><strong><em>Server-side Localization Mod for Minecraft 1.7.10 / GTNH</em></strong><br><strong><em>Minecraft 1.7.10 / GTNH 服务端语言本地化模组</em></strong></p>

A Minecraft 1.7.10 server-side mod that lets server administrators set the display language on the server side, supporting vanilla and mod translations.

一个 Minecraft 1.7.10 服务端模组，允许服务器管理员设置服务端显示语言，支持原版与模组翻译。

> [!NOTE]
> This is an unofficial mod. Please avoid discussing this mod in official GTNH forums.
> 这是一个非官方模组，讨论此模组时请注意场合。

## Downloads & Requirements / 下载与版本需求

| GTNH                | Server Localization 版本范围 | Maintenance / 维护 |
| ------------------- | ------------------- | :----------------: |
| 2.9.0 RC            | 1.2.1 ~ +           |         ✔️         |
| 2.8.4               | [1.0](https://github.com/JiMo258/MinecraftServerLocalization-1.7.10) |  原仓库 / Original  |

- 当前维护目标为 GTNH 2.9.0 RC，运行验证使用 Java 26；产物目标字节码为 Java 8。
- 本模组仅在专用服务端注入语言表，不接管客户端的语言选择。无需安装 MIAOKATZE Core。

## Features / 功能特性

- **Vanilla translations / 原版翻译**：Download and inject Minecraft official language packs.
- **Mod translations / 模组翻译**：Extract translations from loaded mod jars.
- **External translations / 外部翻译**：Load external `.lang` files from txloader directories.
- **GregTech config translations / GT 配置翻译**：Parse `GregTech_{lang}.lang` files in server root / `config/GTNewHorizons` / `config`.
- **IC2 special handling / IC2 特殊处理**：Load IC2 localization reflectively when IC2 is present.
- **UTF-8 encoding / UTF-8 编码**：Full support for Chinese and other multibyte languages.
- **Robust reflection / 健壮反射**：`StringTranslateHelper` caches reflection fields and falls back by type.

## Installation / 安装方法

1. Download `serverlocalization-1.2.1.jar` (replace older copies).
2. Place it into the server's `mods` folder.
3. Restart the server. The config file will be generated automatically.

## Configuration / 配置说明

File / 文件：`config/serverlocalization.cfg`

```ini
general {
    # Server language / 服务器语言 [default: zh_CN]
    S:lang=zh_CN
    # Resolve translated chat components before sending to clients
    B:resolveChatTranslations=true
}
```

## Translation Load Order / 翻译加载顺序

Later loaders override earlier ones / 后加载的会覆盖先加载的：

1. VanillaChanger — Minecraft official language pack
2. OtherModChanger — translations from mod jars
3. TxLoaderChanger — `config/txloader/load/` and `config/txloader/forceload/`
4. GTNHLocalization — `GregTech_{lang}.lang`
5. IC2LangChanger — IC2 localization (if IC2 is loaded)

上述加载在 preInit / postInit 各执行一次。只读取指定语言，不以其他语言文件作为兜底；Minecraft `.lang` 按首个 `=` 分隔并保留反斜杠、冒号和后续等号，IC2 `.properties` 仍按 Properties 规则解析。

### Format safety / 格式串安全

1.2.1 修复了语言注入绕过 Minecraft 数值占位符规范化的问题。原版规则会将 `%d`、`%N$d`、`%.2f` 等简单数值占位符转成字符串占位符，避免 Thaumcraft 冠军/BOSS 名称和命令聊天因实参类型不匹配而中断。此转换可能移除简单浮点占位符的小数精度约束；`%,d` 等带标志的格式不在该通用转换范围内。

对已知六条中文语序错误（Blood Magic 三条、Salis Arcana 一条、GT5U 虫洞两条）作定点参数索引修正。虫洞模板保留千分位与效率精度。修正只作用于内存中的已知错误模板，不改写磁盘汉化包，不覆盖已经修好的新模板。聊天解析保留嵌套组件的样式和交互事件。

反馈中另列的 18 条数字格式键已核对实际整包调用路径：Blood Magic 的千分位提示使用数值实参先格式化，保持原样；CoFH 传送命令已先把小数参数格式化到两位，模板转 `%s` 后保留该显示；Gravitation Suite Neo 两条用于客户端物品提示，不由本模组服务端入口改写。

如果语言包还有未知错误，仍需在上游修正对应模板；本模组不尝试猜测任意翻译的参数语义。

## External Translation Files / 外部翻译文件

### TxLoader directories / TxLoader 目录

- `config/txloader/load/`
- `config/txloader/forceload/`

### GregTech config format / GregTech 配置格式

Search paths / 搜索路径：

- `./GregTech_zh_CN.lang`
- `./config/GTNewHorizons/GregTech_zh_CN.lang`
- `./config/GregTech_zh_CN.lang`

Supported formats / 支持格式：

```
S:"Book.How to: Modular Baubles.Name"=模块化饰品手册
S:gt.blockmachines.multimachine.supercapacitor.name=兰波顿超级电容库
```

## Tech Stack / 技术栈

- Java 8 / Minecraft 1.7.10 / Forge 10.13.4.1614
- Built with GTNH RetroFuturaGradle / 使用 GTNH RetroFuturaGradle 构建

## Acknowledgments / 致谢

This project is based on the original [MinecraftServerLocalization-1.7.10](https://github.com/JiMo258/MinecraftServerLocalization-1.7.10) by [JiMo258](https://github.com/JiMo258).  
Special thanks to the original author for the initial implementation and idea of server-side localization.

本项目基于原作者 [JiMo258](https://github.com/JiMo258) 的 [MinecraftServerLocalization-1.7.10](https://github.com/JiMo258/MinecraftServerLocalization-1.7.10) 进行二次开发与 GTNH 2.9.0 适配。感谢原作者提出并实现服务端语言本地化的思路。

## License / 许可证

See [LICENSE.txt](LICENSE.txt).
详见 LICENSE.txt。
