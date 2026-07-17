package wtf.opal.client.feature.helper.impl;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class AltManagerScreen extends Screen {

    private TextFieldWidget usernameField;
    private ButtonWidget loginOfflineButton;
    private ButtonWidget loginMicrosoftButton;
    private ButtonWidget removeButton;
    private ButtonWidget closeButton;
    private int selectedIndex = -1;
    private List<AltManager.AltAccount> accounts;

    public AltManagerScreen() {
        super(Text.literal("Alt Manager"));
    }

    @Override
    protected void init() {
        accounts = new ArrayList<>(AltManager.get().getAccounts());

        int centerX = this.width / 2;
        int centerY = this.height / 2;

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

        loginOfflineButton = ButtonWidget.builder(Text.literal("Login Offline"), button -> {
            String username = usernameField.getText().trim();
            if (!username.isEmpty()) {
                AltManager.get().loginOffline(username);
                accounts = new ArrayList<>(AltManager.get().getAccounts());
                selectedIndex = -1;
                usernameField.setText("");
            }
        }).dimensions(centerX - 100, centerY - 30, 95, 20).build();
        addDrawableChild(loginOfflineButton);

        loginMicrosoftButton = ButtonWidget.builder(Text.literal("Microsoft Login"), button -> {
            String state = java.util.UUID.randomUUID().toString().replace("-", "");
            String authLink = AltManager.get().getMicrosoftAuthLink(state);
            
            if (authLink != null) {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(authLink));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                AltManager.get().acquireMicrosoftAuthCode(state).thenAccept(authCode -> {
                    AltManager.get().loginMicrosoft(authCode).thenAccept(account -> {
                        accounts = new ArrayList<>(AltManager.get().getAccounts());
                        selectedIndex = -1;
                    }).exceptionally(e -> {
                        e.printStackTrace();
                        return null;
                    });
                }).exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });
            }
        }).dimensions(centerX + 5, centerY - 30, 95, 20).build();
        addDrawableChild(loginMicrosoftButton);

        removeButton = ButtonWidget.builder(Text.literal("Remove"), button -> {
            if (selectedIndex >= 0 && selectedIndex < accounts.size()) {
                AltManager.get().removeAccount(accounts.get(selectedIndex));
                accounts = new ArrayList<>(AltManager.get().getAccounts());
                selectedIndex = -1;
            }
        }).dimensions(centerX - 100, centerY + 100, 95, 20).build();
        addDrawableChild(removeButton);

        closeButton = ButtonWidget.builder(Text.literal("Close"), button -> {
            this.client.setScreen(null);
        }).dimensions(centerX + 5, centerY + 100, 95, 20).build();
        addDrawableChild(closeButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);

        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Alt Manager").formatted(Formatting.GOLD), centerX, centerY - 80, 0xFFFFFF);

        AltManager.AltAccount current = AltManager.get().getCurrentAccount();
        if (current != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Current: ").formatted(Formatting.GRAY)
                            .append(Text.literal(current.username).formatted(Formatting.GREEN)),
                    centerX, centerY + 80, 0xFFFFFF);
        }

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

            if (isSelected) {
                context.fill(x - 2, y - 2, x + width + 2, y + 14, 0x800080FF);
            }

            Text accountText = Text.literal(account.username)
                    .formatted(isCurrent ? Formatting.GREEN : Formatting.WHITE)
                    .append(Text.literal(" (" + account.type.name() + ")").formatted(Formatting.GRAY));

            context.drawTextWithShadow(textRenderer, accountText, x, y, 0xFFFFFF);

            if (isSelected && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 14) {
                if (this.client.mouse.wasLeftButtonClicked()) {
                    AltManager.get().loginOffline(account.username);
                }
            }
        }
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
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

}