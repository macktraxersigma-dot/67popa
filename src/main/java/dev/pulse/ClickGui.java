package dev.pulse;

import static dev.pulse.Draw.*;

/** Меню на Right Shift: тёмные скруглённые панели, плавные анимации, поиск и конфиг. Не зависит от версии игры. */
final class ClickGui {
    static final String[] CATS = {"Info", "Effects", "HUD"}, TITLES = {"ИНФОРМАЦИЯ", "ЭФФЕКТЫ", "ИНТЕРФЕЙС"};
    static final int PW = 116, GAP = 8, HEAD = 22, ROW = 16, RG = 2, SROW = 15, PAD = 4;
    static final int PANEL = 0xE6141220, BORDER = 0x30FFFFFF, TXT_OFF = 0xFF9C98B0;

    Module open; Setting drag; boolean pl, pr;
    String query = ""; long last = System.nanoTime(), savedAt; double appear;

    /** Ввод в строку поиска (символы и Backspace приходят из PulseScreen). */
    void type(String s, boolean back) {
        if (back && !query.isEmpty()) query = query.substring(0, query.length() - 1);
        if (!s.isEmpty() && query.length() < 16) query += s.toLowerCase();
    }

    /** Обёртка: плавное появление (множитель прозрачности). Текст с alpha<8 Minecraft рисует непрозрачным, поэтому пропускаем. */
    static final class Fade implements Gfx {
        final Gfx b; final double a;
        Fade(Gfx b, double a) { this.b = b; this.a = a; }
        public void fill(int x1, int y1, int x2, int y2, int c) { b.fill(x1, y1, x2, y2, mulA(c, a)); }
        public void text(String s, int x, int y, int c) { c = mulA(c, a); if ((c >>> 24) >= 8) b.text(s, x, y, c); }
        public int textWidth(String s) { return b.textWidth(s); }
        public void bold(String s, int x, int y, int c) { c = mulA(c, a); if ((c >>> 24) >= 8) b.bold(s, x, y, c); }
        public int boldWidth(String s) { return b.boldWidth(s); }
        public void item(Object st, int x, int y) { b.item(st, x, y); }
        public void gradient(int x1, int y1, int x2, int y2, int t, int bt) { b.gradient(x1, y1, x2, y2, mulA(t, a), mulA(bt, a)); }
    }

    static int settingsH(Module m) { return m.settings.size() * SROW + 4; }
    static int exH(Module m) { int rh = (int) (settingsH(m) * ease(m.ex)); return rh > 2 ? rh + RG : 0; }

    static int panelH(String cat) {
        int h = HEAD + PAD * 2;
        for (Module m : Pulse.mods) if (m.cat.equals(cat)) h += ROW + RG + exH(m);
        return h;
    }

