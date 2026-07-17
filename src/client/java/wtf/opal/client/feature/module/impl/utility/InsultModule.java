package wtf.opal.client.feature.module.impl.utility;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.time.Stopwatch;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static wtf.opal.client.Constants.mc;

public final class InsultModule extends Module {

    private static final List<String> INSULTS = loadInsults();
    private static final Random RANDOM = new Random();

    private final Stopwatch stopwatch = new Stopwatch();

    private final NumberProperty delay = new NumberProperty("Delay", 1000, 0, 10000, 50);
    private final BooleanProperty antiSpamBypass = new BooleanProperty("Anti Spam Bypass", true);
    private final BooleanProperty randomOrder = new BooleanProperty("Random", true);

    private int index;

    public InsultModule() {
        super("Insult", "Spams random insults from the built-in wordlist", ModuleCategory.UTILITY);
        addProperties(delay, antiSpamBypass, randomOrder);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.getNetworkHandler() == null || INSULTS.isEmpty()) return;

        if (shouldSend()) {
            String insult = nextInsult();

            if (antiSpamBypass.getValue()) {
                insult = Math.random() + " " + insult;
            }

            mc.getNetworkHandler().sendChatMessage(insult);
        }
    }

    private boolean shouldSend() {
        return stopwatch.hasTimeElapsed(delay.getValue().longValue(), true);
    }

    private String nextInsult() {
        if (randomOrder.getValue()) {
            return INSULTS.get(RANDOM.nextInt(INSULTS.size()));
        }

        String insult = INSULTS.get(index);
        index = (index + 1) % INSULTS.size();
        return insult;
    }

    private static List<String> loadInsults() {
        try (InputStream stream = InsultModule.class.getResourceAsStream("/assets/opal/misc/insult.json")) {
            if (stream == null) {
                return new ArrayList<>();
            }

            List<String> list = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    new TypeToken<List<String>>() {}.getType()
            );
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
