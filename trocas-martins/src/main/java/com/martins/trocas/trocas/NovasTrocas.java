package com.martins.trocas.trocas;

import com.martins.trocas.mercador.MercadorConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Trocas novas de cada profissão. A cada nível que o aldeão sobe ele ganha as 2 trocas normais
 * do Minecraft + {@code trocas_novas_por_nivel} trocas sorteadas desta lista.
 */
public final class NovasTrocas {
	/** Cria uma troca para o aldeão (ou null se não der). */
	@FunctionalInterface
	public interface Fabrica {
		MerchantOffer criar(Entity aldeao, RandomSource r);
	}

	private static final Map<ResourceKey<VillagerProfession>, Map<Integer, List<Fabrica>>> TROCAS = new HashMap<>();

	private NovasTrocas() {
	}

	/** Todas as trocas novas, por profissão e nível (o Mercador usa para conhecer os preços). */
	public static Map<ResourceKey<VillagerProfession>, Map<Integer, List<Fabrica>>> todas() {
		return TROCAS;
	}

	// ------------------------------------------------------------------ fábricas de troca

	/** O aldeão VENDE {@code qtd} de {@code item} por {@code preco} esmeraldas. */
	static Fabrica vende(ItemLike item, int qtd, int preco, int usos, int xp) {
		return (e, r) -> new MerchantOffer(new ItemCost(Items.EMERALD, preco), new ItemStack(item, qtd), usos, xp, 0.05f);
	}

	/** O aldeão vende por esmeraldas + um item extra. */
	static Fabrica vendeCom(ItemLike item, int qtd, int preco, ItemLike extra, int qtdExtra, int usos, int xp) {
		return (e, r) -> new MerchantOffer(new ItemCost(Items.EMERALD, preco), Optional.of(new ItemCost(extra, qtdExtra)),
			new ItemStack(item, qtd), usos, xp, 0.05f);
	}

	/** O aldeão COMPRA {@code qtd} de {@code item} e paga {@code esmeraldas}. */
	static Fabrica compra(ItemLike item, int qtd, int esmeraldas, int usos, int xp) {
		return (e, r) -> new MerchantOffer(new ItemCost(item, qtd), new ItemStack(Items.EMERALD, esmeraldas), usos, xp, 0.05f);
	}

	/** Vende um item sorteado de uma lista. */
	static Fabrica vendeUmDe(int qtd, int preco, int usos, int xp, ItemLike... opcoes) {
		return (e, r) -> new MerchantOffer(new ItemCost(Items.EMERALD, preco),
			new ItemStack(opcoes[r.nextInt(opcoes.length)], qtd), usos, xp, 0.05f);
	}

	static Fabrica vendeEncantado(Item item, int preco, int usos, int xp, Ench... encs) {
		return (e, r) -> {
			ItemStack s = new ItemStack(item);
			for (Ench en : encs) {
				Holder<Enchantment> entry = ench(e, en.chave());
				if (entry == null) return null;
				s.enchant(entry, en.nivel());
			}
			return new MerchantOffer(new ItemCost(Items.EMERALD, preco), s, usos, xp, 0.05f);
		};
	}

	/** Item encantado por esmeraldas + um item extra (ex.: blocos de esmeralda, lingote de netherite). */
	static Fabrica vendeEncantadoCom(Item item, int preco, ItemLike extra, int qtdExtra, int usos, int xp, Ench... encs) {
		return (e, r) -> {
			ItemStack s = new ItemStack(item);
			for (Ench en : encs) {
				Holder<Enchantment> entry = ench(e, en.chave());
				if (entry == null) return null;
				s.enchant(entry, en.nivel());
			}
			return new MerchantOffer(new ItemCost(Items.EMERALD, preco), Optional.of(new ItemCost(extra, qtdExtra)), s, usos, xp, 0.05f);
		};
	}

	static Fabrica vendeLivro(ResourceKey<Enchantment> chave, int nivel, int preco, int xp) {
		return (e, r) -> {
			Holder<Enchantment> entry = ench(e, chave);
			if (entry == null) return null;
			ItemStack livro = EnchantmentHelper.createBook(new EnchantmentInstance(entry, nivel));
			return new MerchantOffer(new ItemCost(Items.EMERALD, preco), Optional.of(new ItemCost(Items.BOOK, 1)), livro, 3, xp, 0.2f);
		};
	}

