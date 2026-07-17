package wtf.opal.client.feature.helper.impl;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Alt Manager Screen - 账号管理界面
 * 修复了 HeadlessException 问题，添加了认证状态显示
 */
public final class AltManagerScreen extends Screen {

    private TextFieldWidget usernameField;
    private ButtonWidget loginOfflineButton;
    private ButtonWidget loginMicrosoftButton;
    private ButtonWidget refreshButton;
    private ButtonWidget removeButton;
    private ButtonWidget closeButton;

    private int selectedIndex = -1;
    private List<AltManager.AltAccount> accounts;
    private AuthenticationStatus authStatus = AuthenticationStatus.IDLE;
    private String statusMessage = "";
    private long statusMessageTime = 0;

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
                centerX - 100,
                centerY - 60,
                200,
                20,
                Text.literal("Username")
        );
        usernameField.setPlaceholder(Text.literal("Enter offline username"));
        addDrawableChild(usernameField);

        // 登录离线账号按钮
        loginOfflineButton = ButtonWidget.builder(Text.literal("Login Offline"), button -> {
            String username = usernameField.getText().trim();
            if (!username.isEmpty()) {
                AltManager.get().loginOffline(username);
                accounts = new ArrayList<>(AltManager.get().getAccounts());
                selectedIndex = -1;
                usernameField.setText("");
                showStatusMessage("Logged in as " + username, true);
            }
        }).dimensions(centerX - 100, centerY - 30, 95, 20).build();
        addDrawableChild(loginOfflineButton);

        // Microsoft 登录按钮
        loginMicrosoftButton = ButtonWidget.builder(Text.literal("Microsoft Login"), button -> {
            if (authStatus.isActive()) {
                showStatusMessage("Authentication in progress...", false);
                return;
            }

            authStatus = AuthenticationStatus.WAITING_FOR_BROWSER;
            showStatusMessage("Opening browser...", false);

            AltManager.get().loginMicrosoft(status -> {
                authStatus = status;
                if (status == AuthenticationStatus.SUCCESS) {
                    accounts = new ArrayList<>(AltManager.get().getAccounts());
                    selectedIndex = -1;
                    showStatusMessage("Login successful!", true);
                } else if (status == AuthenticationStatus.FAILED) {
                    showStatusMessage("Login failed. Please try again.", false);
                } else {
                    showStatusMessage(status.getDisplayName(), false);
                }
            }).exceptionally(throwable -> {
                showStatusMessage("Error: " + throwable.getCause().getMessage(), false);
                authStatus = AuthenticationStatus.FAILED;
                return null;
            });
        }).dimensions(centerX + 5, centerY - 30, 95, 20).build();
        addDrawableChild(loginMicrosoftButton);

        // 刷新账号按钮
        refreshButton = ButtonWidget.builder(Text.literal("Refresh"), button -> {
            if (selectedIndex >= 0 && selectedIndex < accounts.size()) {
                AltManager.AltAccount account = accounts.get(selectedIndex);
                if (account.type == AltManager.AccountType.MICROSOFT) {
                    if (authStatus.isActive()) {
                        showStatusMessage("Authentication in progress...", false);
                        return;
                    }

                    authStatus = AuthenticationStatus.ACQUIRING_MS_TOKEN;
                    showStatusMessage("Refreshing token...", false);

                    AltManager.get().refreshAccount(account, status -> {
                        authStatus = status;
                        if (status == AuthenticationStatus.SUCCESS) {
                            accounts = new ArrayList<>(AltManager.get().getAccounts());
                            showStatusMessage("Token refreshed!", true);
                        } else if (status == AuthenticationStatus.FAILED) {
                            showStatusMessage("Token refresh failed.", false);
                        }
                    }).exceptionally(throwable -> {
                        showStatusMessage("Refresh error: " + throwable.getCause().getMessage(), false);
                        authStatus = AuthenticationStatus.FAILED;
                        return null;
                    });
                } else {
                    showStatusMessage("Offline accounts don't need refreshing.", false);
                }
            }
        }).dimensions(centerX - 100, centerY + 70, 95, 20).build();
        addDrawableChild(refreshButton);

        // 移除账号按钮
        removeButton = ButtonWidget.builder(Text.literal("Remove"), button -> {
            if (selectedIndex >= 0 && selectedIndex < accounts.size()) {
                AltManager.AltAccount removed = accounts.get(selectedIndex);
                AltManager.get().removeAccount(removed);
                accounts = new ArrayList<>(AltManager.get().getAccounts());
                selectedIndex = -1;
                showStatusMessage("Removed account: " + removed.username, true);
            }
        }).dimensions(centerX + 5, centerY + 70, 95, 20).build();
        addDrawableChild(removeButton);

        // 关闭按钮
        closeButton = ButtonWidget.builder(Text.literal("Close"), button -> {
            this.client.setScreen(null);
        }).dimensions(centerX - 50, centerY + 100, 100, 20).build();
        addDrawableChild(closeButton);
    }

    /**
     * 显示状态消息
     */
    private void showStatusMessage(String message, boolean success) {
        this.statusMessage = message;
        this.statusMessageTime = System.currentTimeMillis();
    }

    /**
     * 安全地打开浏览器
     * 修复了 HeadlessException 问题
     */
    private boolean openBrowser(String url) {
        try {
            // 方法1: 使用 Desktop API（标准方式）
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }

            // 方法2: 使用操作系统特定命令
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else if (os.contains("nix") || os.contains("nux")) {
                pb = new ProcessBuilder("xdg-open", url);
            } else {
                // 方法3: 降级处理 - 复制到剪贴板
                copyToClipboard(url);
                showStatusMessage("Please open the link manually (copied to clipboard)", false);
                return false;
            }

            pb.start();
            return true;
        } catch (Exception e) {
            // 最终降级处理
            copyToClipboard(url);
            showStatusMessage("Browser error. Link copied to clipboard.", false);
            return false;
        }
    }

    /**
     * 复制文本到剪贴板
     */
    private void copyToClipboard(String text) {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            java.awt.datatransfer.Clipboard clipboard = toolkit.getSystemClipboard();
            StringSelection selection = new StringSelection(text);
            clipboard.setContents(selection, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);

        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 标题
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Alt Manager").formatted(Formatting.GOLD), centerX, centerY - 80, 0xFFFFFF);

        // 当前账号
        AltManager.AltAccount current = AltManager.get().getCurrentAccount();
        if (current != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Current: ").formatted(Formatting.GRAY)
                            .append(Text.literal(current.username).formatted(Formatting.GREEN)),
                    centerX, centerY + 60, 0xFFFFFF);
        }

        // 认证状态显示
        if (authStatus.isActive() || (authStatus == AuthenticationStatus.FAILED && System.currentTimeMillis() - statusMessageTime < 3000)) {
            Text statusText = Text.literal(authStatus.getDisplayName())
                    .formatted(authStatus == AuthenticationStatus.FAILED ? Formatting.RED : Formatting.YELLOW);
            context.drawCenteredTextWithShadow(textRenderer, statusText, centerX, centerY - 85, 0xFFFFFF);
        }

        // 状态消息
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime < 5000) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(statusMessage), centerX, centerY + 130, 0xFFFFFF);
        }

        // 账号列表
        int listY = centerY;
        int maxEntries = 6;
        int startIndex = Math.max(0, selectedIndex - maxEntries / 2);
        int endIndex = Math.min(accounts.size(), startIndex + maxEntries);

        for (int i = startIndex; i < endIndex; i++) {
            AltManager.AltAccount account = accounts.get(i);
            int y = listY + (i - startIndex) * 20;
            int x = centerX - 100;
            int width = 200;

            boolean isSelected = i == selectedIndex;
            boolean isCurrent = current != null && current.username.equals(account.username);

            // 选中高亮
            if (isSelected) {
                context.fill(x - 2, y - 2, x + width + 2, y + 14, 0x800080FF);
            }

            Text accountText = Text.literal(account.username)
                    .formatted(isCurrent ? Formatting.GREEN : Formatting.WHITE)
                    .append(Text.literal(" (" + account.type.name() + ")").formatted(Formatting.GRAY));

            context.drawTextWithShadow(textRenderer, accountText, x, y, 0xFFFFFF);
        }

        // 提示文本
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Left-click to select, Right-click to login").formatted(Formatting.DARK_GRAY),
                centerX, centerY + 150, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int listY = centerY;
        int maxEntries = 6;
        int startIndex = Math.max(0, selectedIndex - maxEntries / 2);
        int endIndex = Math.min(accounts.size(), startIndex + maxEntries);

        for (int i = startIndex; i < endIndex; i++) {
            int y = listY + (i - startIndex) * 20;
            int x = centerX - 100;
            int width = 200;

            if (click.x() >= x && click.x() <= x + width && click.y() >= y && click.y() <= y + 14) {
                selectedIndex = i;

                // 右键直接登录
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    AltManager.AltAccount account = accounts.get(i);
                    if (account.type == AltManager.AccountType.OFFLINE) {
                        AltManager.get().loginOffline(account.username);
                        accounts = new ArrayList<>(AltManager.get().getAccounts());
                        showStatusMessage("Logged in as " + account.username, true);
                    } else {
                        showStatusMessage("Microsoft accounts require re-authentication. Click Microsoft Login.", false);
                    }
                }

                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }
}