package autosawmill.common.compat;

import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages soft-dependency detection across optional companion mods.
 */
public final class CompatManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompatManager.class);

    public static final String MOD_TFC = "tfc";
    public static final String MOD_TFC_DEBARK = "tfc_debark";
    public static final String MOD_TFC_LUMBERJACK = "tfc_lumberjack";
    public static final String MOD_TFC_ITEMS = "tfc_items";
    public static final String MOD_TFC_METAL_TOOLS = "tfc_metal_tools";

    private static boolean debarkLoaded;
    private static boolean lumberjackLoaded;
    private static boolean tfcItemsLoaded;
    private static boolean metalToolsLoaded;
    private static boolean initialized = false;

    private CompatManager() {}

    /**
     * Initializes and caches the status of all companion mods.
     */
    public static void init() {
        if (initialized) return;

        final ModList modList = ModList.get();
        debarkLoaded = modList.isLoaded(MOD_TFC_DEBARK);
        lumberjackLoaded = modList.isLoaded(MOD_TFC_LUMBERJACK);
        tfcItemsLoaded = modList.isLoaded(MOD_TFC_ITEMS);
        metalToolsLoaded = modList.isLoaded(MOD_TFC_METAL_TOOLS);
        initialized = true;

        LOGGER.info("[AutoSawmill] Compat status detected:");
        LOGGER.info("  - TFC Debark:       {}", debarkLoaded ? "ACTIVE" : "ABSENT");
        LOGGER.info("  - TFC Lumberjack:   {}", lumberjackLoaded ? "ACTIVE" : "ABSENT");
        LOGGER.info("  - TFC More Items:   {}", tfcItemsLoaded ? "ACTIVE" : "ABSENT (fallback brass gear will be registered)");
        LOGGER.info("  - TFC Metal Tools:  {}", metalToolsLoaded ? "ACTIVE" : "ABSENT");
    }

    public static boolean isDebarkLoaded() {
        ensureInitialized();
        return debarkLoaded;
    }

    public static boolean isLumberjackLoaded() {
        ensureInitialized();
        return lumberjackLoaded;
    }

    public static boolean isTfcItemsLoaded() {
        ensureInitialized();
        return tfcItemsLoaded;
    }

    public static boolean isMetalToolsLoaded() {
        ensureInitialized();
        return metalToolsLoaded;
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    private static void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }
}