	static Fabrica vendePocao(Item tipo, Holder<Potion> pocao, int preco, int xp) {
		return (e, r) -> new MerchantOffer(new ItemCost(Items.EMERALD, preco), Optional.of(new ItemCost(Items.GLASS_BOTTLE, 1)),
			PotionContents.createItemStack(tipo, pocao), 6, xp, 0.05f);
	}

	static Fabrica vendeFlechas(Holder<Potion> pocao, int qtd, int preco, int xp) {
		return (e, r) -> {
			ItemStack flechas = PotionContents.createItemStack(Items.TIPPED_ARROW, pocao);
			flechas.setCount(qtd);
			return new MerchantOffer(new ItemCost(Items.EMERALD, preco), Optional.of(new ItemCost(Items.ARROW, qtd)), flechas, 8, xp, 0.05f);
		};
	}

	record Ench(ResourceKey<Enchantment> chave, int nivel) {
	}

	static Ench en(ResourceKey<Enchantment> chave, int nivel) {
		return new Ench(chave, nivel);
	}

	private static Holder<Enchantment> ench(Entity e, ResourceKey<Enchantment> chave) {
		return e.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(chave).map(h -> (Holder<Enchantment>) h).orElse(null);
	}

	private static void add(ResourceKey<VillagerProfession> p, int nivel, Fabrica... fabricas) {
		TROCAS.computeIfAbsent(p, k -> new HashMap<>()).computeIfAbsent(nivel, k -> new ArrayList<>()).addAll(List.of(fabricas));
	}

	// ------------------------------------------------------------------ aplicar no aldeão

	/** Chamado (via mixin) logo depois de o aldeão receber as trocas normais de um nível. */
	public static void adicionarTrocas(Villager aldeao, MerchantOffers ofertas) {
		int quantas = MercadorConfig.atual.trocas_novas_por_nivel;
		if (quantas <= 0) return;
		ResourceKey<VillagerProfession> prof = aldeao.getVillagerData().profession().unwrapKey().orElse(null);
		Map<Integer, List<Fabrica>> porNivel = prof == null ? null : TROCAS.get(prof);
		if (porNivel == null) return;
		List<Fabrica> pool = new ArrayList<>(porNivel.getOrDefault(aldeao.getVillagerData().level(), List.of()));
		RandomSource r = aldeao.getRandom();
		for (int i = 0; i < quantas && !pool.isEmpty(); i++) {
			MerchantOffer oferta = pool.remove(r.nextInt(pool.size())).criar(aldeao, r);
			if (oferta != null) ofertas.add(oferta);
		}
	}

	// ------------------------------------------------------------------ as trocas

	public static void registrar() {
		ResourceKey<VillagerProfession> P;

		// ARMEIRO
		P = VillagerProfession.ARMORER;
		add(P, 1, compra(Items.COPPER_INGOT, 8, 1, 16, 2), vende(Items.SHIELD, 1, 4, 8, 1));
		add(P, 2, vende(Items.WOLF_ARMOR, 1, 8, 4, 5), compra(Items.ARMADILLO_SCUTE, 3, 1, 12, 10));
		add(P, 3, vendeEncantado(Items.IRON_CHESTPLATE, 14, 3, 15, en(Enchantments.PROTECTION, 3)),
			vendeEncantado(Items.IRON_BOOTS, 10, 3, 15, en(Enchantments.FEATHER_FALLING, 3)));
		add(P, 4, vendeEncantado(Items.DIAMOND_HELMET, 30, 3, 20, en(Enchantments.RESPIRATION, 3), en(Enchantments.AQUA_AFFINITY, 1)),
			vendeUmDe(1, 20, 2, 20, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
				Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE));
		add(P, 5, vendeCom(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 48, Items.DIAMOND, 4, 2, 30),
			vendeEncantado(Items.DIAMOND_CHESTPLATE, 48, 2, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.UNBREAKING, 3)));

		// AÇOUGUEIRO
		P = VillagerProfession.BUTCHER;
		add(P, 1, vende(Items.GOLDEN_CARROT, 4, 3, 12, 2), compra(Items.BONE, 12, 1, 16, 2));
		add(P, 2, vende(Items.RABBIT_STEW, 1, 2, 12, 5), vende(Items.CAKE, 1, 3, 6, 5));
		add(P, 3, vende(Items.COOKED_BEEF, 6, 3, 16, 10), compra(Items.RABBIT_HIDE, 6, 1, 16, 10));
		add(P, 4, vende(Items.RABBIT_FOOT, 1, 6, 6, 15), vende(Items.PUMPKIN_PIE, 4, 3, 12, 15));
		add(P, 5, vende(Items.GOLDEN_APPLE, 1, 10, 4, 30), vende(Items.HONEY_BOTTLE, 3, 3, 12, 30));

