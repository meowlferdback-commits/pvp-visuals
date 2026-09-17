package com.example.pvpvisuals.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Отрисовывает косметический индикатор вокруг сущности, на которую сейчас
 * наведён прицел игрока. Три стиля:
 *  - CIRCLE:  вращающееся кольцо у ног цели, "бегает" туда-сюда во время атаки;
 *  - DIAMOND: пульсирующий ромб над головой цели;
 *  - GHOST:   полупрозрачный контур-силуэт (упрощённо — вертикальный овал), мигающий.
 *
 * Примечание: часть рендер-API (RenderLayer, VertexConsumerProvider.Immediate)
 * может отличаться по именам между минорными версиями Minecraft/Yarn —
 * при сборке под другую версию сверься с маппингами через Linkie/Loom.
 */
public final class TargetEspRenderer {

    private static final double RANGE = 6.0; // дальность обнаружения цели, блоков

    private TargetEspRenderer() {}

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Entity target = findTarget(client);
        if (!(target instanceof LivingEntity living) || target == client.player) return;

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider.Immediate vcp = client.getBufferBuilders().getEntityVertexConsumers();

        Vec3d camPos = context.camera().getPos();
        double x = MathHelper.lerp(context.tickCounter().getTickDelta(true), living.lastRenderX, living.getX()) - camPos.x;
        double y = MathHelper.lerp(context.tickCounter().getTickDelta(true), living.lastRenderY, living.getY()) - camPos.y;
        double z = MathHelper.lerp(context.tickCounter().getTickDelta(true), living.lastRenderZ, living.getZ()) - camPos.z;

        float age = living.age + context.tickCounter().getTickDelta(true);

        matrices.push();
        matrices.translate(x, y, z);

        switch (PvpVisualsClient.currentStyle) {
            case CIRCLE -> renderCircle(matrices, vcp, age, living.getWidth());
            case DIAMOND -> renderDiamond(matrices, vcp, age, living.getHeight());
            case GHOST -> renderGhost(matrices, vcp, age, living.getWidth(), living.getHeight());
            default -> {}
        }

        matrices.pop();
        vcp.draw();
    }

    /** Находит сущность под прицелом (или последнюю атакованную цель) в пределах RANGE. */
    private static Entity findTarget(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit) {
            double dist = client.player.getPos().distanceTo(entityHit.getEntity().getPos());
            if (dist <= RANGE) return entityHit.getEntity();
        }
        return null;
    }

    // --- CIRCLE: кольцо у ног, точка "бегает" по окружности во время атаки ---
    private static void renderCircle(MatrixStack matrices, VertexConsumerProvider.Immediate vcp, float age, float radius) {
        VertexConsumer buffer = vcp.getBuffer(RenderLayer.getLineStrip());
        Matrix4f pose = matrices.peek().getPositionMatrix();

        int segments = 48;
        float r = radius * 0.9f;
        // "бегущая" точка — доп. яркий сегмент, смещающийся туда-сюда (осциллирует)
        float runnerPhase = (MathHelper.sin(age * 0.15f) + 1f) / 2f; // 0..1 туда-сюда

        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            float angle = t * MathHelper.TAU;
            float px = MathHelper.cos(angle) * r;
            float pz = MathHelper.sin(angle) * r;

            boolean isRunner = Math.abs(t - runnerPhase) < 0.03f;
            float rC = isRunner ? 1.0f : 0.2f;
            float gC = isRunner ? 0.85f : 0.9f;
            float bC = isRunner ? 0.2f : 1.0f;
            float aC = isRunner ? 1.0f : 0.55f;

            buffer.vertex(pose, px, 0.05f, pz).color(rC, gC, bC, aC);
        }
    }

    // --- DIAMOND: пульсирующий ромб над головой цели ---
    private static void renderDiamond(MatrixStack matrices, VertexConsumerProvider.Immediate vcp, float age, float entityHeight) {
        matrices.push();
        matrices.translate(0, entityHeight + 0.5, 0);
        // всегда развёрнут к камере
        matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());

        float pulse = 0.9f + 0.1f * MathHelper.sin(age * 0.2f);
        float size = 0.35f * pulse;

        VertexConsumer buffer = vcp.getBuffer(RenderLayer.getLineStrip());
        Matrix4f pose = matrices.peek().getPositionMatrix();

        float[][] pts = { {0, size}, {size, 0}, {0, -size}, {-size, 0}, {0, size} };
        for (float[] p : pts) {
            buffer.vertex(pose, p[0], p[1], 0).color(1.0f, 0.25f, 0.25f, 0.9f);
        }
        matrices.pop();
    }

    // --- GHOST: мерцающий полупрозрачный контур поверх силуэта цели ---
    private static void renderGhost(MatrixStack matrices, VertexConsumerProvider.Immediate vcp, float age, float width, float height) {
        VertexConsumer buffer = vcp.getBuffer(RenderLayer.getLineStrip());
        Matrix4f pose = matrices.peek().getPositionMatrix();

        float flicker = 0.4f + 0.3f * MathHelper.sin(age * 0.3f) + 0.3f * (float) Math.random();
        float w = width * 0.6f;

        // простой вертикальный овал-контур как "силуэт призрака"
        int segments = 24;
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments * MathHelper.TAU;
            float px = MathHelper.cos(t) * w;
            float py = height / 2f + MathHelper.sin(t) * (height / 2f);
            buffer.vertex(pose, px, py, 0).color(0.7f, 0.9f, 1.0f, MathHelper.clamp(flicker, 0.15f, 0.6f));
        }
    }
}
