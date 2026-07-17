package wtf.opal.client.feature.helper.impl;

import net.minecraft.client.network.ServerInfo;
import wtf.opal.client.feature.helper.IHelper;
import wtf.opal.client.feature.helper.impl.auth.Account;
import wtf.opal.event.impl.game.JoinWorldEvent;
import wtf.opal.event.impl.game.server.ServerDisconnectEvent;
import wtf.opal.event.subscriber.Subscribe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static wtf.opal.client.Constants.mc;

/**
 * Hypixel unban 时间追踪事件监听器
 * 基于 ksyzov/AccountManager Events 移植
 */
public final class AltManagerEvents implements IHelper {

    // Hypixel 临时封禁提示示例：
    // "You are temporarily banned for 6d 23h 59m from this server!"
    // "You are temporarily blocked for 1d from this server!"
    private static final Pattern TEMP_BAN_PATTERN = Pattern.compile(
            "temporarily (?:banned|blocked) for (?:(\\d+)d)?\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m)?",
            Pattern.CASE_INSENSITIVE
    );

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        if (!isHypixel()) {
            return;
        }

        Account current = AltManager.get().getCurrentAccount();
        if (current != null && current.getUnban() != 0) {
            current.setUnban(0);
            AltManager.get().saveAccounts();
        }
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        Account current = AltManager.get().getCurrentAccount();
        if (current == null) {
            return;
        }

        String reason = event.getReason().toLowerCase();

        // 永久封禁
        if (reason.contains("permanently banned") || reason.contains("permanently blocked")) {
            current.setUnban(-1);
            AltManager.get().saveAccounts();
            return;
        }

        // 临时封禁
        Matcher matcher = TEMP_BAN_PATTERN.matcher(reason);
        if (matcher.find()) {
            long duration = parseDuration(matcher);
            if (duration > 0) {
                current.setUnban(System.currentTimeMillis() + duration);
                AltManager.get().saveAccounts();
            }
        }
    }

    private boolean isHypixel() {
        ServerInfo serverInfo = mc.getCurrentServerEntry();
        if (serverInfo == null) {
            return false;
        }
        String address = serverInfo.address.toLowerCase();
        return address.contains("hypixel.net") || address.contains("hypixel.io");
    }

    private long parseDuration(Matcher matcher) {
        long duration = 0;

        String days = matcher.group(1);
        String hours = matcher.group(2);
        String minutes = matcher.group(3);

        if (days != null) {
            duration += Long.parseLong(days) * 24 * 60 * 60 * 1000;
        }
        if (hours != null) {
            duration += Long.parseLong(hours) * 60 * 60 * 1000;
        }
        if (minutes != null) {
            duration += Long.parseLong(minutes) * 60 * 1000;
        }

        return duration;
    }
}
