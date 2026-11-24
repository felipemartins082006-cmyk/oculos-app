package com.martins.estruturas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * Peça que constrói bloco a bloco cada estrutura do mod.
 * Coordenadas são locais: x = largura, y = altura (0 = chão), z = profundidade (0 = frente).
 * O Minecraft gira/espelha tudo automaticamente conforme a direção sorteada.
 */
public class PecaMartins extends ScatteredFeaturePiece {
    private final TipoEstrutura tipo;

    public PecaMartins(TipoEstrutura tipo, RandomSource random, int x, int z) {
        super(EstruturasMod.PECA, x, 64, z, tipo.largura, tipo.altura, tipo.profundidade,
                getRandomHorizontalDirection(random));
        this.tipo = tipo;
    }

    public PecaMartins(CompoundTag tag) {
        super(EstruturasMod.PECA, tag);
        this.tipo = TipoEstrutura.porNome(tag.getStringOr("Tipo", TipoEstrutura.TORRE_DO_MAGO.nome));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext contexto, CompoundTag tag) {
        super.addAdditionalSaveData(contexto, tag);
        tag.putString("Tipo", tipo.nome);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager estruturas, ChunkGenerator gerador,
                            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivo) {
        // Assenta a construção na altura média do terreno (o chão substitui a camada de grama).
        if (!updateAverageGroundHeight(level, box, -1)) return;

        // Limpa árvores/grama/terra dentro da área.
        generateAirBox(level, box, 0, 1, 0, tipo.largura - 1, tipo.altura - 1, tipo.profundidade - 1);

        switch (tipo) {
            case TORRE_DO_MAGO -> torreDoMago(level, random, box);
            case RUINAS_ANTIGAS -> ruinasAntigas(level, random, box);
            case CABANA_DO_CACADOR -> cabanaDoCacador(level, random, box);
        }
    }

    // ------------------------------------------------------------------ Torre do Mago

