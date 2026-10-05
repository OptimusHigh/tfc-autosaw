package autosawmill.client.event;

import autosawmill.client.render.SawmillBlockEntityRenderer;
import autosawmill.common.blockentity.ModBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Handles client-side event registrations for NeoForge.
 * Strictly isolated to Dist.CLIENT to prevent class-loading crashes on dedicated servers.
 */
public final class ModClientEvents {

    public static void init(IEventBus bus) {
        bus.addListener(ModClientEvents::registerRenderers);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SAWMILL.get(), SawmillBlockEntityRenderer::new);
    }

    private ModClientEvents() {}
}
