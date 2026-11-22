package dev.pulse;

/** Всё, что нужно модулям для рисования. Реализация для игры — в McPlatform. */
interface Gfx {
    void fill(int x1, int y1, int x2, int y2, int argb);
    void text(String s, int x, int y, int argb);
    int textWidth(String s);
    void item(Object stack, int x, int y);

    /** Вертикальный градиент (по умолчанию — средний цвет). */
    default void gradient(int x1, int y1, int x2, int y2, int top, int bottom) { fill(x1, y1, x2, y2, Draw.mix(top, bottom, 0.5)); }
    default void push() { }
    default void pop() { }
    default void scale(float s) { }
}