		// CARTÓGRAFO
		P = VillagerProfession.CARTOGRAPHER;
		add(P, 1, vende(Items.COMPASS, 1, 4, 8, 2), compra(Items.FEATHER, 16, 1, 16, 2));
		add(P, 2, vende(Items.SPYGLASS, 1, 6, 6, 5), vende(Items.GLOW_ITEM_FRAME, 4, 3, 12, 5));
		add(P, 3, vendeUmDe(1, 8, 4, 10, Items.FLOWER_BANNER_PATTERN, Items.CREEPER_BANNER_PATTERN, Items.SKULL_BANNER_PATTERN,
			Items.FLOW_BANNER_PATTERN, Items.GUSTER_BANNER_PATTERN), vende(Items.LODESTONE, 1, 24, 2, 10));
		add(P, 4, vende(Items.ECHO_SHARD, 1, 16, 4, 15), vende(Items.MAP, 4, 2, 12, 15));
		add(P, 5, vende(Items.RECOVERY_COMPASS, 1, 30, 2, 30), vende(Items.MOJANG_BANNER_PATTERN, 1, 32, 2, 30));

		// CLÉRIGO
		P = VillagerProfession.CLERIC;
		add(P, 1, vende(Items.BLAZE_POWDER, 2, 4, 12, 2), compra(Items.SPIDER_EYE, 4, 1, 16, 2));
		add(P, 2, vendePocao(Items.POTION, Potions.HEALING, 5, 5), vende(Items.GLOWSTONE_DUST, 8, 2, 12, 5));
		add(P, 3, vende(Items.GHAST_TEAR, 1, 8, 6, 10), vende(Items.PHANTOM_MEMBRANE, 2, 6, 8, 10));
		add(P, 4, vendePocao(Items.POTION, Potions.REGENERATION, 8, 15), vendePocao(Items.POTION, Potions.FIRE_RESISTANCE, 6, 15),
			vende(Items.DRAGON_BREATH, 1, 10, 4, 15));
		add(P, 5, vendeCom(Items.TOTEM_OF_UNDYING, 1, 64, Items.DIAMOND, 4, 1, 30),
			vendePocao(Items.SPLASH_POTION, Potions.STRONG_HEALING, 12, 30));

		// FAZENDEIRO
		P = VillagerProfession.FARMER;
		add(P, 1, vende(Items.SWEET_BERRIES, 8, 1, 16, 2), vende(Items.GLOW_BERRIES, 6, 2, 16, 2));
		add(P, 2, vende(Items.COCOA_BEANS, 4, 1, 16, 5), vende(Items.BAMBOO, 8, 1, 16, 5), compra(Items.CACTUS, 16, 1, 16, 10));
		add(P, 3, vende(Items.MOSS_BLOCK, 4, 2, 12, 10), vende(Items.CHERRY_SAPLING, 1, 2, 8, 10), vende(Items.FLOWERING_AZALEA, 1, 2, 8, 10));
		add(P, 4, vende(Items.TORCHFLOWER_SEEDS, 1, 6, 4, 15), vende(Items.PITCHER_POD, 1, 6, 4, 15));
		add(P, 5, vende(Items.SNIFFER_EGG, 1, 32, 1, 30), vende(Items.GOLDEN_CARROT, 8, 5, 8, 30));

		// PESCADOR
		P = VillagerProfession.FISHERMAN;
		add(P, 1, vende(Items.LILY_PAD, 4, 1, 16, 2), vende(Items.TROPICAL_FISH_BUCKET, 1, 3, 6, 2));
		add(P, 2, vende(Items.PRISMARINE_SHARD, 8, 2, 12, 5), vende(Items.PRISMARINE_CRYSTALS, 4, 3, 12, 5));
		add(P, 3, vende(Items.SPONGE, 1, 6, 6, 10), vende(Items.AXOLOTL_BUCKET, 1, 8, 4, 10));
		add(P, 4, vende(Items.NAUTILUS_SHELL, 1, 8, 4, 15),
			vendeEncantado(Items.FISHING_ROD, 20, 3, 15, en(Enchantments.LUCK_OF_THE_SEA, 3), en(Enchantments.LURE, 3)));
		add(P, 5, vende(Items.HEART_OF_THE_SEA, 1, 40, 1, 30), vende(Items.TRIDENT, 1, 60, 1, 30));

