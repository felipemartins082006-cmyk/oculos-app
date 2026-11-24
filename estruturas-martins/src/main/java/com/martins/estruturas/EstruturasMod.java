package com.martins.estruturas;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ponto de entrada do mod: registra o tipo de estrutura e o tipo de peça. */
public class EstruturasMod implements ModInitializer {
    public static final String MOD_ID = "estruturas";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final StructureType<EstruturaMartins> ESTRUTURA = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE, id("estrutura"), () -> EstruturaMartins.CODEC);

    public static final StructurePieceType PECA = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE, id("peca"), (contexto, tag) -> new PecaMartins(tag));

    public static Identifier id(String caminho) {
        return Identifier.fromNamespaceAndPath(MOD_ID, caminho);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Estruturas Martins carregado: torre do mago, ruínas antigas e cabana do caçador.");
    }
}
