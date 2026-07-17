package wtf.opal.client.feature.module.impl.combat.velocity;

import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.combat.velocity.impl.Velocity2LagMode;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;

public final class Velocity2Module extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.LAG);

    public Velocity2Module() {
        super("Velocity2", "Advanced velocity bypass for GrimAC.", ModuleCategory.COMBAT);
        this.addProperties(this.mode);
        addModuleModes(mode, new Velocity2LagMode(this));
    }

    @Override
    public String getSuffix() {
        return ((Velocity2Mode) this.getActiveMode()).getSuffix();
    }

    public enum Mode {
        LAG("Lag");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}