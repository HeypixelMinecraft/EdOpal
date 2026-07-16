package wtf.opal.client.feature.module.property.impl.bool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import wtf.opal.client.feature.module.property.Property;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.opal.client.screen.click.dropdown.panel.property.impl.MultipleBooleanPropertyComponent;

import java.util.Arrays;
import java.util.List;

public final class MultipleBooleanProperty extends Property<List<BooleanProperty>> {

    private int subPropertyIndex;

    public MultipleBooleanProperty(final String name, final BooleanProperty... booleanProperties) {
        super(name);

        setValue(Arrays.asList(booleanProperties));
    }

    public MultipleBooleanProperty(final String name, final ModuleMode<?> parent, final BooleanProperty... booleanProperties) {
        super(name, parent);

        setValue(Arrays.asList(booleanProperties));
    }

    public BooleanProperty getProperty(final String name) {
        return getValue().stream().filter(booleanProperty -> booleanProperty.getName().equals(name)).findFirst().orElse(null);
    }

    @Override
    public void applyValue(JsonElement propertyValue) {
        if (propertyValue.isJsonArray()) {
            final JsonArray jsonProperties = propertyValue.getAsJsonArray();
            for (JsonElement jsonPropertyElement : jsonProperties) {
                final JsonObject jsonProperty = jsonPropertyElement.getAsJsonObject();
                final String propertyName = jsonProperty.get("name").getAsString();
                final JsonElement propertyVal = jsonProperty.get("value");

                for (BooleanProperty booleanProperty : getValue()) {
                    if (propertyName.equals(booleanProperty.getId())) {
                        booleanProperty.applyValue(propertyVal);
                    }
                }
            }
        }
    }

    public int getSubPropertyIndex() {
        return subPropertyIndex;
    }

    public void setSubPropertyIndex(final int subPropertyIndex) {
        this.subPropertyIndex = subPropertyIndex;
    }

    public void cycleSubPropertyIndex() {
        if (!getValue().isEmpty()) {
            subPropertyIndex = (subPropertyIndex + 1) % getValue().size();
        }
    }

    public BooleanProperty getSelectedSubProperty() {
        return getValue().get(subPropertyIndex);
    }

    @Override
    public PropertyPanel<?> createClickGUIComponent() {
        return new MultipleBooleanPropertyComponent(this);
    }

}
