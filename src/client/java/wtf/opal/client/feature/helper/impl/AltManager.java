package wtf.opal.client.feature.helper.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import wtf.opal.client.Constants;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Alt Manager - 简化版账号管理（暂时移除 MinecraftAuth 依赖）
 * 后续可以重新集成完整的 Microsoft 认证
 */
public final class AltManager {

    private static final AltManager INSTANCE = new AltManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final AccountStorage accountStorage;
    private List<AltAccount> accounts = new ArrayList<>();
    private AltAccount currentAccount;
    private AuthenticationStatus status = AuthenticationStatus.IDLE;

    private AltManager() {
        this.accountStorage = new AccountStorage();
        loadAccounts();
    }

    public static AltManager get() {
        return INSTANCE;
    }

    public List<AltAccount> getAccounts() {
        return accounts;
    }

    public AltAccount getCurrentAccount() {
        return currentAccount;
    }

    public AuthenticationStatus getStatus() {
        return status;
    }

    /**
     * 加载已保存的账号
     */
    private void loadAccounts() {
        accounts = accountStorage.loadAccounts();

        // 从当前 Minecraft 会话恢复当前账号
        try {
            Object session = Constants.mc.getSession();
            String username = (String) session.getClass().getMethod("getUsername").invoke(session);
            String uuid = (String) session.getClass().getMethod("getUuidOrNull").invoke(session);

            // 查找匹配的账号
            for (AltAccount account : accounts) {
                if (account.username.equals(username)) {
                    currentAccount = account;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存账号列表
     */
    private void saveAccounts() {
        try {
            accountStorage.saveAccounts(accounts);
        } catch (AuthenticationException e) {
            e.printStackTrace();
        }
    }

    /**
     * 添加离线账号
     */
    public void addOfflineAccount(String username) {
        AltAccount account = new AltAccount();
        account.type = AccountType.OFFLINE;
        account.username = username;
        account.uuid = generateOfflineUUID(username);

        if (!accounts.contains(account)) {
            accounts.add(account);
            saveAccounts();
        }
    }

    /**
     * 移除账号
     */
    public void removeAccount(AltAccount account) {
        accounts.remove(account);
        saveAccounts();
    }

    /**
     * 登录离线账号
     */
    public void loginOffline(String username) {
        try {
            UUID uuid = generateOfflineUUID(username);
            Object session = createSession(username, uuid.toString(), "", "OFFLINE");
            setSession(session);

            AltAccount account = new AltAccount();
            account.type = AccountType.OFFLINE;
            account.username = username;
            account.uuid = uuid;
            this.currentAccount = account;

            if (!accounts.contains(account)) {
                accounts.add(account);
                saveAccounts();
            }

            status = AuthenticationStatus.SUCCESS;
        } catch (Exception e) {
            status = AuthenticationStatus.FAILED;
            e.printStackTrace();
        }
    }

    /**
     * Microsoft 登录（暂时提示使用官方启动器）
     * TODO: 后续集成 MinecraftAuth 完整认证
     */
    public CompletableFuture<AltAccount> loginMicrosoft(Consumer<AuthenticationStatus> statusCallback) {
        return CompletableFuture.supplyAsync(() -> {
            statusCallback.accept(AuthenticationStatus.FAILED);
            this.status = AuthenticationStatus.FAILED;

            // 暂时不支持，提示用户
            throw new CompletionException(new AuthenticationException(
                    AuthenticationException.AuthenticationError.MICROSOFT_AUTH_FAILED,
                    "Microsoft authentication temporarily unavailable. Please use the official launcher."
            ));
        });
    }

    /**
     * 刷新账号令牌（离线账号不需要）
     */
    public CompletableFuture<AltAccount> refreshAccount(AltAccount account, Consumer<AuthenticationStatus> statusCallback) {
        return CompletableFuture.supplyAsync(() -> {
            if (account.type != AccountType.MICROSOFT) {
                statusCallback.accept(AuthenticationStatus.SUCCESS);
                return account; // 离线账号不需要刷新
            }

            statusCallback.accept(AuthenticationStatus.FAILED);
            throw new CompletionException(new AuthenticationException(
                    AuthenticationException.AuthenticationError.INVALID_TOKEN,
                    "Microsoft authentication temporarily unavailable"
            ));
        });
    }

    /**
     * 创建 Minecraft 会话对象
     */
    private Object createSession(String username, String uuid, String accessToken, String accountType) {
        try {
            Object existingSession = Constants.mc.getSession();
            Class<?> sessionClass = existingSession.getClass();
            String sessionClassName = sessionClass.getName();

            Class<?> accountTypeEnum = Class.forName(sessionClassName + "$AccountType");
            Object accountTypeValue = Enum.valueOf((Class<? extends Enum>) accountTypeEnum, accountType);

            java.lang.reflect.Constructor<?> constructor = sessionClass.getConstructor(
                    String.class, String.class, String.class, accountTypeEnum
            );
            return constructor.newInstance(username, uuid, accessToken, accountTypeValue);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create session", e);
        }
    }

    /**
     * 设置 Minecraft 会话
     */
    public void setSession(Object session) {
        try {
            Field sessionField = MinecraftClient.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            sessionField.set(Constants.mc, session);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 生成离线 UUID
     */
    private UUID generateOfflineUUID(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
    }

    /**
     * 账号类型枚举
     */
    public enum AccountType {
        OFFLINE, MICROSOFT
    }

    /**
     * 账号信息类
     */
    public static class AltAccount {
        public AccountType type;
        public String username;
        public UUID uuid;
        public String accessToken;
        public String refreshToken;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AltAccount that = (AltAccount) o;
            return username.equals(that.username);
        }

        @Override
        public int hashCode() {
            return username.hashCode();
        }
    }
}