    void draw(Gfx base, int sw, int sh, int mxr, int myr, boolean L, boolean R) {
        long nt = System.nanoTime(); double dt = Math.min(0.1, (nt - last) / 1e9); last = nt;
        appear = Math.min(1, appear + dt / 0.18); double ap = ease(appear), k = Math.min(1, dt * 16);
        double sc = Math.max(0.5, Math.min(1.0, Math.min((sw - 16.0) / (3 * PW + 2 * GAP), (sh - 16.0) / 330)));
        int vw = (int) (sw / sc) + 2, vh = (int) (sh / sc) + 2, mx = (int) (mxr / sc), my = (int) (myr / sc);
        boolean pressL = L && !pl, pressR = R && !pr; pl = L; pr = R; if (!L) drag = null;

        base.push(); base.scale((float) sc);
        Gfx g = new Fade(base, ap);
        g.gradient(0, 0, vw, vh, 0xC00A0814, 0xD2150E26);

        int total = 3 * PW + 2 * GAP, x0 = (vw - total) / 2, y0 = Math.max(36, vh / 2 - 135) + (int) ((1 - ap) * 12);
        Module hovered = null;
        for (int c = 0; c < 3; c++) {
            int x = x0 + c * (PW + GAP), y = y0, h = panelH(CATS[c]), acc = Theme.c(c * 0.3);
            round(g, x - 1, y - 1, x + PW + 1, y + h + 1, 5, BORDER);
            round(g, x, y, x + PW, y + h, 4, PANEL);
            round(g, x + 1, y + 1, x + PW - 1, y + HEAD - 1, 4, 0x16FFFFFF);
            textCB(g, TITLES[c], x + PW / 2.0, y + 7, 0xFFF4F2FF);
            g.fill(x + 14, y + HEAD - 1, x + PW - 14, y + HEAD, alpha(acc, 170));
            y += HEAD + PAD;
            for (Module m : Pulse.mods) {
                if (!m.cat.equals(CATS[c])) continue;
                double dim = query.isEmpty() || m.name.toLowerCase().contains(query) ? 1 : 0.28;
                int rx1 = x + PAD, rx2 = x + PW - PAD; boolean has = !m.settings.isEmpty();
                boolean hov = mx >= rx1 && mx < rx2 && my >= y && my < y + ROW;
                m.hv += ((hov ? 1 : 0) - m.hv) * k; m.an += ((m.on ? 1 : 0) - m.an) * k; m.ex += ((m == open ? 1 : 0) - m.ex) * k;
                if (hov) {
                    hovered = m;
                    if (pressL) { if (has && mx >= rx2 - 20) open = open == m ? null : m; else m.on = !m.on; }
                    if (pressR && has) open = open == m ? null : m;
                }
                int off = argb(14 + (int) (18 * m.hv), 255, 255, 255), on = alpha(Theme.c(y / 160.0), 120 + (int) (36 * m.hv));
                round(g, rx1, y, rx2, y + ROW, 3, mulA(mix(off, on, m.an), dim));
                g.text(m.name, rx1 + 6, y + 4, mulA(mix(TXT_OFF, 0xFFFFFFFF, m.an), dim));
                if (has) {
                    int dc = mulA(mix(0xFF5E5A72, 0xFFFFFFFF, Math.max(m.ex, m.hv * 0.6)), dim);
                    for (int i = 0; i < 3; i++) g.fill(rx2 - 15 + i * 4, y + ROW / 2 - 1, rx2 - 13 + i * 4, y + ROW / 2 + 1, dc);
                }
                y += ROW + RG;
                int rh = exH(m);
                if (rh > 0) {
                    int hh = rh - RG, sy = y + 2; boolean ready = m.ex > 0.9;
                    round(g, rx1 + 1, y, rx2 - 1, y + hh, 3, mulA(0x44000000, dim));
                    for (Setting s : m.settings) { if (sy - y + SROW <= hh) settingRow(g, s, rx1 + 7, sy, rx2 - rx1 - 14, mx, my, pressL && ready, pressR && ready, L && ready); sy += SROW; }
                    y += rh;
                }
            }
        }

        textCB(g, "Pulse Visual", vw / 2.0, y0 - 24, Theme.c());
        textC(g, hovered != null ? hovered.name + " — " + hovered.desc : "ЛКМ — вкл/выкл · ПКМ или ··· — настройки · печатай для поиска · Right Shift — закрыть", vw / 2.0, y0 - 12, hovered != null ? 0xFFB8B4CC : 0xFF6E6A82);

        int bw = 150, bx = (vw - bw) / 2, by = vh - 34;
        round(g, bx - 1, by - 1, bx + bw + 1, by + 17, 5, BORDER); round(g, bx, by, bx + bw, by + 16, 4, PANEL);
        boolean cur = (nt / 500_000_000L) % 2 == 0;
        g.text(query.isEmpty() ? "Поиск" : query + (cur ? "_" : ""), bx + 8, by + 4, query.isEmpty() ? 0xFF6E6A82 : 0xFFFFFFFF);

        int cw = 108, cx = vw - cw - 10, cy = vh - 80;
        if (cx > x0 + total + 6) {
            round(g, cx - 1, cy - 1, cx + cw + 1, cy + 2 * (ROW + RG) + HEAD + PAD * 2 + 1, 5, BORDER);
            round(g, cx, cy, cx + cw, cy + 2 * (ROW + RG) + HEAD + PAD * 2, 4, PANEL);
            textCB(g, "КОНФИГ", cx + cw / 2.0, cy + 7, 0xFFF4F2FF); g.fill(cx + 14, cy + HEAD - 1, cx + cw - 14, cy + HEAD, alpha(Theme.c(), 170));
            for (int i = 0; i < 2; i++) {
                int ry = cy + HEAD + PAD + i * (ROW + RG); boolean hov = mx >= cx + PAD && mx < cx + cw - PAD && my >= ry && my < ry + ROW;
                round(g, cx + PAD, ry, cx + cw - PAD, ry + ROW, 3, argb(hov ? 40 : 18, 255, 255, 255));
                boolean ok = i == 0 && System.currentTimeMillis() - savedAt < 1200;
                g.text(ok ? "Сохранено!" : i == 0 ? "Сохранить" : "Загрузить", cx + PAD + 6, ry + 4, hov || ok ? 0xFFFFFFFF : TXT_OFF);
                if (hov && pressL) { if (i == 0) { Pulse.save(); savedAt = System.currentTimeMillis(); } else Pulse.load(); }
            }
        }
        base.pop();
    }

    void settingRow(Gfx g, Setting s, int x, int y, int w, int mx, int my, boolean pressL, boolean pressR, boolean L) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + SROW; int acc = Theme.c();
        g.text(s.name, x, y + 4, 0xFFB8B4CC);
        if (s.type == Setting.NUM) {
            int bx = x + w * 50 / 100, bw = w - w * 50 / 100 - 26, fw = (int) (bw * (s.v - s.min) / (s.max - s.min));
            round(g, bx, y + 6, bx + bw, y + 9, 1, 0x30FFFFFF);
            if (fw >= 2) round(g, bx, y + 6, bx + fw, y + 9, 1, acc);
            round(g, bx + fw - 2, y + 4, bx + fw + 2, y + 11, 2, 0xFFFFFFFF);
            g.text(s.show(), x + w - g.textWidth(s.show()), y + 4, 0xFFFFFFFF);
            if (pressL && mx >= bx - 3 && mx <= bx + bw + 3 && my >= y && my < y + SROW) drag = s;
            if (drag == s && L) s.setNum(s.min + (mx - bx) / (double) bw * (s.max - s.min));
        } else {
            if (s.type == Setting.BOOL) {
                round(g, x + w - 18, y + 3, x + w, y + 12, 4, s.b ? acc : 0xFF2A2738);
                int kx = s.b ? x + w - 9 : x + w - 17; round(g, kx, y + 4, kx + 7, y + 11, 3, 0xFFFFFFFF);
            } else { String v = s.show(); g.text(v, x + w - g.textWidth(v), y + 4, acc); }
            if (hov && pressL) { if (s.type == Setting.BOOL) s.b = !s.b; else s.cycle(1); }
            if (hov && pressR && s.type == Setting.MODE) s.cycle(-1);
        }
    }
}
