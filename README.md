# EdOpal

一个适用于 Minecraft 1.21.10 的 Fabric 模组，提供强大的客户端功能和各种实用模块。

## 功能特性

- **ClickGUI** - 交互式图形用户界面，用于管理模块
- **配置系统** - 本地配置保存/加载功能，无需云端依赖
- **命令系统** - 自定义命令，用于游戏内交互
- **视觉模块** - HUD 元素、ESP 和其他视觉增强功能
- **移动模块** - 玩家移动实用工具
- **战斗模块** - PvP 战斗增强功能

## 构建

```bash
# 构建项目
./gradlew build

# 跳过测试构建
./gradlew build -x test
```

## 使用方法

1. 将构建好的 JAR 文件放入 `.minecraft/mods` 文件夹
2. 使用 Fabric 启动器启动 Minecraft
3. 按 `rshift` 键打开 ClickGUI
4. 使用 `.c` 命令前缀进行配置操作：
   - `.c save <name>` - 保存当前配置
   - `.c load <name>` - 加载已保存的配置
   - `.c list` - 列出可用配置
   - `.c delete <name>` - 删除配置

## 许可证

本项目采用 GNU General Public License v3.0 许可证 - 详情请参阅 [LICENSE](LICENSE) 文件。

altmanager懒得修了，明天看看另一位dev修不修
