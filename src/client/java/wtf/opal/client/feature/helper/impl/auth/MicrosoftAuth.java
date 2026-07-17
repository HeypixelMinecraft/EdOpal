package wtf.opal.client.feature.helper.impl.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 手动 Microsoft OAuth 实现（基于 ksyzov/AccountManager）
 * 使用 Java 21 内置 HttpClient 替代 Apache HttpClient
 */
public final class MicrosoftAuth {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // Account Manager 注册的 Azure 应用 ID
    private static final String CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";

    // 25565 + 10
    private static final int PORT = 25575;

    public static URI getMSAuthLink(String state) {
        try {
            String redirectUri = URLEncoder.encode(String.format("http://localhost:%d/callback", PORT), StandardCharsets.UTF_8);
            String scope = URLEncoder.encode("XboxLive.signin XboxLive.offline_access", StandardCharsets.UTF_8);
            String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);

            String url = String.format(
                    "https://login.live.com/oauth20_authorize.srf?client_id=%s&response_type=code&redirect_uri=%s&scope=%s&state=%s&prompt=select_account",
                    CLIENT_ID, redirectUri, scope, encodedState
            );
            return URI.create(url);
        } catch (Exception e) {
            return null;
        }
    }

    public static CompletableFuture<String> acquireMSAuthCode(String state, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> authCode = new AtomicReference<>(null);
                AtomicReference<String> errorMsg = new AtomicReference<>(null);

                server.createContext("/callback", exchange -> {
                    try {
                        Map<String, String> query = parseQuery(exchange.getRequestURI().toString());

                        if (!state.equals(query.get("state"))) {
                            errorMsg.set(String.format("State mismatch! Expected '%s' but got '%s'.", state, query.get("state")));
                        } else if (query.containsKey("code")) {
                            authCode.set(query.get("code"));
                        } else if (query.containsKey("error")) {
                            errorMsg.set(String.format("%s: %s", query.get("error"), query.get("error_description")));
                        }

                        byte[] response = loadCallbackHtml();
                        exchange.getResponseHeaders().add("Content-Type", "text/html");
                        exchange.sendResponseHeaders(200, response.length);
                        exchange.getResponseBody().write(response);
                    } catch (Exception ignored) {
                    } finally {
                        exchange.close();
                        latch.countDown();
                    }
                });

                try {
                    server.start();
                    latch.await();

                    String code = authCode.get();
                    if (code != null && !code.isBlank()) {
                        return code;
                    }
                    throw new Exception(Optional.ofNullable(errorMsg.get()).orElse("There was no auth code or error description present."));
                } finally {
                    server.stop(2);
                }
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft auth code acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft auth code!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireMSAccessTokens(String authCode, Executor executor) {
        return tokenRequest("authorization_code", "code", authCode, executor);
    }

    public static CompletableFuture<Map<String, String>> refreshMSAccessTokens(String msToken, Executor executor) {
        return tokenRequest("refresh_token", "refresh_token", msToken, executor);
    }

    private static CompletableFuture<Map<String, String>> tokenRequest(String grantType, String tokenKey, String tokenValue, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String form = String.format(
                        "client_id=%s&grant_type=%s&%s=%s&redirect_uri=%s",
                        CLIENT_ID,
                        grantType,
                        tokenKey,
                        URLEncoder.encode(tokenValue, StandardCharsets.UTF_8),
                        URLEncoder.encode(String.format("http://localhost:%d/callback", PORT), StandardCharsets.UTF_8)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://login.live.com/oauth20_token.srf"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                String accessToken = getStringOrThrow(json, "access_token", "There was no Microsoft access token or error description present.");
                String refreshToken = getStringOrThrow(json, "refresh_token", "There was no Microsoft refresh token or error description present.");

                Map<String, String> result = new HashMap<>();
                result.put("access_token", accessToken);
                result.put("refresh_token", refreshToken);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft access tokens acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft access tokens!", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireXboxAccessToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", String.format("d=%s", accessToken));

                JsonObject entity = new JsonObject();
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "http://auth.xboxlive.com");
                entity.addProperty("TokenType", "JWT");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                        .header("Content-Type", "application/json")
                        .timeout(REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(entity.toString()))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject json = response.statusCode() == 200
                        ? JsonParser.parseString(response.body()).getAsJsonObject()
                        : new JsonObject();

                return getStringOrThrow(json, "Token", "There was no access token or error description present.");
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live access token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireXboxXstsToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonArray userTokens = new JsonArray();
                userTokens.add(accessToken);

                JsonObject properties = new JsonObject();
                properties.addProperty("SandboxId", "RETAIL");
                properties.add("UserTokens", userTokens);

                JsonObject entity = new JsonObject();
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
                entity.addProperty("TokenType", "JWT");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                        .header("Content-Type", "application/json")
                        .timeout(REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(entity.toString()))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject json = response.statusCode() == 200
                        ? JsonParser.parseString(response.body()).getAsJsonObject()
                        : new JsonObject();

                String token = getStringOrThrow(json, "Token", "There was no access token or error description present.");
                String uhs = json.getAsJsonObject("DisplayClaims")
                        .getAsJsonArray("xui").get(0).getAsJsonObject()
                        .get("uhs").getAsString();

                Map<String, String> result = new HashMap<>();
                result.put("Token", token);
                result.put("uhs", uhs);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live XSTS token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live XSTS token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireMCAccessToken(String xstsToken, String userHash, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = String.format("{\"identityToken\": \"XBL3.0 x=%s;%s\"}", userHash, xstsToken);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                        .header("Content-Type", "application/json")
                        .timeout(REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                return getStringOrThrow(json, "access_token", "There was no access token or error description present.");
            } catch (InterruptedException e) {
                throw new CancellationException("Minecraft access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Minecraft access token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<MinecraftProfile> login(String mcToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                        .header("Authorization", "Bearer " + mcToken)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                String uuid = getStringOrThrow(json, "id", "There was no profile or error description present.");
                String username = json.get("name").getAsString();
                return new MinecraftProfile(username, uuid, mcToken);
            } catch (InterruptedException e) {
                throw new CancellationException("Minecraft profile fetching was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to fetch Minecraft profile!", e);
            }
        }, executor);
    }

    public static String randomState() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static Map<String, String> parseQuery(String uri) {
        String query = uri;
        int idx = uri.indexOf('?');
        if (idx >= 0) {
            query = uri.substring(idx + 1);
        }
        if (query.startsWith("/callback?")) {
            query = query.substring("/callback?".length());
        } else if (query.startsWith("/callback")) {
            query = query.substring("/callback".length());
        }

        if (query.isEmpty()) {
            return new HashMap<>();
        }

        return java.util.Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        parts -> decode(parts[0]),
                        parts -> parts.length > 1 ? decode(parts[1]) : "",
                        (a, b) -> a
                ));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static byte[] loadCallbackHtml() {
        try (InputStream stream = MicrosoftAuth.class.getResourceAsStream("/callback.html")) {
            if (stream != null) {
                return stream.readAllBytes();
            }
        } catch (IOException ignored) {
        }
        return "<html><body>You may now close this window.</body></html>".getBytes(StandardCharsets.UTF_8);
    }

    private static String getStringOrThrow(JsonObject json, String key, String missingMessage) throws Exception {
        JsonElement element = json.get(key);
        if (element != null && !element.isJsonNull()) {
            String value = element.getAsString();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        if (json.has("error")) {
            throw new Exception(String.format("%s: %s", json.get("error").getAsString(), json.get("error_description").getAsString()));
        }
        if (json.has("XErr")) {
            throw new Exception(String.format("%s: %s", json.get("XErr").getAsString(), json.get("Message").getAsString()));
        }
        throw new Exception(missingMessage);
    }

    public record MinecraftProfile(String username, String uuid, String accessToken) {
    }
}
