package com.example.pvpvisuals.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Точка входа клиентской части мода.
 * Регистрирует:
 *  - рендер target ESP поверх сущности, на которую наведена камера/атакуется;
 *  - обработчик партиклов при попадании (см. HitParticleHandler);
 *  - клавишу для переключения стиля ESP (круг / ромб / призрак / выкл).
 */
public class PvpVisualsClient implements ClientModInitializer {

    // Текущий выбранный стиль ESP, доступен другим классам мода
    public static EspStyle currentStyle = EspStyle.CIRCLE;

    private static KeyBinding toggleStyleKey;

    @Override
    public void onInitializeClient() {
        // Клавиша по умолчанию: ] — переключает стиль ESP по кругу
        toggleStyleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpvisuals.toggle_style",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_BRACKET,
                "category.pvpvisuals"
        ));

        // Рендер ESP каждый кадр, после отрисовки сущностей (чтобы быть поверх модели)
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (currentStyle != EspStyle.OFF) {
                TargetEspRenderer.render(context);
            }
        });

        // Партиклы при попадании по цели
        HitParticleHandler.register();

        // Тик клиента — проверяем нажатие клавиши переключения стиля
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleStyleKey.wasPressed()) {
                currentStyle = currentStyle.next();
                if (client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("PvP Visuals: стиль ESP — " + currentStyle.displayName),
                            true
                    );
                }
            }
        });
    }

    /**
     * Доступные визуальные стили таргет-ESP.
     */
    public enum EspStyle {
        OFF("Выкл"),
        CIRCLE("Круг"),
        DIAMOND("Ромб"),
        GHOST("Призрак");

        public final String displayName;

        EspStyle(String displayName) {
            this.displayName = displayName;
        }

        public EspStyle next() {
            EspStyle[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
}
