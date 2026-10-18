package wtf.opal.client.feature.helper.impl;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import wtf.opal.client.feature.helper.impl.auth.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static wtf.opal.client.Constants.mc;

/**
 * Alt Manager Screen - 账号管理界面
 * 基于 ksyzov/AccountManager GuiAccountManager 移植
 */
public final class AltManagerScreen extends Screen {

    private TextFieldWidget usernameField;
    private ButtonWidget addOfflineButton;
    private ButtonWidget addMicrosoftButton;
    private ButtonWidget loginButton;
    private ButtonWidget deleteButton;
    private ButtonWidget closeButton;

    private int selectedIndex = -1;
    private List<Account> accounts = new ArrayList<>();
    private AuthenticationStatus authStatus = AuthenticationStatus.IDLE;
    private String statusMessage = "";
    private long statusMessageTime = 0;
    private long lastClickTime = 0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AltManagerScreen() {
        super(Text.literal("Alt Manager"));
    }

    @Override
    protected void init() {
        accounts = new ArrayList<>(AltManager.get().getAccounts());

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 用户名输入框
        usernameField = new TextFieldWidget(
                this.textRenderer,
                centerX - 150,
                centerY - 80,
                200,
                20,
                Text.literal("Username")
        );
        usernameField.setPlaceholder(Text.literal("Enter offline username"));
        addDrawableChild(usernameField);

        // 添加离线账号
        addOfflineButton = ButtonWidget.builder(Text.literal("Add Offline"), button -> {
            String username = usernameField.getText().trim();
            if (!username.isEmpty()) {
                Account account = new Account("", "", username, 0L);
                account.setUuid(generateOfflineUUID(username).toString());
                AltManager.get().addAccount(account);
                accounts = new ArrayList<>(AltManager.get().getAccounts());
                usernameField.setText("");
                showStatusMessage("Added offline account: " + username, true);
            }
        }).dimensions(centerX + 60, centerY - 80, 90, 20).build();
        addDrawableChild(addOfflineButton);

        // Microsoft 登录
        addMicrosoftButton = ButtonWidget.builder(Text.literal("Add Microsoft"), button -> {
            if (authStatus.isActive()) {
                showStatusMessage("Authentication in progress...", false);
                return;
            }
            if (mc != null) {
                mc.setScreen(new MicrosoftAuthScreen(this));
            }
        }).dimensions(centerX + 60, centerY - 55, 90, 20).build();
        addDrawableChild(addMicrosoftButton);

        // 登录选中账号
        loginButton = ButtonWidget.builder(Text.literal("Login"), button -> loginSelected()).dimensions(centerX - 150, centerY + 85, 72, 20).build();
        addDrawableChild(loginButton);

        // 删除选中账号
        deleteButton = ButtonWidget.builder(Text.literal("Delete"), button -> {
            if (selectedIndex >= 0 && selectedIndex < accounts.size()) {
                Account removed = accounts.get(selectedIndex);
                AltManager.get().removeAccount(removed);
                accounts = new ArrayList<>(AltManager.get().getAccounts());
                selectedIndex = -1;
                showStatusMessage("Removed account: " + removed.getUsername(), true);
            }
        }).dimensions(centerX - 74, centerY + 85, 72, 20).build();
        addDrawableChild(deleteButton);

        // 关闭
        closeButton = ButtonWidget.builder(Text.literal("Close"), button -> closeScreen()).dimensions(centerX + 2, centerY + 85, 72, 20).build();
        addDrawableChild(closeButton);
    }

    private void loginSelected() {
        if (selectedIndex < 0 || selectedIndex >= accounts.size()) {
            return;
        }
        if (authStatus.isActive()) {
            showStatusMessage("Authentication in progress...", false);
            return;
        }

        Account account = accounts.get(selectedIndex);

        if (!account.isMicrosoft()) {
            AltManager.get().loginOffline(account.getUsername());
            showStatusMessage("Logged in as " + account.getUsername(), true);
            return;
        }

        authStatus = AuthenticationStatus.REFRESHING_TOKEN;
        showStatusMessage("Logging in...", false);

        AltManager.get().loginAccount(account, status -> {
            authStatus = status;
            if (status == AuthenticationStatus.SUCCESS) {
                accounts = new ArrayList<>(AltManager.get().getAccounts());
                showStatusMessage("Login successful!", true);
            } else if (status == AuthenticationStatus.FAILED) {
                showStatusMessage("Login failed. Try re-adding the account.", false);
            } else {
                showStatusMessage(status.getDisplayName(), false);
            }
        }, executor).exceptionally(throwable -> {
            showStatusMessage("Login error: " + throwable.getCause().getMessage(), false);
            authStatus = AuthenticationStatus.FAILED;
            return null;
        });
    }

    private java.util.UUID generateOfflineUUID(String username) {
        return java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
    }

    private void showStatusMessage(String message, boolean success) {
        this.statusMessage = message;
        this.statusMessageTime = System.currentTimeMillis();
    }

