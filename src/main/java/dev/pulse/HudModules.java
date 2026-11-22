package dev.pulse;

import dev.pulse.Data.*;
import java.util.*;
import static dev.pulse.Draw.*;

/** Интерфейс и мир: Interface, HotAlerts, Crosshair, ArmorHUD, Ambience. */
final class HudModules {

    // ───────────── Interface ─────────────
    static final class Interface extends Module {
        final Setting accent = mode("Акцент", 0, Theme.NAMES), watermark = bool("Ватермарк", true), list = bool("Список модулей", true), coords = bool("Координаты", true), target = bool("Карточка цели", true);
        int tid = -1; double thp;
        Interface() { super("Interface", "HUD", "Тема, ватермарк, список модулей, координаты и карточка цели"); add(accent, watermark, list, coords, target); on = true; }

        @Override void render(Frame f, Gfx g) {
            Self m = f.me; int acc = Theme.c();
            if (watermark.on()) {
                String rest = m.name + "  ·  " + m.fps + " fps" + (m.ping > 0 ? "  ·  " + m.ping + " ms" : "");
                int bw = g.boldWidth("Pulse"), w = bw + g.textWidth(rest) + 32;
                card(g, 6, 6, 6 + w, 22, 0xC0141220); g.fill(14, 6, 6 + w - 8, 7, alpha(acc, 190));
                round(g, 12, 12, 16, 16, 2, acc); g.bold("Pulse", 21, 10, acc); g.text(rest, 21 + bw + 8, 10, 0xFFE6E4F2);
            }
            int y0 = 28;
            if (coords.on()) { String s = String.format(java.util.Locale.ROOT, "XYZ  %.1f  %.1f  %.1f", m.x, m.y, m.z); int w = g.textWidth(s) + 14;
                card(g, 6, y0, 6 + w, y0 + 15, 0xB0141220); g.text(s, 13, y0 + 4, 0xFFD2D0E0); }
            if (list.on()) {
                List<Module> on = new ArrayList<>(); for (Module x : Pulse.mods) if (x.on && x != this) on.add(x);
                on.sort((a, b) -> g.textWidth(b.name) - g.textWidth(a.name)); int y = 6, i = 0;
                for (Module x : on) { int w = g.textWidth(x.name) + 14, x2 = f.sw - 6; int c = Theme.c(i * 0.08);
                    round(g, x2 - w, y, x2, y + 14, 3, 0xA0141220); round(g, x2 - 3, y + 3, x2 - 1, y + 11, 1, c); g.text(x.name, x2 - w + 6, y + 3, c); y += 16; i++; }
            }
            if (target.on() && f.target != null && !f.target.kind.equals("item")) {
                Ent t = f.target; if (t.id != tid) { tid = t.id; thp = t.hp; } thp += (t.hp - thp) * 0.12;
                double fr = Math.max(0, Math.min(1, thp / Math.max(1, t.maxHp)));
                String nm = t.name.length() > 13 ? t.name.substring(0, 12) + "…" : t.name, hp = String.format(java.util.Locale.ROOT, "%.1f", t.hp);
                int w = 128, x = f.sw / 2 + 28, y = f.sh / 2 + 16;
                card(g, x, y, x + w, y + 30, 0xC0141220);
                g.bold(nm, x + 8, y + 6, 0xFFFFFFFF); g.text(hp, x + w - 8 - g.textWidth(hp), y + 6, mix(0xFFFF5566, 0xFF55FF8A, fr));
                round(g, x + 8, y + 20, x + w - 8, y + 24, 2, 0x30FFFFFF);
                int fw = (int) ((w - 16) * fr); if (fw >= 3) round(g, x + 8, y + 20, x + 8 + fw, y + 24, 2, acc);
            }
        }
    }

    // ───────────── HotAlerts ─────────────
    static final class HotAlerts extends Module {
        final Setting low = bool("Низкое HP", true), totem = bool("Тотем", true), potion = bool("Зелья", true), lowAt = num("HP меньше", 6, 2, 12, 1);
        static final class A { String t; int col; long until; }
        final List<A> alerts = new ArrayList<>(); final Set<String> warned = new HashSet<>(); int lastTotems = -1;
        HotAlerts() { super("HotAlerts", "HUD", "Уведомления: низкое HP, тотем, конец зелья"); add(low, totem, potion, lowAt); on = true; }

        void push(String t, int col, long now) { A a = new A(); a.t = t; a.col = col; a.until = now + 3000; alerts.add(a); if (alerts.size() > 5) alerts.remove(0); }

        @Override void tick(Frame f) {
            Self m = f.me;
            if (totem.on() && lastTotems > m.totems) push("ТОТЕМ СРАБОТАЛ · осталось " + m.totems, 0xFFFFD84D, f.now); lastTotems = m.totems;
            if (potion.on()) {
                Set<String> cur = new HashSet<>();
                for (Eff e : m.eff) { cur.add(e.name); if (e.ticks > 0 && e.ticks <= 100 && warned.add(e.name)) push(e.name + " закончится через " + Math.max(1, e.ticks / 20) + " с", 0xFF7FD7FF, f.now); }
                warned.retainAll(cur);
            }
        }

