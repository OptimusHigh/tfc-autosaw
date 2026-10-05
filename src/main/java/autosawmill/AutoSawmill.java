package autosawmill;

import autosawmill.common.compat.CompatManager;
import autosawmill.common.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the AutoSawmill mod.
 */
@Mod(AutoSawmill.MOD_ID)
public class AutoSawmill {
    public static final String MOD_ID = "autosawmill";
    public static final Logger LOGGER = LoggerFactory.getLogger(AutoSawmill.class);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_MODE_TABS.register(MOD_ID, () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MOD_ID))
                    .icon(() -> new ItemStack(ModItems.SAW_BLADE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SAWMILL.get());
                        output.accept(ModItems.SAW_BLADE.get());
                        if (ModItems.BRASS_GEAR != null) {
                            output.accept(ModItems.BRASS_GEAR.get());
                        }
                    })
                    .build());

    public AutoSawmill(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[AutoSawmill] Initializing mod lifecycle for NeoForge 1.21.1...");

        // Initialize compat checks early in mod constructor
        CompatManager.init();

        // Register deferred registries on the mod event bus
        ModItems.ITEMS.register(modEventBus);
        autosawmill.common.block.ModBlocks.BLOCKS.register(modEventBus);
        autosawmill.common.blockentity.ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Lifecycle event listeners
        modEventBus.addListener(this::commonSetup);

        // Client initialization
        if (FMLEnvironment.dist.isClient()) {
            autosawmill.client.event.ModClientEvents.init(modEventBus);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[AutoSawmill] Common setup finished. Ready to power sawmills!");
        });
    }

    /**
     * Helper to construct mod resource locations.
     */
    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
