package wtf.opal.client;

import net.minecraft.client.MinecraftClient;
import wtf.opal.client.renderer.NVGRenderer;

import java.io.File;

public final class Constants {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final long VG = NVGRenderer.getContext();
    
    private static File directory;
    
    public static File getDirectory() {
        if (directory == null) {
            directory = new File(MinecraftClient.getInstance().runDirectory, File.separator + "opal" + File.separator);
        }
        return directory;
    }

    public static final double FIRST_FALL_MOTION = 0.0784000015258789D;
}
