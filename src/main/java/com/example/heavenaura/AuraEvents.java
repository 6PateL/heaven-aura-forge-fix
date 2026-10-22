package com.example.heavenaura;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ефекти безкінечні (INFINITE_DURATION): Hourglass при запуску Made in Heaven прискорює спливання
 * тривалості ефектів, тому короткі ефекти мигали. Безкінечні не спливають, тож ми самі їх знімаємо,
 * коли артефакт прибрали, істота вийшла з радіусу, носій помер/вийшов або сервер зупиняється.
 */
@Mod.EventBusSubscriber(modid = HeavenAura.MOD_ID)
public final class AuraEvents {
    private static final String MARK = "heaven_aura_buffed";
    private static final Map<UUID, Set<UUID>> ACTIVE = new HashMap<>(); // носій -> ті, кого він зараз бафає

    private record Buff(MobEffect effect, int amplifier) {}

    private AuraEvents() {}

    // ------------------------------------------------------------ тік носія

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer holder)) return;
        AuraConfig cfg = AuraConfig.get();
        int interval = Math.max(1, cfg.refreshTicks);
        if (holder.tickCount % interval != 0) return;

        boolean holds = cfg.enabled && !holder.isSpectator() && holdsArtifact(holder, cfg);
        if (holds) pulse(holder, cfg, interval);
        else release(holder.getServer(), holder.getUUID());
    }

    private static boolean holdsArtifact(ServerPlayer p, AuraConfig cfg) {
        if (isArtifact(p.getMainHandItem(), cfg)) return true;
        return !cfg.mainHandOnly && isArtifact(p.getOffhandItem(), cfg);
    }

    private static boolean isArtifact(ItemStack stack, AuraConfig cfg) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && cfg.items.contains(id.toString());
    }

    // ------------------------------------------------------------ імпульс

    private static void pulse(ServerPlayer holder, AuraConfig cfg, int interval) {
        List<Buff> buffs = buildBuffs(cfg);
        Set<UUID> now = new HashSet<>();

        if (!buffs.isEmpty()) {
            apply(holder, buffs);
            now.add(holder.getUUID());

            double r = Math.max(1, cfg.radius);
            double r2 = r * r;
            int mobs = 0;

            for (LivingEntity e : holder.level().getEntitiesOfClass(LivingEntity.class, holder.getBoundingBox().inflate(r))) {
                if (e == holder || !e.isAlive() || e.distanceToSqr(holder) > r2) continue;
                if (e instanceof Enemy || e instanceof ArmorStand) continue;   // вороги й манекени — ні

                if (e instanceof Player other) {
                    if (cfg.affectPlayers && !other.isSpectator()) { apply(other, buffs); now.add(other.getUUID()); }
                    continue;
                }
                if (!cfg.affectMobs || !(e instanceof Mob mob)) continue;
                if (cfg.skipAggroedMobs && mob.getTarget() instanceof Player) continue;
                if (mobs++ >= cfg.maxMobTargets) continue;
                apply(mob, buffs);
                now.add(mob.getUUID());
            }
        }

        // хто випав з аури (відійшов далеко, почав атакувати) — знімаємо ефекти
        Set<UUID> prev = ACTIVE.put(holder.getUUID(), now);
        if (prev != null) {
            for (UUID id : prev) {
                if (!now.contains(id) && !buffedByAnyone(id)) strip(find(holder.getServer(), id));
            }
        }

        // ціна за використання
        if (cfg.holderExhaustionPerSecond > 0 && !holder.isCreative()) {
            holder.causeFoodExhaustion((float) (cfg.holderExhaustionPerSecond * interval / 20.0));
        }
    }

    private static void apply(LivingEntity target, List<Buff> buffs) {
        boolean icon = target instanceof Player;
        for (Buff b : buffs) {
            MobEffectInstance cur = target.getEffect(b.effect());
            if (cur != null && cur.isInfiniteDuration() && cur.getAmplifier() == b.amplifier()) continue; // вже є
            // ambient + без частинок; іконка лише у гравців
            target.addEffect(new MobEffectInstance(b.effect(), MobEffectInstance.INFINITE_DURATION,
                    b.amplifier(), true, false, icon));
        }
        target.getPersistentData().putBoolean(MARK, true);
    }

    // ------------------------------------------------------------ зняття

    /** Носій більше не тримає артефакт (або вийшов/помер): знімаємо все, що він давав. */
    private static void release(MinecraftServer server, UUID holderId) {
        Set<UUID> set = ACTIVE.remove(holderId);
        if (set == null || server == null) return;
        for (UUID id : set) {
            if (!buffedByAnyone(id)) strip(find(server, id));
        }
    }

    private static void strip(LivingEntity e) {
        if (e == null) return;
        for (MobEffect eff : ourEffectTypes()) {
            MobEffectInstance cur = e.getEffect(eff);
            if (cur != null && cur.isInfiniteDuration()) e.removeEffect(eff);   // чужі (скінченні) ефекти не чіпаємо
        }
        e.getPersistentData().remove(MARK);
    }

    private static boolean buffedByAnyone(UUID id) {
        for (Set<UUID> s : ACTIVE.values()) if (s.contains(id)) return true;
        return false;
    }

    private static LivingEntity find(MinecraftServer server, UUID id) {
        if (server == null) return null;
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }

    // ------------------------------------------------------------ страховка від «завислих» ефектів

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) release(p.getServer(), p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            release(p.getServer(), p.getUUID());
            strip(p);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getPersistentData().getBoolean(MARK) && !buffedByAnyone(p.getUUID())) {
            strip(p);     // залишилось від минулої сесії
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.getEntity() instanceof Player) return;
        if (event.getEntity() instanceof LivingEntity le && le.getPersistentData().getBoolean(MARK) && !buffedByAnyone(le.getUUID())) {
            strip(le);    // моб завантажився з ефектом, а носія вже немає
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (UUID holder : new ArrayList<>(ACTIVE.keySet())) release(event.getServer(), holder);
        ACTIVE.clear();
    }

    // ------------------------------------------------------------ ефекти з конфігу

    private static List<Buff> buildBuffs(AuraConfig cfg) {
        List<Buff> list = new ArrayList<>();
        if (cfg.strengthAmplifier >= 0) list.add(new Buff(MobEffects.DAMAGE_BOOST, cfg.strengthAmplifier));
        if (cfg.resistanceAmplifier >= 0) list.add(new Buff(MobEffects.DAMAGE_RESISTANCE, cfg.resistanceAmplifier));
        if (cfg.regenerationAmplifier >= 0) list.add(new Buff(MobEffects.REGENERATION, cfg.regenerationAmplifier));

        for (String s : AuraConfig.get().extraEffects) {          // формат "modid:effect:amplifier"
            int cut = s.lastIndexOf(':');
            if (cut <= 0) continue;
            try {
                ResourceLocation id = new ResourceLocation(s.substring(0, cut));
                int amp = Integer.parseInt(s.substring(cut + 1).trim());
                if (amp >= 0 && ForgeRegistries.MOB_EFFECTS.containsKey(id)) {
                    list.add(new Buff(ForgeRegistries.MOB_EFFECTS.getValue(id), amp));
                }
            } catch (RuntimeException ignored) {
                // кривий рядок у конфігу — пропускаємо
            }
        }
        return list;
    }

    /** Усі типи ефектів, які ми можемо давати (без урахування рівнів) — щоб коректно знімати. */
    private static List<MobEffect> ourEffectTypes() {
        List<MobEffect> list = new ArrayList<>(List.of(MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE, MobEffects.REGENERATION));
        for (String s : AuraConfig.get().extraEffects) {
            int cut = s.lastIndexOf(':');
            if (cut <= 0) continue;
            try {
                ResourceLocation id = new ResourceLocation(s.substring(0, cut));
                if (ForgeRegistries.MOB_EFFECTS.containsKey(id)) list.add(ForgeRegistries.MOB_EFFECTS.getValue(id));
            } catch (RuntimeException ignored) {
                // пропускаємо
            }
        }
        return list;
    }
}
