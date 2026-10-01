<h1 align="center">Server Localization</h1>
<p align="center"><strong><em>Server-side Localization Mod for Minecraft 1.7.10 / GTNH</em></strong><br><strong><em>Minecraft 1.7.10 / GTNH 服务端语言本地化模组</em></strong></p>

A Minecraft 1.7.10 server-side mod that lets server administrators set the display language on the server side, supporting vanilla and mod translations.

一个 Minecraft 1.7.10 服务端模组，允许服务器管理员设置服务端显示语言，支持原版与模组翻译。

> [!NOTE]
> This is an unofficial mod. Please avoid discussing this mod in official GTNH forums.
> 这是一个非官方模组，讨论此模组时请注意场合。

## Downloads & Requirements / 下载与版本需求

| GTNH                | Server Localization | Maintenance / 维护 |
| ------------------- | ------------------- | :----------------: |
| 2.9.0               | 1.1.0+              |         ✔️         |
| 2.8.4               | [1.0](https://github.com/JiMo258/MinecraftServerLocalization-1.7.10) |  原仓库 / Original  |

- Java 8 / Java 17+ / Java 21 均可运行构建产物。
- 产物同时兼容 Java 8 与 Java 17+ 服务端。

## Features / 功能特性

- **Vanilla translations / 原版翻译**：Download and inject Minecraft official language packs.
- **Mod translations / 模组翻译**：Extract translations from loaded mod jars.
- **External translations / 外部翻译**：Load external `.lang` files from txloader directories.
- **GregTech config translations / GT 配置翻译**：Parse `GregTech_{lang}.lang` files in server root / `config/GTNewHorizons` / `config`.
- **IC2 special handling / IC2 特殊处理**：Load IC2 localization reflectively when IC2 is present.
- **UTF-8 encoding / UTF-8 编码**：Full support for Chinese and other multibyte languages.
- **Robust reflection / 健壮反射**：`StringTranslateHelper` caches reflection fields and falls back by type.

## Installation / 安装方法

1. Download the latest `serverlocalization-1.1.0.jar`.
2. Place it into the server's `mods` folder.
3. Restart the server. The config file will be generated automatically.

## Configuration / 配置说明

File / 文件：`config/serverlocalization.cfg`

```ini
general {
    # Server language / 服务器语言 [default: zh_CN]
    S:lang=zh_CN
}
```

## Translation Load Order / 翻译加载顺序

Later loaders override earlier ones / 后加载的会覆盖先加载的：

1. VanillaChanger — Minecraft official language pack
2. OtherModChanger — translations from mod jars
3. TxLoaderChanger — `config/txloader/load/` and `config/txloader/forceload/`
4. GTNHLocalization — `GregTech_{lang}.lang`
5. IC2LangChanger — IC2 localization (if IC2 is loaded)

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
