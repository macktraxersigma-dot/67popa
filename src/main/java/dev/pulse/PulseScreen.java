package dev.pulse;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Экран меню. Мышь и клавиши опрашиваем через GLFW, чтобы не зависеть от сигнатур обработчиков в разных версиях игры. */
final class PulseScreen extends Screen {
    private final ClickGui gui = new ClickGui();
    private final boolean[] keys = new boolean[350];

    PulseScreen() { super(Text.literal("Pulse Visual")); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient c = MinecraftClient.getInstance(); long h = c.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        StringBuilder sb = new StringBuilder(); boolean back = false;
        for (int k = 32; k <= 259; k++) {
            if (!(k == 32 || k == 45 || (k >= 48 && k <= 57) || (k >= 65 && k <= 90) || k == 259)) continue;
            boolean d = GLFW.glfwGetKey(h, k) == GLFW.GLFW_PRESS;
            if (d && !keys[k]) { if (k == 259) back = true; else sb.append((char) k); }
            keys[k] = d;
        }
        gui.type(sb.toString(), back);
        gui.draw(new McPlatform.McGfx(ctx, c.textRenderer), width, height, mouseX, mouseY, l, r);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Pulse.save(); }
}
