
package dev.pulse;

import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ЕДИНСТВЕННЫЙ файл (вместе с PulseClient и PulseScreen),
 * который обращается к классам Minecraft.
 */
final class McPlatform {
    private static Map<Integer, double[]> prev = new HashMap<>(), cur = new HashMap<>();
    private static long tickNs = System.nanoTime();
    private static List<Data.Mark> marks = new ArrayList<>();
    private static int scanTimer, totems;
    private static List<Data.Ent> snaps = new ArrayList<>(); // тяжёлые снимки сущностей: раз в тик, а не в каждый кадр

    // ───── рисование ─────
    static final class McGfx implements Gfx {
        private final DrawContext c;
        private final TextRenderer t;

        McGfx(DrawContext c, TextRenderer t) {
            this.c = c;
            this.t = t;
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int argb) {
            c.fill(x1, y1, x2, y2, argb);
        }

        private static final StyleSpriteSource UI = new StyleSpriteSource.Font(Identifier.of("pulse", "ui")),
                BOLD = new StyleSpriteSource.Font(Identifier.of("pulse", "bold"));
        private static final Map<String, Text> UI_T = new HashMap<>(), BOLD_T = new HashMap<>();
        private static final Map<String, Integer> UI_W = new HashMap<>(), BOLD_W = new HashMap<>();

        private static Text styled(Map<String, Text> cache, StyleSpriteSource f, String s) {
            Text t = cache.get(s);
            if (t == null) {
                if (cache.size() > 600) cache.clear();
                t = Text.literal(s).setStyle(Style.EMPTY.withFont(f));
                cache.put(s, t);
            }
            return t;
        }

        @Override
        public void text(String s, int x, int y, int argb) {
            c.drawText(t, styled(UI_T, UI, s), x, y, argb, false);
        }

        @Override
        public void bold(String s, int x, int y, int argb) {
            c.drawText(t, styled(BOLD_T, BOLD, s), x, y, argb, false);
        }

        @Override
        public int textWidth(String s) {
            Integer w = UI_W.get(s);
            if (w == null) { if (UI_W.size() > 600) UI_W.clear(); w = t.getWidth(styled(UI_T, UI, s)); UI_W.put(s, w); }
            return w;
        }

        @Override
        public int boldWidth(String s) {
            Integer w = BOLD_W.get(s);
            if (w == null) { if (BOLD_W.size() > 600) BOLD_W.clear(); w = t.getWidth(styled(BOLD_T, BOLD, s)); BOLD_W.put(s, w); }
            return w;
        }

        @Override
        public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
            c.fillGradient(x1, y1, x2, y2, top, bottom);
        }

        @Override
        public void push() {
            c.getMatrices().pushMatrix();
        }

        @Override
        public void pop() {
            c.getMatrices().popMatrix();
        }

        @Override
        public void scale(float s) {
            c.getMatrices().scale(s, s);
        }

