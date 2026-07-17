package wtf.opal.utility.data;

import com.google.gson.*;
import com.ibm.icu.impl.Pair;
import wtf.opal.client.OpalClient;
import wtf.opal.client.binding.BindingService;
import wtf.opal.client.binding.IBindable;
import wtf.opal.client.binding.type.InputType;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.UnknownModuleException;
import wtf.opal.client.feature.module.property.Property;
import wtf.opal.utility.data.serializer.PairSerializer;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import wtf.opal.client.Constants;
import static wtf.opal.client.Constants.getDirectory;

public final class SaveUtility {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Pair.class, new PairSerializer())
            .excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting()
            .create();

    private static final BindingService BINDING_SERVICE = OpalClient.getInstance().getBindRepository().getBindingService();
    private static File getConfigsDirectory() {
        return new File(getDirectory(), "configs");
    }

    private SaveUtility() {
    }

    public static void saveBindings() {
        try {
            if (!getDirectory().exists()) {
                getDirectory().mkdir();
            }
            final File file = new File(getDirectory(), "bindings.json");
            final JsonArray bindingsArray = new JsonArray();
            for (final Pair<Integer, InputType> binding : BINDING_SERVICE.getBindingMap().keySet()) {
                final JsonObject bindingJson = new JsonObject();
                bindingJson.addProperty("keyCode", binding.first);
                JsonArray bindablesArray = new JsonArray();
                for (IBindable bindable : BINDING_SERVICE.getBindingMap().get(binding)) {
                    if (bindable instanceof Module module) {
                        JsonObject moduleJson = new JsonObject();
                        moduleJson.addProperty("module", module.getId());
                        bindablesArray.add(moduleJson);
                    } else if (bindable instanceof Config config) {
                        JsonObject configJson = new JsonObject();
                        configJson.addProperty("config", config.getName());
                        bindablesArray.add(configJson);
                    }
                }
                bindingJson.add("bindables", bindablesArray);
                bindingsArray.add(bindingJson);
            }
            Files.writeString(file.toPath(), GSON.toJson(bindingsArray));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadBindings() {
        try (final FileReader reader = new FileReader(new File(getDirectory(), "bindings.json"))) {
            final JsonArray bindingsArray = JsonParser.parseReader(reader).getAsJsonArray();
            for (final JsonElement bindingElement : bindingsArray) {
                final JsonObject bindingJson = bindingElement.getAsJsonObject();
                final int keyCode = bindingJson.get("keyCode").getAsInt();
                final InputType inputType = keyCode < 10 ? InputType.MOUSE : InputType.KEYBOARD;
                final JsonArray bindablesArray = bindingJson.getAsJsonArray("bindables");
                for (final JsonElement bindableElement : bindablesArray) {
                    final JsonObject bindableJson = bindableElement.getAsJsonObject();
                    if (bindableJson.has("module")) {
                        final String moduleID = bindableJson.get("module").getAsString();
                        final Module module = OpalClient.getInstance().getModuleRepository().getModule(moduleID);
                        BINDING_SERVICE.register(keyCode, module, inputType);
                    } else if (bindableJson.has("config")) {
                        final String configName = bindableJson.get("config").getAsString();
                        final Config config = new Config(configName);
                        BINDING_SERVICE.register(keyCode, config, inputType);
                    }
                }
            }
        } catch (IOException | UnknownModuleException e) {
            e.printStackTrace();
        }
    }

    public static void saveConfig(final String name) {
        try {
            if (!getConfigsDirectory().exists()) {
                getConfigsDirectory().mkdirs();
            }
            final File file = new File(getConfigsDirectory(), name + ".json");
            final JsonArray modulesArray = new JsonArray();
            for (final Module module : OpalClient.getInstance().getModuleRepository().getModules()) {
                final JsonObject moduleJson = new JsonObject();
                moduleJson.addProperty("name", module.getId());
                moduleJson.addProperty("enabled", module.isEnabled());
                moduleJson.addProperty("visible", module.isVisible());
                final JsonArray propertiesArray = new JsonArray();
                for (final Property<?> property : module.getPropertyList()) {
                    if (!property.isHidden()) {
                        final JsonObject propertyJson = new JsonObject();
                        propertyJson.addProperty("name", property.getId());
                        propertyJson.add("value", GSON.toJsonTree(property.getValue()));
                        propertiesArray.add(propertyJson);
                    }
                }
                moduleJson.add("properties", propertiesArray);
                modulesArray.add(moduleJson);
            }
            Files.writeString(file.toPath(), GSON.toJson(modulesArray));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean loadConfig(final String name) {
        final File file = new File(getConfigsDirectory(), name + ".json");
        if (!file.exists()) {
            return false;
        }
        try (final FileReader reader = new FileReader(file)) {
            final JsonArray modulesArray = JsonParser.parseReader(reader).getAsJsonArray();
            for (final JsonElement moduleElement : modulesArray) {
                final JsonObject moduleJson = moduleElement.getAsJsonObject();
                final String moduleID = moduleJson.get("name").getAsString();
                for (final Module clientModule : OpalClient.getInstance().getModuleRepository().getModules()) {
                    if (moduleID.equals(clientModule.getId())) {
                        if (moduleJson.has("enabled")) {
                            final boolean enabled = moduleJson.get("enabled").getAsBoolean();
                            if (enabled != clientModule.isEnabled()) {
                                clientModule.setEnabled(enabled);
                            }
                        }
                        if (moduleJson.has("visible")) {
                            final boolean visible = moduleJson.get("visible").getAsBoolean();
                            if (visible != clientModule.isVisible()) {
                                clientModule.setVisible(visible);
                            }
                        }
                        if (moduleJson.has("properties")) {
                            final JsonArray propertiesArray = moduleJson.getAsJsonArray("properties");
                            for (final JsonElement propertyElement : propertiesArray) {
                                final JsonObject propertyJson = propertyElement.getAsJsonObject();
                                final String propertyName = propertyJson.get("name").getAsString();
                                final JsonElement propertyValueElement = propertyJson.get("value");
                                for (final Property<?> clientProperty : clientModule.getPropertyList()) {
                                    if (propertyName.equals(clientProperty.getId())) {
                                        clientProperty.applyValue(propertyValueElement);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static List<String> listConfigs() {
        final List<String> configNames = new ArrayList<>();
        if (!getConfigsDirectory().exists()) {
            return configNames;
        }
        final File[] files = getConfigsDirectory().listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (final File file : files) {
                final String fname = file.getName();
                configNames.add(fname.substring(0, fname.length() - 5));
            }
        }
        return configNames;
    }

    public static boolean deleteConfig(final String name) {
        final File file = new File(getConfigsDirectory(), name + ".json");
        return file.exists() && file.delete();
    }

}