package io.github.tbenassi.com.hotdeposit.client.gui;

import io.github.cottonmc.cotton.gui.widget.data.Vec2i;
import io.github.tbenassi.com.hotdeposit.client.mixin.HandledScreenAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class CustomCheckboxWidget extends ClickableWidget {
    private static final Identifier TEXTURE_CUSTOM = Identifier.of("hot-deposit", "textures/gui/checkbox_texture.png");
    private static final int TEXT_COLOR = 0x636363;
    private final boolean showMessage;
    private final HandledScreen<?> parent;
    private final Optional<Function<HandledScreenAccessor, Vec2i>> posUpdater;
    private boolean checked;
    private final Callback callback;

    CustomCheckboxWidget(int x, int y, int width, int height,
                         Text message, boolean checked, Callback callback, Boolean showMessage, HandledScreen<?> parent,
                         Optional<Function<HandledScreenAccessor, Vec2i>> posUpdater
    ) {
        super(x, y, width, height, message);
        this.checked = checked;
        this.callback = callback;
        this.showMessage = showMessage;
        this.parent = parent;
        this.posUpdater = posUpdater;
        this.setTooltip(Tooltip.of(message));
        Screens.getButtons(parent).add(this);
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        this.checked = !this.checked;
        this.callback.onValueChange(this, this.checked);
    }

    public boolean isChecked() {
        return this.checked;
    }

    private void setPos(Vec2i pos) {
        setX(pos.x());
        setY(pos.y());
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, this.getNarrationMessage());
        if (this.active) {
            if (this.isFocused()) {
                builder.put(NarrationPart.USAGE, Text.translatable("narration.checkbox.usage.focused"));
            } else {
                builder.put(NarrationPart.USAGE, Text.translatable("narration.checkbox.usage.hovered"));
            }
        }
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        posUpdater.ifPresent(updater -> setPos(updater.apply((HandledScreenAccessor) parent)));

        // Texture dimensions (assuming 64x64 texture atlas)
        int textureWidth = 64;
        int textureHeight = 64;

        // Calculate the u/v coordinates based on the state
        float u = this.isHovered() ? 15.0F : 0.0F;
        float v = this.isChecked() ? 15.0F : 0.0F;

        // Draw checkbox using drawTexture with RenderPipelines.GUI_TEXTURED
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE_CUSTOM,
                getX(), getY(),
                u, v,
                this.width, this.height,
                textureWidth, textureHeight);

        if (this.showMessage) {
            MinecraftClient minecraftClient = MinecraftClient.getInstance();
            TextRenderer textRenderer = minecraftClient.textRenderer;
            int textX = getX() - 50;
            int textY = getY() + (this.height - 8) / 2;
            context.drawText(textRenderer, this.getMessage(), textX, textY, TEXT_COLOR, false);
        }
    }

    @Environment(value=EnvType.CLIENT)
    public interface Callback {
        CustomCheckboxWidget.Callback EMPTY = (checkbox, checked) -> {};

        void onValueChange(CustomCheckboxWidget var1, boolean var2);
    }

    public static CustomCheckboxWidget.Builder builder(HandledScreen<?> parent, Text text) {
        return new CustomCheckboxWidget.Builder(parent, text);
    }

    @Environment(value=EnvType.CLIENT)
    public static class Builder {
        private final Text message;
        private boolean showMessage = false;
        private int width = 15;
        private int height = 15;
        private int x = 0;
        private int y = 0;
        private CustomCheckboxWidget.Callback callback = CustomCheckboxWidget.Callback.EMPTY;
        private boolean checked = false;
        private final HandledScreen<?> parent;
        private Optional<Function<HandledScreenAccessor, Vec2i>> posUpdater = Optional.empty();

        Builder(HandledScreen<?> parent, Text message) {
            this.parent = parent;
            this.message = message;
            if (!this.message.getString().isEmpty())
                this.showMessage = true;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder callback(CustomCheckboxWidget.Callback callback) {
            this.callback = callback;
            return this;
        }

        public Builder checked(boolean checked) {
            this.checked = checked;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder posUpdater(Function<HandledScreenAccessor, Vec2i> posUpdater) {
            this.posUpdater = Optional.ofNullable(posUpdater);
            return this;
        }

        public void build() {
            CustomCheckboxWidget.Callback callback = this.callback != null ? this.callback : (checkbox, checked) -> {
                this.callback.onValueChange(checkbox, checked);
            };
            new CustomCheckboxWidget(this.x, this.y, this.width, this.height, this.message, this.checked, callback,
                    this.showMessage, this.parent, this.posUpdater);
        }
    }
}