        @Override
        public void item(Object stack, int x, int y) {
            c.drawItem((ItemStack) stack, x, y);
        }
    }

    // ───── тик: позиции, снимки сущностей, скан контейнеров ─────
    static void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;

        if (w == null || mc.player == null) {
            prev.clear();
            cur.clear();
            snaps = new ArrayList<>();
            marks = new ArrayList<>();
            return;
        }

        prev = cur;
        cur = new HashMap<>();
        tickNs = System.nanoTime();

        List<Data.Ent> list = new ArrayList<>();

        for (Entity e : w.getEntities()) {
            cur.put(e.getId(), new double[]{e.getX(), e.getY(), e.getZ()});

            if (e == mc.player || e.isSpectator() || e instanceof ArmorStandEntity) continue;

            Data.Ent d = ent(e, 1.0);
            if (d != null) list.add(d);
        }

        snaps = list;

        int n = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING)) n += s.getCount();
        }
        totems = n;

        if (--scanTimer <= 0) {
            scanTimer = 20;
            scan(mc, w);
        }
    }

    /** Ищем контейнеры по блок-сущностям загруженных чанков (раньше перебирался куб до 65x65x65 блоков раз в секунду). */
    private static void scan(MinecraftClient mc, ClientWorld w) {
        List<Data.Mark> out = new ArrayList<>();

        if (!Pulse.esp.on || !Pulse.esp.blocks.on()) {
            marks = out;
            return;
        }

        int r = (int) Pulse.esp.range.num();
        long r2 = (long) r * r;
        BlockPos o = mc.player.getBlockPos();
        int pcx = o.getX() >> 4, pcz = o.getZ() >> 4, cr = (r >> 4) + 1;

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) continue;

                for (BlockEntity be : ch.getBlockEntities().values()) {
                    String t =
                            be instanceof EnderChestBlockEntity ? "ender" :
                            be instanceof ChestBlockEntity ? "chest" :
                            be instanceof ShulkerBoxBlockEntity ? "shulker" :
                            be instanceof BarrelBlockEntity ? "barrel" :
                            null;

                    if (t == null) continue;

                    BlockPos bp = be.getPos();
                    long dx = bp.getX() - o.getX(), dy = bp.getY() - o.getY(), dz = bp.getZ() - o.getZ();
                    if (dx * dx + dy * dy + dz * dz > r2) continue;

                    Data.Mark k = new Data.Mark();
                    k.x = bp.getX();
                    k.y = bp.getY();
                    k.z = bp.getZ();
                    k.type = t;
                    out.add(k);
                }
            }
        }

        marks = out;
    }

    // ───── снимок кадра ─────
    static Data.Frame frame() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity pl = mc.player;
        ClientWorld w = mc.world;

        if (pl == null || w == null) return null;

        Data.Frame f = new Data.Frame();
        f.sw = mc.getWindow().getScaledWidth();
        f.sh = mc.getWindow().getScaledHeight();
        f.now = System.currentTimeMillis();

        double t = Math.max(
                0,
                Math.min(1, (System.nanoTime() - tickNs) / 50_000_000.0)
        );

        // Исправлено для Minecraft 1.21.11
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = cam.getCameraPos();

        f.cam.x = cp.x;
        f.cam.y = cp.y;
        f.cam.z = cp.z;
        f.cam.yaw = cam.getYaw();
        f.cam.pitch = cam.getPitch();
        f.cam.fov = mc.options.getFov().getValue();

        Data.Self m = f.me;

        m.name = pl.getName().getString();
        m.x = pl.getX();
        m.y = pl.getY();
        m.z = pl.getZ();
        m.eyeY = pl.getEyeY();

        m.yaw = pl.getYaw();
        m.pitch = pl.getPitch();
        m.hp = pl.getHealth();
        m.maxHp = pl.getMaxHealth();
        m.absorb = pl.getAbsorptionAmount();

        m.onGround = pl.isOnGround();
        m.vy = pl.getVelocity().y;
        m.useTicks = pl.getItemUseTime();
        m.cooldown = pl.getAttackCooldownProgress(0.5f);

        m.fps = mc.getCurrentFps();

        PlayerListEntry me = mc.getNetworkHandler() == null
                ? null
                : mc.getNetworkHandler().getPlayerListEntry(pl.getUuid());

        m.ping = me == null ? 0 : me.getLatency();

        m.held = held(pl.getMainHandStack());

        if (m.held.isEmpty()) {
            m.held = held(pl.getOffHandStack());
        }

        armor(pl, m.armor, m.armorFrac);
        effects(pl, m.eff);

        m.totems = totems;

        int tid = mc.targetedEntity == null ? -1 : mc.targetedEntity.getId();

        for (Data.Ent d : snaps) {
            double[] a = prev.get(d.id), b = cur.get(d.id);

            if (a != null && b != null) {
                d.x = a[0] + (b[0] - a[0]) * t;
                d.y = a[1] + (b[1] - a[1]) * t;
                d.z = a[2] + (b[2] - a[2]) * t;
            }

            f.ents.add(d);
            if (d.id == tid) f.target = d;
        }

        f.marks = marks;

        f.ray = (x1, y1, z1, x2, y2, z2) -> {
            HitResult h = w.raycast(
                    new RaycastContext(
                            new Vec3d(x1, y1, z1),
                            new Vec3d(x2, y2, z2),
                            RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE,
                            pl
                    )
            );

            if (h.getType() == HitResult.Type.MISS) {
                return null;
            }

            Vec3d p = h.getPos();

            return new double[]{p.x, p.y, p.z};
        };

        return f;
    }

    /** Снимок одной сущности (для события удара и для кадра). */
    static Data.Ent ent(Entity e, double t) {
        Data.Ent d = new Data.Ent();

        if (e instanceof PlayerEntity) {
            d.kind = "player";
        } else if (e instanceof ItemEntity) {
            d.kind = "item";
        } else if (e instanceof LivingEntity) {
            d.kind = "mob";
        } else {
            return null;
        }

        d.id = e.getId();
        d.name = e.getName().getString();
        d.invisible = e.isInvisible();
        d.w = e.getWidth();
        d.h = e.getHeight();

        double[] a = prev.get(d.id);
        double[] b = cur.get(d.id);

        if (a != null && b != null) {
            d.x = a[0] + (b[0] - a[0]) * t;
            d.y = a[1] + (b[1] - a[1]) * t;
            d.z = a[2] + (b[2] - a[2]) * t;
        } else {
            d.x = e.getX();
            d.y = e.getY();
            d.z = e.getZ();
        }

        if (e instanceof LivingEntity le) {
            d.hp = le.getHealth();
            d.maxHp = le.getMaxHealth();
            d.hostile = e instanceof HostileEntity;

            armor(le, d.armor, d.armorFrac);
            effects(le, d.eff);

            if (e instanceof PlayerEntity) {
                MinecraftClient mc = MinecraftClient.getInstance();

                PlayerListEntry p = mc.getNetworkHandler() == null
                        ? null
                        : mc.getNetworkHandler().getPlayerListEntry(e.getUuid());

                d.ping = p == null ? -1 : p.getLatency();
            }
        }

        if (e instanceof ItemEntity ie) {
            d.itemName = ie.getStack().getName().getString();
            d.count = ie.getStack().getCount();
            d.maxHp = 0;
        }

        return d;
    }

    private static void armor(
            LivingEntity e,
            Object[] out,
            double[] frac
    ) {
        EquipmentSlot[] s = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        for (int i = 0; i < 4; i++) {
            ItemStack st = e.getEquippedStack(s[i]);

            out[i] = st.isEmpty() ? null : st;

            frac[i] = st.isEmpty()
                    ? -1
                    : st.isDamageable()
                        ? 1.0 - st.getDamage() / (double) st.getMaxDamage()
                        : 1.0;
        }
    }

    private static void effects(
            LivingEntity e,
            List<Data.Eff> out
    ) {
        for (StatusEffectInstance i : e.getStatusEffects()) {
            out.add(
                    new Data.Eff(
                            i.getEffectType().value().getName().getString(),
                            i.getDuration(),
                            i.getAmplifier()
                    )
            );
        }
    }

    private static String held(ItemStack s) {
        if (s.isEmpty()) return "";

        if (s.isOf(Items.ENDER_PEARL)) return "pearl";
        if (s.isOf(Items.SNOWBALL)) return "snowball";
        if (s.isOf(Items.EGG)) return "egg";
        if (s.isOf(Items.SPLASH_POTION)) return "splash";
        if (s.isOf(Items.LINGERING_POTION)) return "lingering";
        if (s.isOf(Items.EXPERIENCE_BOTTLE)) return "xp";
        if (s.isOf(Items.TRIDENT)) return "trident";
        if (s.isOf(Items.BOW)) return "bow";
        if (s.isOf(Items.CROSSBOW)) return "crossbow";

        return "";
    }
}