        @Override void render(Frame f, Gfx g) {
            int y = f.sh / 4;
            if (low.on() && m(f) <= lowAt.num()) {
                double pulse = 0.5 + 0.5 * Math.sin(f.now / 140.0); String s = "НИЗКОЕ ЗДОРОВЬЕ  " + String.format(java.util.Locale.ROOT, "%.1f", m(f));
                int w = g.textWidth(s) + 16; pill(g, f.sw / 2 - w / 2, y - 3, f.sw / 2 + w / 2, y + 13, alpha(0x2A0A0E, (int) (150 + 80 * pulse)));
                g.fill(f.sw / 2 - w / 2, y + 12, f.sw / 2 + w / 2, y + 13, alpha(0xFF4D4D, (int) (140 + 115 * pulse))); textC(g, s, f.sw / 2.0, y + 1, 0xFFFF6B6B); y += 20;
            }
            for (Iterator<A> it = alerts.iterator(); it.hasNext(); ) {
                A a = it.next(); long left = a.until - f.now; if (left <= 0) { it.remove(); continue; } double k = Math.min(1, left / 500.0);
                int w = g.textWidth(a.t) + 16; pill(g, f.sw / 2 - w / 2, y - 3, f.sw / 2 + w / 2, y + 13, mulA(0xC0141220, k));
                g.fill(f.sw / 2 - w / 2 + 6, y + 12, f.sw / 2 - w / 2 + 6 + (int) ((w - 12) * left / 3000.0), y + 13, mulA(a.col, k)); textC(g, a.t, f.sw / 2.0, y + 1, mulA(a.col, k)); y += 18;
            }
        }
        static double m(Frame f) { return f.me.hp + f.me.absorb; }
    }

    // ───────────── Crosshair ─────────────
    static final class Crosshair extends Module {
        final Setting ring = bool("Кольцо кулдауна", true), dot = bool("Точка", true), hitm = bool("Хитмаркер", true), radius = num("Радиус", 9, 6, 18, 1);
        long lastHit;
        Crosshair() { super("Crosshair", "HUD", "Кольцо перезарядки удара (как в CS) и хитмаркер"); add(ring, dot, hitm, radius); on = true; }
        @Override void onAttack(Ent e) { lastHit = System.currentTimeMillis(); }

        @Override void render(Frame f, Gfx g) {
            double cx = f.sw / 2.0, cy = f.sh / 2.0, R = radius.num(); int c = Theme.c();
            if (ring.on()) {
                double pr = Math.max(0, Math.min(1, f.me.cooldown)); int n = 64;
                for (int i = 0; i < n; i++) { double a = -Math.PI / 2 + i * 2 * Math.PI / n, px = cx + Math.cos(a) * R, py = cy + Math.sin(a) * R;
                    dot(g, px, py, 2, i / (double) n <= pr ? alpha(pr >= 1 ? 0x55FF8A : c, 235) : 0x30FFFFFF); }
            }
            if (dot.on()) { dot(g, cx, cy, 3, 0xAA000000); dot(g, cx, cy, 1, c); }
            if (hitm.on()) { long age = f.now - lastHit; if (age < 280) { double k = 1 - age / 280.0; int col = alpha(0xFFFFFF, (int) (255 * k)); double in = R + 1 + (1 - k) * 3, out = in + 5;
                for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) line(g, cx + sx * in * 0.7, cy + sy * in * 0.7, cx + sx * out * 0.7, cy + sy * out * 0.7, col, 1); } }
        }
    }

    // ───────────── ArmorHUD ─────────────
    static final class ArmorHud extends Module {
        final Setting pct = bool("Проценты", true);
        ArmorHud() { super("ArmorHUD", "HUD", "Надетая броня и прочность на экране"); add(pct); on = true; }

        @Override void render(Frame f, Gfx g) {
            int x = f.sw - 4 * 24 - 6, y = f.sh - (pct.on() ? 44 : 34), n = 0; for (Object o : f.me.armor) if (o != null) n++;
            if (n == 0) return;
            pill(g, x - 4, y - 4, f.sw - 2, y + (pct.on() ? 34 : 24), 0x90141220);
            for (int i = 0; i < 4; i++) { Object o = f.me.armor[i]; double fr = f.me.armorFrac[i]; int ix = x + i * 24;
                if (o == null) { g.fill(ix + 1, y + 1, ix + 15, y + 15, 0x20FFFFFF); continue; }
                g.item(o, ix, y); int bc = mix(0xFFFF4D4D, 0xFF4DFF88, fr);
                g.fill(ix, y + 17, ix + 16, y + 19, 0x80000000); g.fill(ix, y + 17, ix + (int) (16 * fr), y + 19, bc);
                if (pct.on()) textC(g, (int) Math.round(fr * 100) + "%", ix + 8, y + 22, bc); }
        }
    }

    // ───────────── Ambience ─────────────
    static final class Ambience extends Module {
        final Setting vignette = bool("Виньетка", true), vStr = num("Сила виньетки", 150, 30, 230, 10), tint = bool("Тонировка", false),
            tintA = num("Сила тонировки", 28, 5, 90, 1), tintC = mode("Цвет тонировки", 0, "Тема", "Тёплый", "Холодный", "Тёмный");
        Ambience() { super("Ambience", "HUD", "Цветовой фильтр и виньетка экрана"); add(vignette, vStr, tint, tintA, tintC); }

        @Override void render(Frame f, Gfx g) {
            if (tint.on()) { int c = tintC.idx == 0 ? Theme.c() : tintC.idx == 1 ? 0xFFFF9A3C : tintC.idx == 2 ? 0xFF3C8CFF : 0xFF000000; g.fill(0, 0, f.sw, f.sh, alpha(c, tintA.i())); }
            if (vignette.on()) { int t = Math.max(2, f.sh / 54); for (int i = 0; i < 12; i++) { double k = 1 - i / 12.0; outline(g, i * t, i * t, f.sw - i * t, f.sh - i * t, alpha(0x000000, (int) (vStr.num() * k * k)), t); } }
        }
    }
}
