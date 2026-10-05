package autosawmill.common.block;

import autosawmill.AutoSawmill;
import autosawmill.common.blockentity.ModBlockEntities;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AutoSawmill.MOD_ID);

    public static final DeferredBlock<SupportPileBlock> SUPPORT_PILE = BLOCKS.register("support_pile",
            () -> new SupportPileBlock(ExtendedProperties.of(MapColor.WOOD)
                    .strength(0.6F)
                    .sound(SoundType.WOOD)
                    .flammable(60, 30)
                    .blockEntity(ModBlockEntities.SUPPORT_PILE)));

    public static final DeferredBlock<SawmillBlock> SAWMILL = BLOCKS.register("sawmill",
            () -> new SawmillBlock(ExtendedProperties.of(MapColor.METAL)
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .blockEntity(ModBlockEntities.SAWMILL)
                    .serverTicks(autosawmill.common.blockentity.SawmillBlockEntity::serverTick)));

    private ModBlocks() {}
}
