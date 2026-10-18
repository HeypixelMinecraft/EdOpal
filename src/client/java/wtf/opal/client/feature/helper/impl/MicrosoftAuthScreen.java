package wtf.opal.client.feature.helper.impl;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import wtf.opal.client.feature.helper.impl.auth.Account;
import wtf.opal.client.feature.helper.impl.auth.MicrosoftAuth;

import java.util.concurrent.*;

import static wtf.opal.client.Constants.mc;

/**
 * Microsoft OAuth 认证进度界面
 * 基于 ksyzov/AccountManager GuiMicrosoftAuth 移植
 */
public final class MicrosoftAuthScreen extends Screen {

    private final Screen parent;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String authLink = "";
    private AuthenticationStatus status = AuthenticationStatus.WAITING_FOR_BROWSER;
    private String statusMessage = "";
    private boolean completed = false;

    private ButtonWidget openButton;
    private ButtonWidget cancelButton;

    public MicrosoftAuthScreen(Screen parent) {
        super(Text.literal("Microsoft Authentication"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        openButton = ButtonWidget.builder(Text.literal("Open Browser"), button -> {
            AltManager.openBrowser(authLink);
            AltManager.copyToClipboard(authLink);
        }).dimensions(centerX - 105, centerY + 30, 100, 20).build();
        addDrawableChild(openButton);

        cancelButton = ButtonWidget.builder(Text.literal("Cancel"), button -> closeScreen()).dimensions(centerX + 5, centerY + 30, 100, 20).build();
        addDrawableChild(cancelButton);

        startAuth();
    }

    private void startAuth() {
        String state = MicrosoftAuth.randomState();
        this.authLink = MicrosoftAuth.getMSAuthLink(state).toString();

        AltManager.copyToClipboard(authLink);
        AltManager.openBrowser(authLink);

        status = AuthenticationStatus.WAITING_FOR_BROWSER;

        CompletableFuture.supplyAsync(() -> {
            try {
                String authCode = MicrosoftAuth.acquireMSAuthCode(state, executor).join();

                updateStatus(AuthenticationStatus.ACQUIRING_TOKENS);
                var msTokens = MicrosoftAuth.acquireMSAccessTokens(authCode, executor).join();
                String msAccessToken = msTokens.get("access_token");
                String refreshToken = msTokens.get("refresh_token");

                updateStatus(AuthenticationStatus.AUTHENTICATING_XBOX);
                String xboxToken = MicrosoftAuth.acquireXboxAccessToken(msAccessToken, executor).join();

                updateStatus(AuthenticationStatus.AUTHENTICATING_XSTS);
                var xstsData = MicrosoftAuth.acquireXboxXstsToken(xboxToken, executor).join();
                String xstsToken = xstsData.get("Token");
                String userHash = xstsData.get("uhs");

                updateStatus(AuthenticationStatus.AUTHENTICATING_MINECRAFT);
                String mcToken = MicrosoftAuth.acquireMCAccessToken(xstsToken, userHash, executor).join();

                updateStatus(AuthenticationStatus.FETCHING_PROFILE);
                MicrosoftAuth.MinecraftProfile profile = MicrosoftAuth.login(mcToken, executor).join();

                Object session = createSession(profile.username(), profile.uuid(), profile.accessToken(), "MOJANG");
                AltManager.get().setSession(session);

                Account account = new Account(refreshToken, profile.accessToken(), profile.username(), 0L);
                account.setUuid(profile.uuid());
                AltManager.get().addAccount(account);

                updateStatus(AuthenticationStatus.SUCCESS);
                completed = true;
                return account;
            } catch (Exception e) {
                updateStatus(AuthenticationStatus.FAILED);
                statusMessage = e.getMessage() != null ? e.getMessage() : "Authentication failed";
                completed = true;
                throw new CompletionException(e);
            }
        }, executor).exceptionally(throwable -> {
            updateStatus(AuthenticationStatus.FAILED);
            return null;
        });
    }

    private void updateStatus(AuthenticationStatus newStatus) {
        this.status = newStatus;
        this.statusMessage = newStatus.getDisplayName();
    }

    private Object createSession(String username, String uuid, String accessToken, String accountType) {
        try {
            Object existingSession = mc.getSession();
            Class<?> sessionClass = existingSession.getClass();
            String sessionClassName = sessionClass.getName();

            Class<?> accountTypeEnum = Class.forName(sessionClassName + "$AccountType");
            Object accountTypeValue = Enum.valueOf((Class<? extends Enum>) accountTypeEnum, accountType);

            try {
                java.lang.reflect.Constructor<?> constructor = sessionClass.getConstructor(
                        String.class, java.util.UUID.class, String.class, accountTypeEnum
                );
                return constructor.newInstance(username, java.util.UUID.fromString(uuid), accessToken, accountTypeValue);
            } catch (NoSuchMethodException ignored) {
                java.lang.reflect.Constructor<?> constructor = sessionClass.getConstructor(
                        String.class, String.class, String.class, accountTypeEnum
                );
                return constructor.newInstance(username, uuid, accessToken, accountTypeValue);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create session", e);
        }
    }

    private void closeScreen() {
        executor.shutdownNow();
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (completed && status == AuthenticationStatus.SUCCESS) {
            closeScreen();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Microsoft Authentication").formatted(Formatting.GOLD), centerX, centerY - 60, 0xFFFFFF);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(status.getDisplayName()).formatted(status == AuthenticationStatus.FAILED ? Formatting.RED : Formatting.YELLOW),
                centerX, centerY - 20, 0xFFFFFF);

        if (status == AuthenticationStatus.FAILED && !statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(statusMessage).formatted(Formatting.RED), centerX, centerY, 0xFFFFFF);
        } else {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("A login link has been copied to your clipboard.").formatted(Formatting.GRAY),
                    centerX, centerY, 0xFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void removed() {
        executor.shutdownNow();
        super.removed();
    }
}
