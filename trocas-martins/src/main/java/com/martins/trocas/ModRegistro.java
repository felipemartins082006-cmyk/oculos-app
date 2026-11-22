package com.martins.trocas;

import com.google.common.collect.ImmutableSet;
import com.martins.trocas.mercador.BalcaoDoMercadorBlock;
import com.martins.trocas.mercador.MercadorScreenHandler;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;

public final class ModRegistro {
	public static final RegistryKey<PointOfInterestType> MERCADOR_POI_KEY =
		RegistryKey.of(RegistryKeys.POINT_OF_INTEREST_TYPE, TrocasMartins.id("mercador"));

	public static final Block BALCAO_DO_MERCADOR = new BalcaoDoMercadorBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.OAK_TAN)
		.instrument(NoteBlockInstrument.BASS)
		.strength(2.5f)
		.sounds(BlockSoundGroup.WOOD)
		.burnable()
		.nonOpaque());

	public static final Item BALCAO_DO_MERCADOR_ITEM = new BlockItem(BALCAO_DO_MERCADOR, new Item.Settings());

	/** Quantas vezes cada troca do Mercador já foi usada desde a última reposição (salvo no aldeão). */
	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<java.util.Map<String, Integer>> USOS =
		net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<java.util.Map<String, Integer>>builder()
			.persistent(com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.INT))
			.initializer(java.util.HashMap::new)
			.buildAndRegister(TrocasMartins.id("usos"));

	public static PointOfInterestType MERCADOR_POI;
	public static VillagerProfession MERCADOR;

	public static final ScreenHandlerType<MercadorScreenHandler> MERCADOR_SCREEN = new ExtendedScreenHandlerType<>(
		MercadorScreenHandler::new, PacketCodecs.VAR_INT);

	private ModRegistro() {
	}

	public static void registrar() {
		Registry.register(Registries.BLOCK, TrocasMartins.id("balcao_do_mercador"), BALCAO_DO_MERCADOR);
		Registry.register(Registries.ITEM, TrocasMartins.id("balcao_do_mercador"), BALCAO_DO_MERCADOR_ITEM);

		MERCADOR_POI = PointOfInterestHelper.register(TrocasMartins.id("mercador"), 1, 1, BALCAO_DO_MERCADOR);

		MERCADOR = Registry.register(Registries.VILLAGER_PROFESSION, TrocasMartins.id("mercador"), new VillagerProfession(
			"mercador",
			entry -> entry.matchesKey(MERCADOR_POI_KEY),
			entry -> entry.matchesKey(MERCADOR_POI_KEY),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER));

		Registry.register(Registries.SCREEN_HANDLER, TrocasMartins.id("mercador"), MERCADOR_SCREEN);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries ->
			entries.addAfter(Items.LECTERN, BALCAO_DO_MERCADOR_ITEM));
	}
}
