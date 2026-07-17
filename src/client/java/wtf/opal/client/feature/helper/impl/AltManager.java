package wtf.opal.client.feature.helper.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.MinecraftClient;
import wtf.opal.client.Constants;

import java.io.*;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class AltManager {

    private static final AltManager INSTANCE = new AltManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CLIENT_ID = "44387aa2-cd6d-4cdc-b25d-2fa7eb6df546";
    private static final String SCOPE = "XboxLive.signin XboxLive.offline_access";
    private static final int PORT = 25575;

    private List<AltAccount> accounts = new ArrayList<>();
    private AltAccount currentAccount;
    private boolean isAuthenticating = false;

    private AltManager() {
        loadAccounts();
    }

    private static File getAltsFile() {
        return new File(Constants.getDirectory(), "alts.json");
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

    public boolean isAuthenticating() {
        return isAuthenticating;
    }

    public void loadAccounts() {
        File altsFile = getAltsFile();
        if (altsFile.exists()) {
            try (Reader reader = new FileReader(altsFile)) {
                accounts = GSON.fromJson(reader, new TypeToken<List<AltAccount>>() {}.getType());
            } catch (Exception e) {
                accounts = new ArrayList<>();
            }
        }
    }

    public void saveAccounts() {
        try (Writer writer = new FileWriter(getAltsFile())) {
            GSON.toJson(accounts, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addOfflineAccount(String username) {
        AltAccount account = new AltAccount();
        account.type = AccountType.OFFLINE;
        account.username = username;
        account.uuid = generateOfflineUUID(username);
        accounts.add(account);
        saveAccounts();
    }

    public void removeAccount(AltAccount account) {
        accounts.remove(account);
        saveAccounts();
    }

    public void loginOffline(String username) {
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
    }

    public String getMicrosoftAuthLink(String state) {
        try {
            return String.format(
                    "https://login.live.com/oauth20_authorize.srf?client_id=%s&response_type=code&redirect_uri=http://localhost:%d/callback&scope=%s&state=%s&prompt=select_account",
                    CLIENT_ID, PORT, SCOPE, state
            );
        } catch (Exception e) {
            return null;
        }
    }

    public CompletableFuture<String> acquireMicrosoftAuthCode(String state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> authCode = new AtomicReference<>(null);
                AtomicReference<String> errorMsg = new AtomicReference<>(null);

                server.createContext("/callback", exchange -> {
                    String query = exchange.getRequestURI().getQuery();
                    Map<String, String> params = parseQuery(query);

                    if (!state.equals(params.get("state"))) {
                        errorMsg.set(String.format("State mismatch! Expected '%s' but got '%s'.", state, params.get("state")));
                    } else if (params.containsKey("code")) {
                        authCode.set(params.get("code"));
                    } else if (params.containsKey("error")) {
                        errorMsg.set(String.format("%s: %s", params.get("error"), params.get("error_description")));
                    }

                    String response = "<html><body>Authentication complete! You can close this window now.</body></html>";
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    exchange.getResponseBody().write(responseBytes);
                    exchange.getResponseBody().close();
                    latch.countDown();
                });

                try {
                    server.start();
                    latch.await();
                    if (authCode.get() != null && !authCode.get().isEmpty()) {
                        return authCode.get();
                    } else {
                        throw new Exception(errorMsg.get() != null ? errorMsg.get() : "No auth code received");
                    }
                } finally {
                    server.stop(2);
                }
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft auth code acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft auth code!", e);
            }
        });
    }

    public CompletableFuture<AltAccount> loginMicrosoft(String authCode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                isAuthenticating = true;

                Map<String, String> msTokens = acquireMSAccessTokens(authCode);
                String msAccessToken = msTokens.get("access_token");
                String msRefreshToken = msTokens.get("refresh_token");

                String xboxToken = acquireXboxAccessToken(msAccessToken);
                String xboxUserHash = acquireXboxUserHash(xboxToken);
                String minecraftToken = acquireMinecraftToken(xboxToken, xboxUserHash);

                Map<String, String> minecraftProfile = acquireMinecraftProfile(minecraftToken);
                String username = minecraftProfile.get("name");
                String uuid = minecraftProfile.get("id");

                Object session = createSession(username, uuid, minecraftToken, "MSA");
                setSession(session);

                AltAccount account = new AltAccount();
                account.type = AccountType.MICROSOFT;
                account.username = username;
                account.uuid = UUID.fromString(uuid);
                account.accessToken = minecraftToken;
                account.refreshToken = msRefreshToken;
                this.currentAccount = account;

                if (!accounts.contains(account)) {
                    accounts.add(account);
                    saveAccounts();
                }

                isAuthenticating = false;
                return account;
            } catch (Exception e) {
                isAuthenticating = false;
                throw new RuntimeException("Microsoft login failed", e);
            }
        });
    }

    private Map<String, String> acquireMSAccessTokens(String authCode) throws Exception {
        String url = "https://login.live.com/oauth20_token.srf";
        String params = String.format(
                "client_id=%s&grant_type=authorization_code&code=%s&redirect_uri=http://localhost:%d/callback",
                CLIENT_ID, authCode, PORT
        );

        String response = httpPost(url, params, "application/x-www-form-urlencoded");
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        String accessToken = json.get("access_token").getAsString();
        String refreshToken = json.get("refresh_token").getAsString();

        Map<String, String> result = new HashMap<>();
        result.put("access_token", accessToken);
        result.put("refresh_token", refreshToken);
        return result;
    }

    private String acquireXboxAccessToken(String msAccessToken) throws Exception {
        String url = "https://user.auth.xboxlive.com/user/authenticate";
        String body = String.format(
                "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=%s\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}",
                msAccessToken
        );

        String response = httpPost(url, body, "application/json");
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        return json.get("Token").getAsString();
    }

    private String acquireXboxUserHash(String xboxToken) throws Exception {
        String url = "https://xsts.auth.xboxlive.com/xsts/authorize";
        String body = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xboxToken + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";

        String response = httpPost(url, body, "application/json");
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        return json.get("DisplayClaims").getAsJsonObject().get("xui").getAsJsonArray().get(0).getAsJsonObject().get("uhs").getAsString();
    }

    private String acquireMinecraftToken(String xboxToken, String xboxUserHash) throws Exception {
        String url = "https://api.minecraftservices.com/authentication/login_with_xbox";
        String body = String.format("{\"identityToken\":\"XBL3.0 x=%s;%s\"}", xboxUserHash, xboxToken);

        String response = httpPost(url, body, "application/json");
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        return json.get("access_token").getAsString();
    }

    private Map<String, String> acquireMinecraftProfile(String minecraftToken) throws Exception {
        String url = "https://api.minecraftservices.com/minecraft/profile";
        String response = httpGet(url, minecraftToken);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        Map<String, String> result = new HashMap<>();
        result.put("name", json.get("name").getAsString());
        result.put("id", json.get("id").getAsString());
        return result;
    }

    private String httpPost(String urlStr, String body, String contentType) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String httpGet(String urlStr, String bearerToken) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            String key = idx > 0 ? pair.substring(0, idx) : pair;
            String value = idx > 0 && pair.length() > idx + 1 ? pair.substring(idx + 1) : "";
            params.put(key, value);
        }
        return params;
    }

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

    public void setSession(Object session) {
        try {
            Field sessionField = MinecraftClient.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            sessionField.set(Constants.mc, session);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private UUID generateOfflineUUID(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
    }

    public enum AccountType {
        OFFLINE, MICROSOFT
    }

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