    private void closeScreen() {
        executor.shutdownNow();
        if (mc != null) {
            mc.setScreen(null);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (mc.currentScreen == this) {
            // 刷新账号列表，防止 unban 状态变化
            accounts = new ArrayList<>(AltManager.get().getAccounts());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 标题
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Alt Manager").formatted(Formatting.GOLD), centerX, centerY - 110, 0xFFFFFF);

        // 当前账号
        Account current = AltManager.get().getCurrentAccount();
        if (current != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Current: ").formatted(Formatting.GRAY)
                            .append(Text.literal(current.getUsername()).formatted(Formatting.GREEN)),
                    centerX, centerY - 95, 0xFFFFFF);
        }

        // 账号列表
        int listX = centerX - 150;
        int listY = centerY - 50;
        int listWidth = 300;
        int rowHeight = 16;
        int maxEntries = 7;
        int startIndex = Math.max(0, Math.min(selectedIndex - maxEntries / 2, Math.max(0, accounts.size() - maxEntries)));
        int endIndex = Math.min(accounts.size(), startIndex + maxEntries);

        for (int i = startIndex; i < endIndex; i++) {
            Account account = accounts.get(i);
            int y = listY + (i - startIndex) * rowHeight;

            boolean isSelected = i == selectedIndex;
            boolean isCurrent = current != null && current.getUsername().equals(account.getUsername());

            if (isSelected) {
                context.fill(listX - 2, y - 2, listX + listWidth + 2, y + rowHeight, 0x800080FF);
            }

            Text accountText = Text.literal(account.getUsername())
                    .formatted(isCurrent ? Formatting.GREEN : Formatting.WHITE)
                    .append(Text.literal(account.isMicrosoft() ? " [MS]" : " [Offline]").formatted(Formatting.GRAY));
            context.drawTextWithShadow(textRenderer, accountText, listX, y, 0xFFFFFF);

            // unban 状态
            Text unbanText = formatUnbanStatus(account.getUnban());
            int unbanWidth = textRenderer.getWidth(unbanText);
            context.drawTextWithShadow(textRenderer, unbanText, listX + listWidth - unbanWidth, y, 0xFFFFFF);
        }

        // 状态消息
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime < 5000) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(statusMessage), centerX, centerY + 115, 0xFFFFFF);
        }

        // 提示
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Left/Right click to select · Double-click/Enter to login · Delete to remove").formatted(Formatting.DARK_GRAY),
                centerX, centerY + 130, 0xFFFFFF);
    }

    private Text formatUnbanStatus(long unban) {
        if (unban == 0) {
            return Text.literal("");
        } else if (unban == -1) {
            return Text.literal("Perm").formatted(Formatting.DARK_RED);
        } else if (unban <= System.currentTimeMillis()) {
            return Text.literal("Unbanned").formatted(Formatting.GREEN);
        } else {
            long diff = unban - System.currentTimeMillis();
            long days = diff / (24 * 60 * 60 * 1000);
            long hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
            long minutes = (diff % (60 * 60 * 1000)) / (60 * 1000);
            if (days > 0) {
                return Text.literal(String.format("%dd %dh", days, hours)).formatted(Formatting.YELLOW);
            } else if (hours > 0) {
                return Text.literal(String.format("%dh %dm", hours, minutes)).formatted(Formatting.YELLOW);
            } else {
                return Text.literal(String.format("%dm", minutes)).formatted(Formatting.YELLOW);
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int listX = centerX - 150;
        int listY = centerY - 50;
        int listWidth = 300;
        int rowHeight = 16;
        int maxEntries = 7;
        int startIndex = Math.max(0, Math.min(selectedIndex - maxEntries / 2, Math.max(0, accounts.size() - maxEntries)));
        int endIndex = Math.min(accounts.size(), startIndex + maxEntries);

        for (int i = startIndex; i < endIndex; i++) {
            int y = listY + (i - startIndex) * rowHeight;
            if (click.x() >= listX && click.x() <= listX + listWidth && click.y() >= y && click.y() <= y + rowHeight) {
                selectedIndex = i;

                long now = System.currentTimeMillis();
                if (doubled || (now - lastClickTime < 300)) {
                    loginSelected();
                }
                lastClickTime = now;

                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    loginSelected();
                }

                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        if (accounts.isEmpty()) {
            return super.keyPressed(keyInput);
        }

        if (keyCode == GLFW.GLFW_KEY_UP) {
            selectedIndex = selectedIndex <= 0 ? accounts.size() - 1 : selectedIndex - 1;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            selectedIndex = selectedIndex >= accounts.size() - 1 ? 0 : selectedIndex + 1;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            loginSelected();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (selectedIndex >= 0 && selectedIndex < accounts.size()) {
                Account removed = accounts.get(selectedIndex);
                AltManager.get().removeAccount(removed);
                accounts = new ArrayList<>(AltManager.get().getAccounts());
                selectedIndex = -1;
                showStatusMessage("Removed account: " + removed.getUsername(), true);
            }
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public void removed() {
        executor.shutdownNow();
        super.removed();
    }
}
