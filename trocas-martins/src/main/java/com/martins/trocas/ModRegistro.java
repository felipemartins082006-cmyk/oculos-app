package com.martins.trocas;

import com.google.common.collect.ImmutableSet;
import com.martins.trocas.mercador.BalcaoDoMercadorBlock;
import com.martins.trocas.mercador.MercadorMenu;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;

public final class ModRegistro {
	public static final ResourceKey<PoiType> MERCADOR_POI_KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TrocasMartins.id("mercador"));
	public static final ResourceKey<VillagerProfession> MERCADOR_KEY = ResourceKey.create(Registries.VILLAGER_PROFESSION, TrocasMartins.id("mercador"));
	private static final ResourceKey<Block> BALCAO_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, TrocasMartins.id("balcao_do_mercador"));
	private static final ResourceKey<Item> BALCAO_ITEM_KEY = ResourceKey.create(Registries.ITEM, TrocasMartins.id("balcao_do_mercador"));

	public static final Block BALCAO_DO_MERCADOR = new BalcaoDoMercadorBlock(BlockBehaviour.Properties.of()
		.mapColor(MapColor.WOOD)
		.instrument(NoteBlockInstrument.BASS)
		.strength(2.5f)
		.sound(SoundType.WOOD)
		.ignitedByLava()
		.noOcclusion()
		.setId(BALCAO_BLOCK_KEY));

	public static final Item BALCAO_DO_MERCADOR_ITEM = new BlockItem(BALCAO_DO_MERCADOR,
		new Item.Properties().setId(BALCAO_ITEM_KEY).useBlockDescriptionPrefix());

	public static PoiType MERCADOR_POI;
	public static VillagerProfession MERCADOR;

	/** Quantas vezes cada troca do Mercador já foi usada desde a última reposição (salvo no aldeão). */
	public static final AttachmentType<Map<String, Integer>> USOS = AttachmentRegistry.create(TrocasMartins.id("usos"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).initializer(HashMap::new));

	/** Níveis depois de Mestre (0 = Mestre, 1 = Mestre II...). */
	public static final AttachmentType<Integer> NIVEL_EXTRA = AttachmentRegistry.create(TrocasMartins.id("nivel_extra"),
		b -> b.persistent(Codec.INT));

	/** Mercador depois de Mestre: trocas novas sorteadas (inovar). */
	public static final AttachmentType<java.util.List<com.martins.trocas.mercador.LojaMercador.Oferta>> INOVACOES =
		AttachmentRegistry.create(TrocasMartins.id("inovacoes"),
			b -> b.persistent(com.martins.trocas.mercador.LojaMercador.Oferta.CODEC_SALVO.listOf()));

	/** Mercador depois de Mestre: quantas vezes cada troca forte foi otimizada (15% mais barata cada). */
	public static final AttachmentType<Map<String, Integer>> DESCONTOS = AttachmentRegistry.create(TrocasMartins.id("descontos"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));

	public static final MenuType<MercadorMenu> MERCADOR_MENU = new ExtendedMenuType<>(MercadorMenu::new, ByteBufCodecs.VAR_INT);

	private ModRegistro() {
	}

	/** O aldeão é Mercador? */
	public static boolean ehMercador(net.minecraft.world.entity.npc.villager.Villager v) {
		return v.getVillagerData().profession().is(MERCADOR_KEY);
	}

	public static void registrar() {
		Registry.register(BuiltInRegistries.BLOCK, BALCAO_BLOCK_KEY, BALCAO_DO_MERCADOR);
		Registry.register(BuiltInRegistries.ITEM, BALCAO_ITEM_KEY, BALCAO_DO_MERCADOR_ITEM);

		MERCADOR_POI = PoiHelper.register(TrocasMartins.id("mercador"), 1, 1, BALCAO_DO_MERCADOR);

		MERCADOR = Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, MERCADOR_KEY, new VillagerProfession(
			Component.translatable("entity.trocasmartins.villager.mercador"),
			h -> h.is(MERCADOR_POI_KEY),
			h -> h.is(MERCADOR_POI_KEY),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_CARTOGRAPHER,
			Int2ObjectMap.ofEntries()));

		Registry.register(BuiltInRegistries.MENU, TrocasMartins.id("mercador"), MERCADOR_MENU);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(saida ->
			saida.insertAfter(Items.LECTERN, BALCAO_DO_MERCADOR_ITEM));
	}
}
