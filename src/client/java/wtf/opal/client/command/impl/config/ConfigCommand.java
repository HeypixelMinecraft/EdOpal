package wtf.opal.client.command.impl.config;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import wtf.opal.client.command.Command;
import wtf.opal.client.command.arguments.ConfigArgumentType;
import wtf.opal.utility.data.SaveUtility;
import wtf.opal.utility.misc.chat.ChatUtility;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class ConfigCommand extends Command {

    public ConfigCommand() {
        super("Failed to initialize repository:", "Interacts with configs.", "c");
    }

    @Override
    protected void onCommand(final LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("save").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = context.getArgument("config_name", String.class).toLowerCase();

            SaveUtility.saveConfig(configName);
            ChatUtility.success("Config '" + configName + "' saved successfully!");

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("list").executes(context -> {
            final List<String> configs = SaveUtility.listConfigs();

            if (configs.isEmpty()) {
                ChatUtility.print("No configs found.");
            } else {
                ChatUtility.print("Available configs:");
                for (final String config : configs) {
                    ChatUtility.print("  - " + config);
                }
            }

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("load").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = context.getArgument("config_name", String.class).toLowerCase();

            if (SaveUtility.loadConfig(configName)) {
                ChatUtility.success("Config '" + configName + "' loaded successfully!");
            } else {
                ChatUtility.error("Config '" + configName + "' not found!");
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("delete").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = context.getArgument("config_name", String.class).toLowerCase();

            if (SaveUtility.deleteConfig(configName)) {
                ChatUtility.success("Config '" + configName + "' deleted successfully!");
            } else {
                ChatUtility.error("Config '" + configName + "' not found!");
            }

            return SINGLE_SUCCESS;
        })));
    }
}