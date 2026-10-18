package wtf.opal.client.screen.click.dropdown;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.visual.ClickGUIModule;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.client.renderer.repository.FontRepository;
import wtf.opal.client.renderer.text.NVGTextRenderer;
import wtf.opal.client.screen.click.dropdown.panel.CategoryPanel;
import wtf.opal.utility.misc.HoverUtility;
import wtf.opal.utility.misc.Multithreading;
import wtf.opal.utility.player.PlayerUtility;
import wtf.opal.utility.render.ColorUtility;
import wtf.opal.utility.render.animation.Animation;
import wtf.opal.utility.render.animation.Easing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static wtf.opal.client.Constants.mc;

public final class DropdownClickGUI extends Screen {

    private static final float SEARCH_PANEL_OFFSET = 23;
    private static final float SEARCH_BAR_WIDTH = 300;
    private static final float SEARCH_BAR_HEIGHT = 18;

    private final List<CategoryPanel> categoryPanelList = new ArrayList<>();
    private final Animation islandSearchAnimation = new Animation(Easing.DYNAMIC_ISLAND, 250);
    private TextFieldWidget searchField;
    public static boolean displayingBinds, selectingBind, typingString;
    private static boolean renderingBloom;
    private static boolean liquidGlassBackgroundActive;
    private boolean closing;

    private float searchBarX, searchBarY;

    public DropdownClickGUI() {
        super(Text.empty());

        int index = 0;
        for (ModuleCategory category : ModuleCategory.VALUES) {
            categoryPanelList.add(new CategoryPanel(category, index));

            index++;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        final boolean frameStarted = NVGRenderer.beginFrame();

        this.islandSearchAnimation.run(this.closing ? 0 : 1);

        displayingBinds = PlayerUtility.isKeyPressed(GLFW.GLFW_KEY_TAB);
        final boolean allowDrag = OpalClient.getInstance().getModuleRepository().getModule(ClickGUIModule.class).isAllowDrag();

        final int categoryAmount = categoryPanelList.size();
        for (int i = 0; i < categoryAmount; i++) {
            final CategoryPanel panel = categoryPanelList.get(i);

            final float y = 25 + SEARCH_PANEL_OFFSET * this.islandSearchAnimation.getValue();
            final float width = 110;
            final float spacing = 10;
            final float height = 20;

            final float totalWidth = categoryAmount * width + (categoryAmount - 1) * spacing;

            final float startX = (mc.getWindow().getScaledWidth() - totalWidth) / 2;
            final float x = startX + i * (width + spacing);

            panel.setBasePosition(x, y);
            panel.setDraggingAllowed(allowDrag);
            panel.setDimensions(x + panel.getDragOffsetX(), y + panel.getDragOffsetY(), width, height);
            panel.render(context, mouseX, mouseY, delta);
        }

        this.renderSearchBar(mouseX, mouseY);

        if (frameStarted) {
            NVGRenderer.endFrameAndReset(true);
        }
    }

    /**
     * Draws the module search pill above the category rail. The original implementation delegated this to the
     * dynamic island overlay; keeping it local makes the click GUI self-contained.
     */
    private void renderSearchBar(final int mouseX, final int mouseY) {
        final float progress = this.islandSearchAnimation.getValue();
        if (progress <= 0.01F) {
            return;
        }

        this.searchBarX = (mc.getWindow().getScaledWidth() - SEARCH_BAR_WIDTH) / 2F;
        this.searchBarY = 25 + (1F - progress) * 4F;

        final boolean focused = this.isSearchFocused();
        final boolean hovering = HoverUtility.isHovering(
                this.searchBarX, this.searchBarY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, mouseX, mouseY
        );

        final int theme = ColorUtility.getClientTheme().first;

        NVGRenderer.globalAlpha(progress);
        NVGRenderer.roundedRect(this.searchBarX, this.searchBarY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, SEARCH_BAR_HEIGHT / 2F, NVGRenderer.BLUR_PAINT);
        NVGRenderer.roundedRect(this.searchBarX, this.searchBarY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, SEARCH_BAR_HEIGHT / 2F, 0xd90f0f0f);

        if (focused || hovering) {
            NVGRenderer.roundedRectOutline(
                    this.searchBarX, this.searchBarY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, SEARCH_BAR_HEIGHT / 2F,
                    1F, ColorUtility.applyOpacity(theme, focused ? 0.85F : 0.4F)
            );
        }

        final NVGTextRenderer font = FontRepository.getFont("productsans-medium");
        final String text = this.getSearchText();
        final float textX = this.searchBarX + 10F;
        final float textY = this.searchBarY + SEARCH_BAR_HEIGHT / 2F;

        if (text.isEmpty()) {
            font.drawString("Type to search modules", textX, textY, 8, 0x80ffffff);
        } else {
            font.drawString(text, textX, textY, 8, -1);
        }

        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            final int cursor = Math.min(this.getSearchCursor(), text.length());
            final float caretX = textX + font.getStringWidth(text.substring(0, cursor), 8);
            NVGRenderer.rect(caretX + 1F, textY - 4F, 0.8F, 8F, -1);
        }

        NVGRenderer.globalAlpha(1);
    }

