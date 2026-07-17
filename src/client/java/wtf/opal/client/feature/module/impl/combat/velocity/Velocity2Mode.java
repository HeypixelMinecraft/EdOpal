package wtf.opal.client.feature.module.impl.combat.velocity;

import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;

public abstract class Velocity2Mode extends ModuleMode<Velocity2Module> {
    protected Velocity2Mode(Velocity2Module module) {
        super(module);
    }

    public String getSuffix() {
        return this.getEnumValue().toString();
    }
}