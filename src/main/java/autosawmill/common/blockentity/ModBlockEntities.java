package autosawmill.common.blockentity;

import autosawmill.AutoSawmill;
import autosawmill.common.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AutoSawmill.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupportPileBlockEntity>> SUPPORT_PILE =
            BLOCK_ENTITIES.register("support_pile", () ->
                    BlockEntityType.Builder.of(SupportPileBlockEntity::new, ModBlocks.SUPPORT_PILE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SawmillBlockEntity>> SAWMILL =
            BLOCK_ENTITIES.register("sawmill", () ->
                    BlockEntityType.Builder.of(SawmillBlockEntity::new, ModBlocks.SAWMILL.get()).build(null));

    private ModBlockEntities() {}
}