		// FLECHEIRO
		P = VillagerProfession.FLETCHER;
		add(P, 1, vende(Items.SPECTRAL_ARROW, 8, 3, 12, 2), compra(Items.FEATHER, 24, 1, 16, 2));
		add(P, 2, vende(Items.WIND_CHARGE, 6, 4, 12, 5), vendeFlechas(Potions.SLOWNESS, 8, 3, 5));
		add(P, 3, vendeEncantado(Items.BOW, 12, 3, 10, en(Enchantments.POWER, 3)), vendeFlechas(Potions.POISON, 8, 4, 10));
		add(P, 4, vendeEncantado(Items.CROSSBOW, 18, 3, 15, en(Enchantments.MULTISHOT, 1), en(Enchantments.QUICK_CHARGE, 2)),
			vendeFlechas(Potions.HARMING, 8, 6, 15));
		add(P, 5, vendeEncantado(Items.BOW, 30, 2, 30, en(Enchantments.INFINITY, 1), en(Enchantments.POWER, 4)),
			vende(Items.SPECTRAL_ARROW, 16, 6, 12, 30));

		// CURTIDOR
		P = VillagerProfession.LEATHERWORKER;
		add(P, 1, vende(Items.LEAD, 1, 3, 12, 2), compra(Items.RABBIT_HIDE, 6, 1, 16, 2));
		add(P, 2, vende(Items.IRON_HORSE_ARMOR, 1, 6, 6, 5), vende(Items.ITEM_FRAME, 2, 2, 12, 5));
		add(P, 3, vende(Items.NAME_TAG, 1, 6, 6, 10), vende(Items.GOLDEN_HORSE_ARMOR, 1, 8, 6, 10));
		add(P, 4, vende(Items.DIAMOND_HORSE_ARMOR, 1, 16, 3, 15), vende(Items.WOLF_ARMOR, 1, 10, 4, 15));
		add(P, 5, vende(Items.TURTLE_HELMET, 1, 20, 2, 30), vendeEncantado(Items.LEATHER_BOOTS, 24, 2, 30,
			en(Enchantments.DEPTH_STRIDER, 3), en(Enchantments.FEATHER_FALLING, 4)));

		// BIBLIOTECÁRIO
		P = VillagerProfession.LIBRARIAN;
		add(P, 1, vende(Items.GLOW_INK_SAC, 4, 2, 12, 2), vende(Items.WRITABLE_BOOK, 1, 3, 12, 2));
		add(P, 2, vende(Items.CHISELED_BOOKSHELF, 1, 4, 12, 5), vende(Items.EXPERIENCE_BOTTLE, 3, 5, 12, 5));
		add(P, 3, vendeLivro(Enchantments.EFFICIENCY, 4, 20, 10), vendeLivro(Enchantments.PROTECTION, 4, 22, 10),
			vendeLivro(Enchantments.SHARPNESS, 4, 22, 10));
		add(P, 4, vendeLivro(Enchantments.UNBREAKING, 3, 26, 15), vendeLivro(Enchantments.FORTUNE, 3, 32, 15),
			vendeLivro(Enchantments.LOOTING, 3, 32, 15));
		add(P, 5, vendeLivro(Enchantments.MENDING, 1, 40, 30), vendeLivro(Enchantments.SILK_TOUCH, 1, 30, 30),
			vendeLivro(Enchantments.SWIFT_SNEAK, 3, 48, 30));

		// PEDREIRO
		P = VillagerProfession.MASON;
		add(P, 1, vende(Items.CALCITE, 8, 1, 16, 2), vende(Items.TUFF_BRICKS, 8, 1, 16, 2));
		add(P, 2, vende(Items.OBSIDIAN, 4, 4, 12, 5), vende(Items.MUD_BRICKS, 8, 1, 16, 5));
		add(P, 3, vende(Items.CRYING_OBSIDIAN, 4, 4, 12, 10), vende(Items.AMETHYST_CLUSTER, 2, 4, 12, 10));
		add(P, 4, vende(Items.END_STONE_BRICKS, 8, 3, 12, 15), vende(Items.PURPUR_BLOCK, 8, 3, 12, 15),
			vende(Items.POINTED_DRIPSTONE, 4, 2, 12, 15));
		add(P, 5, vende(Items.SEA_LANTERN, 8, 6, 12, 30),
			vendeUmDe(4, 6, 12, 30, Items.OCHRE_FROGLIGHT, Items.VERDANT_FROGLIGHT, Items.PEARLESCENT_FROGLIGHT));

