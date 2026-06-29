package net.fabricmc.fabric.api.client.rendering.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.GuiGraphics;

@FunctionalInterface
public interface HudRenderCallback {

    void onHudRender(GuiGraphics drawContext, float tickDelta);

    Event<HudRenderCallback> EVENT = EventFactory.createArrayBacked(HudRenderCallback.class,
        (listeners) -> (drawContext, tickDelta) -> {
            for (HudRenderCallback event : listeners) {
                event.onHudRender(drawContext, tickDelta);
            }
        }
    );
}
