package wtf.opal.client.feature.helper.impl;

import net.minecraft.client.MinecraftClient;
import wtf.opal.client.Constants;
import wtf.opal.client.feature.helper.impl.auth.Account;
import wtf.opal.client.feature.helper.impl.auth.MicrosoftAuth;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Alt Manager - 账号管理
 * 基于 ksyzov/AccountManager 的账号管理逻辑移植
 */
public final class AltManager {

    private static final AltManager INSTANCE = new AltManager();

    private final AccountStorage accountStorage;
    private List<Account> accounts = new ArrayList<>();
    private Account currentAccount;
    private AuthenticationStatus status = AuthenticationStatus.IDLE;

    private AltManager() {
        this.accountStorage = new AccountStorage();
        loadAccounts();
    }

    public static AltManager get() {
        return INSTANCE;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public Account getCurrentAccount() {
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

        try {
            Object session = Constants.mc.getSession();
            String username = (String) session.getClass().getMethod("getUsername").invoke(session);

            for (Account account : accounts) {
                if (account.getUsername().equals(username)) {
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
    public void saveAccounts() {
        try {
            accountStorage.saveAccounts(accounts);
        } catch (AuthenticationException e) {
            e.printStackTrace();
        }
    }

    /**
     * 添加账号
     */
    public void addAccount(Account account) {
        if (!accounts.contains(account)) {
            accounts.add(account);
            saveAccounts();
        }
    }

    /**
     * 移除账号
     */
    public void removeAccount(Account account) {
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

            Account account = new Account("", "", username, 0L);
            account.setUuid(uuid.toString());
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
     * Microsoft OAuth 登录
     */
    public CompletableFuture<Account> loginMicrosoft(Consumer<AuthenticationStatus> statusCallback, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                status = AuthenticationStatus.WAITING_FOR_BROWSER;
                statusCallback.accept(status);

                String state = MicrosoftAuth.randomState();
                String authLink = MicrosoftAuth.getMSAuthLink(state).toString();

                copyToClipboard(authLink);
                openBrowser(authLink);

                status = AuthenticationStatus.ACQUIRING_TOKENS;
                statusCallback.accept(status);

                String authCode = MicrosoftAuth.acquireMSAuthCode(state, executor).join();

                status = AuthenticationStatus.ACQUIRING_TOKENS;
                statusCallback.accept(status);

                var msTokens = MicrosoftAuth.acquireMSAccessTokens(authCode, executor).join();
                String msAccessToken = msTokens.get("access_token");
                String refreshToken = msTokens.get("refresh_token");

                status = AuthenticationStatus.AUTHENTICATING_XBOX;
                statusCallback.accept(status);

                String xboxToken = MicrosoftAuth.acquireXboxAccessToken(msAccessToken, executor).join();

                status = AuthenticationStatus.AUTHENTICATING_XSTS;
                statusCallback.accept(status);

                var xstsData = MicrosoftAuth.acquireXboxXstsToken(xboxToken, executor).join();
                String xstsToken = xstsData.get("Token");
                String userHash = xstsData.get("uhs");

                status = AuthenticationStatus.AUTHENTICATING_MINECRAFT;
                statusCallback.accept(status);

                String mcToken = MicrosoftAuth.acquireMCAccessToken(xstsToken, userHash, executor).join();

                status = AuthenticationStatus.FETCHING_PROFILE;
                statusCallback.accept(status);

                MicrosoftAuth.MinecraftProfile profile = MicrosoftAuth.login(mcToken, executor).join();

                Object session = createSession(profile.username(), profile.uuid(), profile.accessToken(), "MOJANG");
                setSession(session);

                Account account = new Account(refreshToken, profile.accessToken(), profile.username(), 0L);
                account.setUuid(profile.uuid());
                this.currentAccount = account;

                if (!accounts.contains(account)) {
                    accounts.add(account);
                }
                saveAccounts();

                status = AuthenticationStatus.SUCCESS;
                statusCallback.accept(status);
                return account;
            } catch (CompletionException e) {
                status = AuthenticationStatus.FAILED;
                statusCallback.accept(status);
                throw e;
            } catch (Exception e) {
                status = AuthenticationStatus.FAILED;
                statusCallback.accept(status);
                throw new CompletionException(new AuthenticationException(
                        AuthenticationException.AuthenticationError.MICROSOFT_AUTH_FAILED,
                        e.getMessage(),
                        e
                ));
            }
        }, executor);
    }

    /**
     * 刷新账号令牌
     */
    public CompletableFuture<Account> refreshAccount(Account account, Consumer<AuthenticationStatus> statusCallback, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            if (!account.isMicrosoft() || account.getRefreshToken() == null || account.getRefreshToken().isEmpty()) {
                statusCallback.accept(AuthenticationStatus.SUCCESS);
                return account;
            }

            try {
                status = AuthenticationStatus.REFRESHING_TOKEN;
                statusCallback.accept(status);

                var msTokens = MicrosoftAuth.refreshMSAccessTokens(account.getRefreshToken(), executor).join();
                String msAccessToken = msTokens.get("access_token");
                String refreshToken = msTokens.get("refresh_token");

                String xboxToken = MicrosoftAuth.acquireXboxAccessToken(msAccessToken, executor).join();
                var xstsData = MicrosoftAuth.acquireXboxXstsToken(xboxToken, executor).join();
                String xstsToken = xstsData.get("Token");
                String userHash = xstsData.get("uhs");
                String mcToken = MicrosoftAuth.acquireMCAccessToken(xstsToken, userHash, executor).join();
                MicrosoftAuth.MinecraftProfile profile = MicrosoftAuth.login(mcToken, executor).join();

                Object session = createSession(profile.username(), profile.uuid(), profile.accessToken(), "MOJANG");
                setSession(session);

                account.setRefreshToken(refreshToken);
                account.setAccessToken(profile.accessToken());
                account.setUsername(profile.username());
                account.setUuid(profile.uuid());
                this.currentAccount = account;
                saveAccounts();

                status = AuthenticationStatus.SUCCESS;
                statusCallback.accept(status);
                return account;
            } catch (CompletionException e) {
                status = AuthenticationStatus.FAILED;
                statusCallback.accept(status);
                throw e;
            } catch (Exception e) {
                status = AuthenticationStatus.FAILED;
                statusCallback.accept(status);
                throw new CompletionException(new AuthenticationException(
                        AuthenticationException.AuthenticationError.INVALID_TOKEN,
                        e.getMessage(),
                        e
                ));
            }
        }, executor);
    }

    /**
     * 使用已有账号直接登录（先尝试 accessToken，失败则 refresh）
     */
    public CompletableFuture<Account> loginAccount(Account account, Consumer<AuthenticationStatus> statusCallback, Executor executor) {
        if (!account.isMicrosoft()) {
            return CompletableFuture.supplyAsync(() -> {
                loginOffline(account.getUsername());
                return account;
            }, executor);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                MicrosoftAuth.MinecraftProfile profile = MicrosoftAuth.login(account.getAccessToken(), executor).join();
                Object session = createSession(profile.username(), profile.uuid(), profile.accessToken(), "MOJANG");
                setSession(session);

                account.setUsername(profile.username());
                account.setUuid(profile.uuid());
                account.setAccessToken(profile.accessToken());
                this.currentAccount = account;
                saveAccounts();

                status = AuthenticationStatus.SUCCESS;
                statusCallback.accept(status);
                return account;
            } catch (Exception e) {
                // accessToken 失效，尝试 refresh
                return refreshAccount(account, statusCallback, executor).join();
            }
        }, executor);
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

            try {
                // Minecraft 1.21+ Session(String, UUID, String, AccountType)
                java.lang.reflect.Constructor<?> constructor = sessionClass.getConstructor(
                        String.class, UUID.class, String.class, accountTypeEnum
                );
                return constructor.newInstance(username, UUID.fromString(uuid), accessToken, accountTypeValue);
            } catch (NoSuchMethodException ignored) {
                // 旧版本 Session(String, String, String, AccountType)
                java.lang.reflect.Constructor<?> constructor = sessionClass.getConstructor(
                        String.class, String.class, String.class, accountTypeEnum
                );
                return constructor.newInstance(username, uuid, accessToken, accountTypeValue);
            }
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
     * 复制文本到剪贴板
     */
    public static void copyToClipboard(String text) {
        try {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 尝试用系统浏览器打开链接
     */
    public static void openBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