		// PASTOR
		P = VillagerProfession.SHEPHERD;
		add(P, 1, vende(Items.COBWEB, 2, 3, 12, 2), compra(Items.STRING, 16, 1, 16, 2));
		add(P, 2, vendeUmDe(8, 3, 12, 5, Items.WOOL.pink(), Items.WOOL.cyan(), Items.WOOL.purple(), Items.WOOL.lime(), Items.WOOL.orange(),
			Items.WOOL.magenta(), Items.WOOL.lightBlue(), Items.WOOL.yellow()), vende(Items.PAINTING, 2, 2, 12, 5));
		add(P, 3, vendeUmDe(4, 2, 12, 10, Items.CANDLE, Items.DYED_CANDLE.red(), Items.DYED_CANDLE.blue(), Items.DYED_CANDLE.green(), Items.DYED_CANDLE.pink(),
			Items.DYED_CANDLE.purple(), Items.DYED_CANDLE.yellow()), vende(Items.LOOM, 1, 2, 8, 10));
		add(P, 4, vende(Items.PIGLIN_BANNER_PATTERN, 1, 10, 4, 15), vende(Items.GLOBE_BANNER_PATTERN, 1, 10, 4, 15));
		add(P, 5, vendeUmDe(1, 16, 2, 30, Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS, Items.MUSIC_DISC_CHIRP,
			Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL, Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD,
			Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_WAIT, Items.MUSIC_DISC_CREATOR_MUSIC_BOX), vende(Items.JUKEBOX, 1, 8, 4, 30));

		// FERRAMENTEIRO
		P = VillagerProfession.TOOLSMITH;
		add(P, 1, vende(Items.BRUSH, 1, 3, 12, 2), compra(Items.COPPER_INGOT, 8, 1, 16, 2));
		add(P, 2, vende(Items.SPYGLASS, 1, 5, 8, 5), vende(Items.SHEARS, 1, 2, 12, 5));
		add(P, 3, vendeEncantado(Items.IRON_PICKAXE, 10, 3, 10, en(Enchantments.EFFICIENCY, 3)),
			vendeEncantado(Items.IRON_SHOVEL, 6, 3, 10, en(Enchantments.EFFICIENCY, 3), en(Enchantments.UNBREAKING, 2)));
		add(P, 4, vendeEncantado(Items.DIAMOND_PICKAXE, 30, 3, 15, en(Enchantments.FORTUNE, 3)),
			vendeEncantado(Items.DIAMOND_AXE, 22, 3, 15, en(Enchantments.EFFICIENCY, 4)));
		add(P, 5, vendeEncantado(Items.DIAMOND_PICKAXE, 48, 2, 30, en(Enchantments.EFFICIENCY, 5), en(Enchantments.UNBREAKING, 3)),
			vendeCom(Items.NETHERITE_SCRAP, 1, 40, Items.GOLD_INGOT, 4, 2, 30));

		// ARMEIRO DE ARMAS (ferreiro de armas)
		P = VillagerProfession.WEAPONSMITH;
		add(P, 1, vende(Items.SHIELD, 1, 5, 8, 2), compra(Items.IRON_INGOT, 5, 1, 16, 2));
		add(P, 2, vendeEncantado(Items.IRON_SWORD, 8, 3, 5, en(Enchantments.SHARPNESS, 2)), vende(Items.WIND_CHARGE, 4, 3, 12, 5));
		add(P, 3, vendeEncantado(Items.IRON_AXE, 12, 3, 10, en(Enchantments.SHARPNESS, 3)), vende(Items.BREEZE_ROD, 1, 6, 6, 10));
		add(P, 4, vendeEncantado(Items.DIAMOND_SWORD, 30, 3, 15, en(Enchantments.SHARPNESS, 4), en(Enchantments.LOOTING, 2)));
		add(P, 5, vende(Items.HEAVY_CORE, 1, 48, 1, 30),
			vendeEncantado(Items.DIAMOND_SWORD, 50, 2, 30, en(Enchantments.SHARPNESS, 5), en(Enchantments.FIRE_ASPECT, 2)));

	}
}