    private void torreDoMago(WorldGenLevel level, RandomSource random, BoundingBox box) {
        final int c = 4; // centro
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                int dx = x - c, dz = z - c;
                if (!circulo(dx, dz, 4)) continue;
                boolean parede = !circulo(dx, dz, 3);

                // Alicerce até o chão e pisos dos três andares.
                fillColumnDown(level, Blocks.STONE_BRICKS.defaultBlockState(), x, -1, z, box);
                placeBlock(level, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), x, 0, z, box);
                if (!parede) {
                    placeBlock(level, Blocks.SPRUCE_PLANKS.defaultBlockState(), x, 8, z, box);
                    placeBlock(level, Blocks.SPRUCE_PLANKS.defaultBlockState(), x, 16, z, box);
                }
                if (parede) {
                    for (int y = 1; y <= 19; y++) {
                        placeBlock(level, pedraVelha(random), x, y, z, box);
                    }
                }
            }
        }

        // Telhado em cone com ponta de ametista.
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                int dx = x - c, dz = z - c;
                for (int camada = 0; camada < 3; camada++) {
                    if (circulo(dx, dz, 4 - camada)) {
                        placeBlock(level, Blocks.DEEPSLATE_TILES.defaultBlockState(), x, 20 + camada, z, box);
                    }
                }
            }
        }
        placeBlock(level, Blocks.AMETHYST_BLOCK.defaultBlockState(), c, 23, c, box);

        // Porta (abertura) na frente e janelas nos três andares.
        placeBlock(level, Blocks.AIR.defaultBlockState(), c, 1, 0, box);
        placeBlock(level, Blocks.AIR.defaultBlockState(), c, 2, 0, box);
        BlockState vidro = Blocks.GLASS_PANE.defaultBlockState();
        for (int y : new int[]{4, 5, 12, 13, 18}) {
            placeBlock(level, vidro, 0, y, c, box);
            placeBlock(level, vidro, 8, y, c, box);
        }

        // Escada de mão no fundo, atravessando os pisos.
        BlockState escada = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
        for (int y = 1; y <= 17; y++) {
            placeBlock(level, escada, c, y, 7, box);
        }

        // Térreo: barril e lanterna.
        placeBlock(level, Blocks.BARREL.defaultBlockState(), 2, 1, 6, box);
        placeBlock(level, lanternaPendurada(), c, 7, c, box);

        // 2º andar: biblioteca.
        for (int y = 9; y <= 10; y++) {
            for (int z = 3; z <= 5; z++) {
                placeBlock(level, Blocks.BOOKSHELF.defaultBlockState(), 1, y, z, box);
                placeBlock(level, Blocks.BOOKSHELF.defaultBlockState(), 7, y, z, box);
            }
        }
        placeBlock(level, lanternaPendurada(), c, 15, c, box);

        // Topo: sala do mago com mesa de encantamento e o tesouro.
        placeBlock(level, Blocks.ENCHANTING_TABLE.defaultBlockState(), c, 17, c, box);
        placeBlock(level, Blocks.CANDLE.defaultBlockState(), 6, 17, 3, box);
        placeBlock(level, Blocks.CANDLE.defaultBlockState(), 6, 17, 5, box);
        placeBlock(level, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), c, 19, c, box);
        createChest(level, box, random, 2, 17, c, BuiltInLootTables.WOODLAND_MANSION);
    }

    // ------------------------------------------------------------------ Ruínas Antigas

    private void ruinasAntigas(WorldGenLevel level, RandomSource random, BoundingBox box) {
        // Piso rachado e com falhas.
        for (int x = 0; x < 13; x++) {
            for (int z = 0; z < 13; z++) {
                if (random.nextFloat() < 0.85f) {
                    placeBlock(level, random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : pedraVelha(random), x, 0, z, box);
                }
            }
        }

        // Oito colunas quebradas de alturas diferentes.
        int[][] colunas = {{1, 1}, {1, 6}, {1, 11}, {6, 1}, {6, 11}, {11, 1}, {11, 6}, {11, 11}};
        for (int[] col : colunas) {
            int altura = 2 + random.nextInt(5);
            fillColumnDown(level, Blocks.STONE_BRICKS.defaultBlockState(), col[0], -1, col[1], box);
            for (int y = 1; y <= altura; y++) {
                placeBlock(level, pedraVelha(random), col[0], y, col[1], box);
            }
            if (altura >= 5) {
                placeBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), col[0], altura, col[1], box);
            }
        }

        // Restos de muro entre as colunas.
        for (int i = 2; i <= 10; i++) {
            if (i == 6) continue;
            for (int y = 1; y <= 2; y++) {
                float chance = y == 1 ? 0.6f : 0.25f;
                maybeGenerateBlock(level, box, random, chance, 1, y, i, pedraVelha(random));
                maybeGenerateBlock(level, box, random, chance, 11, y, i, pedraVelha(random));
                maybeGenerateBlock(level, box, random, chance, i, y, 11, pedraVelha(random));
                maybeGenerateBlock(level, box, random, chance, i, y, 1, pedraVelha(random));
            }
        }

        // Teias de aranha espalhadas.
        for (int i = 0; i < 6; i++) {
            placeBlock(level, Blocks.COBWEB.defaultBlockState(), 2 + random.nextInt(9), 1, 2 + random.nextInt(9), box);
        }

        // Altar no centro, com um baú escondido embaixo dele.
        placeBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 6, 0, 6, box);
        placeBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 6, 1, 6, box);
        placeBlock(level, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 5, 1, 6, box);
        placeBlock(level, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 7, 1, 6, box);
        placeBlock(level, Blocks.LANTERN.defaultBlockState(), 6, 2, 6, box);
        createChest(level, box, random, 6, -1, 6, BuiltInLootTables.SIMPLE_DUNGEON);
    }

    // ------------------------------------------------------------------ Cabana do Caçador

    private void cabanaDoCacador(WorldGenLevel level, RandomSource random, BoundingBox box) {
        BlockState tabua = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState tronco = Blocks.SPRUCE_LOG.defaultBlockState();

        // Assoalho (z 0-1 é a varanda) com alicerce de pedra.
        for (int x = 1; x <= 7; x++) {
            for (int z = 0; z <= 9; z++) {
                fillColumnDown(level, Blocks.COBBLESTONE.defaultBlockState(), x, -1, z, box);
                placeBlock(level, tabua, x, 0, z, box);
            }
        }

        // Paredes: troncos nos cantos, tábuas no resto.
        for (int y = 1; y <= 5; y++) {
            for (int z = 2; z <= 9; z++) {
                boolean canto = z == 2 || z == 9;
                placeBlock(level, canto ? tronco : tabua, 1, y, z, box);
                placeBlock(level, canto ? tronco : tabua, 7, y, z, box);
            }
            for (int x = 2; x <= 6; x++) {
                placeBlock(level, tabua, x, y, 2, box);
                placeBlock(level, tabua, x, y, 9, box);
            }
        }
        // Empena (triângulo do telhado) na frente e atrás.
        for (int z : new int[]{2, 9}) {
            for (int x = 2; x <= 6; x++) placeBlock(level, tabua, x, 6, z, box);
        }

        // Porta e janelas.
        placeBlock(level, Blocks.AIR.defaultBlockState(), 4, 1, 2, box);
        placeBlock(level, Blocks.AIR.defaultBlockState(), 4, 2, 2, box);
        BlockState vidro = Blocks.GLASS_PANE.defaultBlockState();
        for (int y = 2; y <= 3; y++) {
            placeBlock(level, vidro, 1, y, 5, box);
            placeBlock(level, vidro, 7, y, 5, box);
            placeBlock(level, vidro, 4, y, 9, box);
            placeBlock(level, vidro, 2, y, 2, box);
            placeBlock(level, vidro, 6, y, 2, box);
        }

        // Telhado de duas águas com escadas de madeira escura.
        BlockState descidaLeste = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState descidaOeste = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        for (int z = 0; z <= 10; z++) {
            placeBlock(level, descidaLeste, 0, 5, z, box);
            placeBlock(level, descidaLeste, 1, 6, z, box);
            placeBlock(level, descidaLeste, 2, 7, z, box);
            placeBlock(level, descidaOeste, 8, 5, z, box);
            placeBlock(level, descidaOeste, 7, 6, z, box);
            placeBlock(level, descidaOeste, 6, 7, z, box);
            for (int x = 3; x <= 5; x++) placeBlock(level, Blocks.DARK_OAK_PLANKS.defaultBlockState(), x, 7, z, box);
        }

        // Varanda com cerca e lanternas.
        for (int x : new int[]{1, 2, 3, 5, 6, 7}) {
            placeBlock(level, Blocks.SPRUCE_FENCE.defaultBlockState(), x, 1, 0, box);
        }
        placeBlock(level, Blocks.LANTERN.defaultBlockState(), 1, 2, 0, box);
        placeBlock(level, Blocks.LANTERN.defaultBlockState(), 7, 2, 0, box);

        // Feno do lado de fora, embaixo do beiral.
        placeBlock(level, Blocks.HAY_BLOCK.defaultBlockState(), 0, 1, 6, box);
        placeBlock(level, Blocks.HAY_BLOCK.defaultBlockState(), 0, 1, 7, box);
        placeBlock(level, Blocks.HAY_BLOCK.defaultBlockState(), 0, 2, 7, box);

        // Interior: bancada, fornalha, barril, lanterna e o baú do caçador.
        placeBlock(level, Blocks.CRAFTING_TABLE.defaultBlockState(), 2, 1, 8, box);
        placeBlock(level, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH), 3, 1, 8, box);
        placeBlock(level, Blocks.BARREL.defaultBlockState(), 6, 1, 7, box);
        placeBlock(level, lanternaPendurada(), 4, 5, 5, box);
        createChest(level, box, random, 6, 1, 8, BuiltInLootTables.VILLAGE_WEAPONSMITH);
    }

    // ------------------------------------------------------------------ utilitários

    /** Verdadeiro se (dx, dz) está dentro de um círculo "de blocos" com o raio dado. */
    private static boolean circulo(int dx, int dz, int raio) {
        return dx * dx + dz * dz <= raio * raio + raio;
    }

    /** Tijolo de pedra sorteado entre normal, com musgo e rachado: dá aspecto antigo. */
    private static BlockState pedraVelha(RandomSource random) {
        float r = random.nextFloat();
        if (r < 0.2f) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        if (r < 0.35f) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    private static BlockState lanternaPendurada() {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }
}
