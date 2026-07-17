package wtf.opal.client.feature.helper.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.msa.step.MsaStep;
import net.raphimc.minecraftauth.util.MicrosoftOauthPojoAdapters;
import wtf.opal.client.Constants;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Alt Manager - 使用 MinecraftAuth 库进行账号管理
 */
public final class AltManager {

    private static final AltManager INSTANCE = new AltManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Microsoft OAuth 应用配置
    private static final String CLIENT_ID = "44387aa2-cd6d-4cdc-b25d-2fa7eb6df546";
    private static final int PORT = 25575;

    private final AccountStorage accountStorage;
    private List<AltAccount> accounts = new ArrayList<>();
    private AltAccount currentAccount;
    private AuthenticationStatus status = AuthenticationStatus.IDLE;
    private StepFullJavaSession.FullJavaSession javaSession;

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始 Microsoft 登录流程
     * @param statusCallback 状态更新回调
     * @return CompletableFuture 包含登录结果
     */
    public CompletableFuture<AltAccount> loginMicrosoft(Consumer<AuthenticationStatus> statusCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                statusCallback.accept(AuthenticationStatus.WAITING_FOR_BROWSER);
                this.status = AuthenticationStatus.WAITING_FOR_BROWSER;

                // 创建 MinecraftAuth 客户端
                StepFullJavaSession.FullJavaSession session = MinecraftAuth.getJavaClient(
                        MinecraftAuth.builder()
                                .withClientId(CLIENT_ID)
                                .withDeviceToken()
                                .build()
                ).loginWithMicrosoft();

                statusCallback.accept(AuthenticationStatus.ACQUIRING_MS_TOKEN);
                this.status = AuthenticationStatus.ACQUIRING_MS_TOKEN;

                statusCallback.accept(AuthenticationStatus.MINECRAFT_AUTHENTICATING);
                this.status = AuthenticationStatus.MINECRAFT_AUTHENTICATING;

                // 提取账号信息
                String username = session.getGameProfile().getName();
                String uuid = session.getGameProfile().getId().toString();
                String accessToken = session.getAccessToken();

                // 切换会话
                Object mcSession = createSession(username, uuid, accessToken, "MSA");
                setSession(mcSession);

                statusCallback.accept(AuthenticationStatus.SAVING_ACCOUNT);
                this.status = AuthenticationStatus.SAVING_ACCOUNT;

                // 保存账号
                AltAccount account = new AltAccount();
                account.type = AccountType.MICROSOFT;
                account.username = username;
                account.uuid = UUID.fromString(uuid);
                account.accessToken = accessToken;

                this.currentAccount = account;
                this.javaSession = session;

                if (!accounts.contains(account)) {
                    accounts.add(account);
                    saveAccounts();
                }

                statusCallback.accept(AuthenticationStatus.SUCCESS);
                this.status = AuthenticationStatus.SUCCESS;

                return account;
            } catch (Exception e) {
                statusCallback.accept(AuthenticationStatus.FAILED);
                this.status = AuthenticationStatus.FAILED;
                throw new CompletionException(new AuthenticationException(
                        AuthenticationException.AuthenticationError.MICROSOFT_AUTH_FAILED,
                        "Microsoft authentication failed",
                        e
                ));
            }
        });
    }

    /**
     * 刷新账号令牌
     */
    public CompletableFuture<AltAccount> refreshAccount(AltAccount account, Consumer<AuthenticationStatus> statusCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (account.type != AccountType.MICROSOFT) {
                    return account; // 离线账号不需要刷新
                }

                statusCallback.accept(AuthenticationStatus.ACQUIRING_MS_TOKEN);
                this.status = AuthenticationStatus.ACQUIRING_MS_TOKEN;

                // 使用 refresh token 刷新
                if (javaSession != null) {
                    javaSession = MinecraftAuth.getJavaClient(
                            MinecraftAuth.builder()
                                    .withClientId(CLIENT_ID)
                                    .withDeviceToken()
                                    .build()
                    ).refreshJavaSession(javaSession);

                    String accessToken = javaSession.getAccessToken();

                    account.accessToken = accessToken;
                    saveAccounts();

                    statusCallback.accept(AuthenticationStatus.SUCCESS);
                    this.status = AuthenticationStatus.SUCCESS;

                    return account;
                } else {
                    throw new AuthenticationException(
                            AuthenticationException.AuthenticationError.INVALID_TOKEN,
                            "No valid session to refresh"
                    );
                }
            } catch (Exception e) {
                statusCallback.accept(AuthenticationStatus.FAILED);
                this.status = AuthenticationStatus.FAILED;
                throw new CompletionException(new AuthenticationException(
                        AuthenticationException.AuthenticationError.INVALID_TOKEN,
                        "Failed to refresh token",
                        e
                ));
            }
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