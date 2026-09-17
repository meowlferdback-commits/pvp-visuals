package com.example.pvpvisuals.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Отслеживает изменение здоровья видимых LivingEntity между тиками
 * и спавнит декоративные партиклы, когда health падает (= сущность получила урон).
 *
 * Это чисто визуальный, клиентский эффект — не влияет на геймплей/урон.
 * Для полностью синхронного эффекта (без задержки в 1 тик) на своём сервере
 * можно вместо этого слать кастомный пакет через ServerPlayNetworking при уроне.
 */
public final class HitParticleHandler {

    private static final Map<Integer, Float> lastHealth = new HashMap<>();

    private HitParticleHandler() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;

            for (Entity entity : client.world.getEntities()) {
                if (!(entity instanceof LivingEntity living)) continue;

                float currentHealth = living.getHealth();
                Float previous = lastHealth.get(living.getId());

                if (previous != null && currentHealth < previous && currentHealth > 0) {
                    spawnHitParticles(client, living, previous - currentHealth);
                }

                lastHealth.put(living.getId(), currentHealth);
            }

            // чистим мёртвые записи, чтобы не копилась память
            lastHealth.keySet().removeIf(id -> client.world.getEntityById(id) == null);
        });
    }

    private static void spawnHitParticles(MinecraftClient client, LivingEntity living, float damage) {
        Vec3d pos = living.getPos().add(0, living.getHeight() * 0.6, 0);
        int count = (int) Math.min(12, 4 + damage * 1.5f);

        for (int i = 0; i < count; i++) {
            double ox = (client.world.random.nextDouble() - 0.5) * living.getWidth();
            double oy = client.world.random.nextDouble() * living.getHeight() * 0.5;
            double oz = (client.world.random.nextDouble() - 0.5) * living.getWidth();

            client.world.addParticle(
                    ParticleTypes.CRIT,
                    pos.x + ox, pos.y + oy, pos.z + oz,
                    0, 0.05, 0
            );
        }

        // лёгкая дополнительная вспышка урона покрупнее
        client.world.addParticle(
                ParticleTypes.SWEEP_ATTACK,
                pos.x, pos.y, pos.z,
                0, 0, 0
        );
    }
}
