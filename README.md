# EdOpal

A Fabric mod for Minecraft 1.21.10, featuring a powerful client with various utility modules.

## Features

- **ClickGUI** - Interactive graphical user interface for managing modules
- **Config System** - Local config save/load functionality without cloud dependency
- **Command System** - Custom commands for in-game interaction
- **Visual Modules** - HUD elements, ESP, and other visual enhancements
- **Movement Modules** - Player movement utilities
- **Combat Modules** - PvP combat enhancements

## Building

```bash
# Build the project
./gradlew build

# Build without running tests
./gradlew build -x test
```

## Usage

1. Place the built JAR file in your `.minecraft/mods` folder
2. Launch Minecraft with Fabric loader
3. Press `Y` to open the ClickGUI
4. Use `.c` command prefix for config operations:
   - `.c save <name>` - Save current config
   - `.c load <name>` - Load a saved config
   - `.c list` - List available configs
   - `.c delete <name>` - Delete a config

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.