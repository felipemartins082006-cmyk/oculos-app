package com.martins.estruturas;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * Estrutura configurável: o campo "tipo" no JSON (data/estruturas/worldgen/structure)
 * escolhe qual construção será gerada.
 */
public class EstruturaMartins extends Structure {
    public static final MapCodec<EstruturaMartins> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            TipoEstrutura.CODEC.fieldOf("tipo").forGetter(e -> e.tipo)
    ).apply(i, EstruturaMartins::new));

    private final TipoEstrutura tipo;

    public EstruturaMartins(StructureSettings settings, TipoEstrutura tipo) {
        super(settings);
        this.tipo = tipo;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX();
        int z = ctx.chunkPos().getMiddleBlockZ();
        int y = ctx.chunkGenerator().getFirstOccupiedHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        // Não gera em cima de água ou lava.
        var coluna = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        if (!coluna.getBlock(y).getFluidState().isEmpty()) {
            return Optional.empty();
        }
        return onTopOfChunkCenter(ctx, Heightmap.Types.WORLD_SURFACE_WG, pecas -> pecas.addPiece(
                new PecaMartins(tipo, ctx.random(), ctx.chunkPos().getMinBlockX(), ctx.chunkPos().getMinBlockZ())));
    }

    @Override
    public StructureType<?> type() {
        return EstruturasMod.ESTRUTURA;
    }
}