    public void renderBloom(final DrawContext context, final float delta) {
        renderingBloom = true;
        try {
            this.render(context, -1, -1, delta);
        } finally {
            renderingBloom = false;
        }
    }

    public static boolean isRenderingBloom() {
        return renderingBloom;
    }

    public static boolean isLiquidGlassBackgroundActive() {
        return liquidGlassBackgroundActive;
    }

    public static void setLiquidGlassBackgroundActive(final boolean active) {
        liquidGlassBackgroundActive = active;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && this.islandSearchAnimation.getValue() > 0.5F
                && HoverUtility.isHovering(this.searchBarX, this.searchBarY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, click.x(), click.y())) {
            this.searchField.setFocused(true);
            this.searchField.setCursorToEnd(false);
            typingString = true;
            return true;
        }

        if (this.isSearchFocused()) {
            this.blurSearch();
        }

        final boolean allowDrag = OpalClient.getInstance().getModuleRepository().getModule(ClickGUIModule.class).isAllowDrag();
        categoryPanelList.forEach(categoryPanel -> categoryPanel.setDraggingAllowed(allowDrag));
        categoryPanelList.forEach(categoryPanel -> categoryPanel.mouseClicked(click.x(), click.y(), click.button()));

        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        categoryPanelList.forEach(categoryPanel -> categoryPanel.mouseReleased(click.x(), click.y(), click.button()));

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        categoryPanelList.forEach(categoryPanel -> categoryPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount));

        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (this.isSearchFocused()) {
            if (keyInput.key() == GLFW.GLFW_KEY_ESCAPE || keyInput.key() == GLFW.GLFW_KEY_ENTER) {
                this.blurSearch();
            } else {
                this.searchField.keyPressed(keyInput);
            }
            return true;
        }

        // needed for close functionality
        super.keyPressed(keyInput);

        categoryPanelList.forEach(categoryPanel -> categoryPanel.keyPressed(keyInput));

        return true;
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (this.isSearchFocused()) {
            this.searchField.charTyped(charInput);
            return true;
        }

        categoryPanelList.forEach(categoryPanel -> categoryPanel.charTyped((char) charInput.codepoint(), charInput.modifiers()));
        return true;
    }

    @Override
    protected void init() {
        this.closing = false;
        this.islandSearchAnimation.setValue(0);
        this.islandSearchAnimation.setFinished(false);
        this.searchField = new TextFieldWidget(mc.textRenderer, 0, 0, 1, 1, Text.literal("Module search"));
        this.searchField.setMaxLength(64);
        this.searchField.setDrawsBackground(false);
        this.searchField.setChangedListener(this::applySearch);
        this.applySearch("");
        typingString = false;
        categoryPanelList.forEach(CategoryPanel::init);
    }

    @Override
    public void close() {
        if (selectingBind) {
            return;
        }

        this.closing = true;
        this.blurSearch();
        categoryPanelList.forEach(CategoryPanel::close);

        Multithreading.schedule(
                () -> OpalClient.getInstance().getModuleRepository().getModule(ClickGUIModule.class).setEnabled(false),
                100, TimeUnit.MILLISECONDS
        );
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public float getIslandSearchProgress() {
        return this.islandSearchAnimation.getValue();
    }

    public boolean isClosing() {
        return this.closing;
    }

    public boolean isSearchFocused() {
        return this.searchField != null && this.searchField.isFocused();
    }

    public String getSearchText() {
        return this.searchField == null ? "" : this.searchField.getText();
    }

    public int getSearchCursor() {
        return this.searchField == null ? 0 : this.searchField.getCursor();
    }

    private void blurSearch() {
        if (this.searchField == null) {
            return;
        }

        this.searchField.setFocused(false);
        typingString = false;
    }

    private void applySearch(final String query) {
        this.categoryPanelList.forEach(panel -> panel.setSearchQuery(query));
    }

}
