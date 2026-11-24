package com.martins.trocas.mercador;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.trocas.NiveisExtras;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O que o Mercador VENDE para o jogador, nível por nível.
 * - Novato: materiais, blocos, plantas, comida e utilidades do dia a dia (e diamante/netherite a preço absurdo).
 * - Cada nível novo (Aprendiz, Qualificado, Especialista, Mestre) ACRESCENTA trocas novas.
 * - Depois de Mestre (Mestre II, III...): um item muito forte por nível; quando a lista acaba, alterna
 *   otimizar (uma troca forte 15% mais barata) e inovar (uma troca nova sorteada), como os outros aldeões.
 * Preços: se algum aldeão ou o mascate vende o item, a mesma troca; se não, 25% acima do que o próprio
 * Mercador pagaria (comprar e revender nunca dá lucro); itens raros custam caro de propósito.
 */
public final class LojaMercador {
	/** Uma troca de venda: paga custoA (+ custoB) e recebe resultado. */
	public record Oferta(ItemStack custoA, ItemStack custoB, ItemStack resultado, int maxUsos, String chave) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Oferta> CODEC = StreamCodec.composite(
			ItemStack.STREAM_CODEC, Oferta::custoA,
			ItemStack.OPTIONAL_STREAM_CODEC, Oferta::custoB,
			ItemStack.STREAM_CODEC, Oferta::resultado,
			ByteBufCodecs.VAR_INT, Oferta::maxUsos,
			ByteBufCodecs.STRING_UTF8, Oferta::chave,
			Oferta::new);

		/** Para salvar as trocas inovadas no próprio aldeão. */
		public static final Codec<Oferta> CODEC_SALVO = RecordCodecBuilder.create(i -> i.group(
			ItemStack.CODEC.fieldOf("custo_a").forGetter(Oferta::custoA),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("custo_b", ItemStack.EMPTY).forGetter(Oferta::custoB),
			ItemStack.CODEC.fieldOf("resultado").forGetter(Oferta::resultado),
			Codec.INT.fieldOf("usos").forGetter(Oferta::maxUsos),
			Codec.STRING.fieldOf("chave").forGetter(Oferta::chave)
		).apply(i, Oferta::new));

		/** Custo total em esmeraldas (blocos contam 9). */
		public int esmeraldas() {
			return emEsmeraldas(custoA) + emEsmeraldas(custoB);
		}

		/** Esta troca depois de {@code vezes} otimizações (15% mais barata cada), sem nunca dar lucro. */
		Oferta otimizada(int vezes) {
			int preco = (int) Math.floor(esmeraldas() * Math.pow(0.85, vezes));
			int piso = (int) Math.ceil(PrecoMercador.valorUnitario(resultado) * resultado.getCount() * MARGEM / PrecoMercador.UMA_ESMERALDA);
			return caro(resultado, Math.max(Math.max(1, piso), preco), maxUsos + vezes, chave);
		}
	}

	public static int emEsmeraldas(ItemStack s) {
		if (s.is(Items.EMERALD)) return s.getCount();
		if (s.is(Items.EMERALD_BLOCK)) return s.getCount() * 9;
		return 0;
	}

	private static final double MARGEM = 1.25;
	/** Igual à maioria das trocas dos aldeões: 12 vezes até repor. */
	public static final int USOS = 12;
	/** Itens raros: poucas vezes por reposição. */
	public static final int USOS_RAROS = 1;
	/** Vendas para o Mercador: 16 trocas de cada item até repor no Novato (sobe com o nível). */
	public static final int USOS_VENDA = 16;

	/** Quantas vendas de cada item o Mercador aceita até repor: +4 por nível e +2 por nível depois de Mestre. */
	public static int limiteVenda(int nivel, int extra) {
		return USOS_VENDA + 4 * Math.max(0, Math.min(5, nivel) - 1) + 2 * Math.max(0, extra);
	}

	// ------------------------------------------------------------------ catálogo por nível

	private static final Item[] NOVATO = {
		// madeira
		Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG,
		Items.MANGROVE_LOG, Items.CHERRY_LOG, Items.BAMBOO_BLOCK, Items.CRIMSON_STEM, Items.WARPED_STEM,
		// pedras e terra
		Items.STONE, Items.COBBLED_DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.CALCITE,
		Items.SAND, Items.RED_SAND, Items.GRAVEL, Items.CLAY_BALL, Items.MUD, Items.MOSS_BLOCK, Items.GLASS, Items.TERRACOTTA,
		Items.PACKED_ICE, Items.BLUE_ICE, Items.OBSIDIAN, Items.END_STONE, Items.PRISMARINE_SHARD,
		// plantas
		Items.OAK_SAPLING, Items.CHERRY_SAPLING, Items.SUGAR_CANE, Items.CACTUS, Items.PUMPKIN, Items.MELON, Items.BAMBOO,
		Items.KELP, Items.VINE, Items.LILY_PAD, Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.COCOA_BEANS, Items.WHEAT_SEEDS,
		Items.BROWN_MUSHROOM, Items.RED_MUSHROOM, Items.NETHER_WART, Items.SUNFLOWER, Items.POPPY,
		// comida
		Items.BREAD, Items.APPLE, Items.CARROT, Items.POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN,
		Items.COOKED_COD, Items.PUMPKIN_PIE, Items.COOKIE, Items.CAKE, Items.GOLDEN_CARROT,
		// materiais
		Items.COAL, Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ,
		Items.AMETHYST_SHARD, Items.GLOWSTONE_DUST, Items.STRING, Items.LEATHER, Items.FEATHER, Items.BONE, Items.GUNPOWDER,
		Items.SLIME_BALL, Items.INK_SAC, Items.PAPER, Items.BOOK, Items.FLINT, Items.ENDER_PEARL,
		Items.EXPERIENCE_BOTTLE, Items.WOOL.white(),
		// utilidades
		Items.TORCH, Items.LANTERN, Items.BUCKET, Items.SHEARS, Items.COMPASS, Items.CLOCK, Items.MAP, Items.ARROW, Items.LEAD,
		Items.SADDLE, Items.NAME_TAG, Items.BELL,
	};

	/** Aprendiz: blocos de construção e decoração. */
	private static final Item[] APRENDIZ = {
		Items.QUARTZ_BLOCK, Items.GLOWSTONE, Items.SEA_LANTERN, Items.PRISMARINE, Items.DARK_PRISMARINE, Items.AMETHYST_BLOCK,
		Items.MUD_BRICKS, Items.TUFF_BRICKS, Items.DEEPSLATE_BRICKS, Items.POLISHED_BLACKSTONE_BRICKS, Items.HONEY_BLOCK,
		Items.SLIME_BLOCK, Items.HAY_BLOCK, Items.BOOKSHELF, Items.ITEM_FRAME, Items.GLOW_ITEM_FRAME, Items.PAINTING,
		Items.SOUL_LANTERN, Items.FLOWER_POT, Items.CANDLE, Items.MOSS_CARPET, Items.WOOL.red(), Items.WOOL.blue(),
		Items.WOOL.green(), Items.WOOL.yellow(), Items.WOOL.black(), Items.CRYING_OBSIDIAN,
	};

	/** Qualificado: ferramentas, armadura de ferro e utilidades. */
	private static final Item[] QUALIFICADO = {
		Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.MILK_BUCKET, Items.POWDER_SNOW_BUCKET, Items.FISHING_ROD, Items.SPYGLASS,
		Items.BRUSH, Items.SHIELD, Items.BOW, Items.CROSSBOW, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL,
		Items.IRON_SWORD, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
		Items.FIREWORK_ROCKET, Items.TNT, Items.GOLDEN_APPLE, Items.SPECTRAL_ARROW, Items.HONEY_BOTTLE, Items.ANVIL,
		Items.GRINDSTONE, Items.BREWING_STAND, Items.CAULDRON,
	};

	/** Especialista: materiais raros de criaturas e do Nether/mar. */
	private static final Item[] ESPECIALISTA = {
		Items.BLAZE_ROD, Items.BLAZE_POWDER, Items.MAGMA_CREAM, Items.GHAST_TEAR, Items.PHANTOM_MEMBRANE, Items.RABBIT_FOOT,
		Items.NAUTILUS_SHELL, Items.PRISMARINE_CRYSTALS, Items.SPONGE, Items.TURTLE_SCUTE, Items.ARMADILLO_SCUTE,
		Items.ENDER_EYE, Items.HONEYCOMB, Items.GLOW_INK_SAC,
	};

	/** Equipamento de diamante (Mestre): preço em diamantes da receita x preço absurdo do diamante. */
	private static final Object[][] DIAMANTE = {
		{Items.DIAMOND_SWORD, 2}, {Items.DIAMOND_PICKAXE, 3}, {Items.DIAMOND_AXE, 3}, {Items.DIAMOND_SHOVEL, 1},
		{Items.DIAMOND_HELMET, 5}, {Items.DIAMOND_CHESTPLATE, 8}, {Items.DIAMOND_LEGGINGS, 7}, {Items.DIAMOND_BOOTS, 4},
	};

	private static final int PRECO_DIAMANTE = 108; // 12 blocos de esmeralda

	private static List<List<Oferta>> niveis = List.of();
	private static List<Oferta> elite = List.of();

	private LojaMercador() {
	}

	/** Todas as trocas possíveis da loja (todos os níveis e as de depois de Mestre). */
	public static List<Oferta> todas() {
		List<Oferta> l = new ArrayList<>();
		niveis.forEach(l::addAll);
		l.addAll(elite);
		return l;
	}

	/** As trocas do Novato (as primeiras que todo Mercador tem). */
	public static List<Oferta> ofertas() {
		return niveis.isEmpty() ? List.of() : niveis.get(0);
	}

	public static List<Oferta> elite() {
		return elite;
	}

	/** Trocas novas de um nível (1 = Novato ... 5 = Mestre). */
	public static List<Oferta> doNivel(int nivel) {
		return nivel >= 1 && nivel <= niveis.size() ? niveis.get(nivel - 1) : List.of();
	}

	/** O que ESTE Mercador vende agora: os níveis já desbloqueados + os de depois de Mestre + as inovações. */
	public static List<Oferta> ofertasPara(Villager v) {
		int nivel = v.getVillagerData().level();
		int extra = NiveisExtras.nivelExtra(v);
		List<Oferta> l = new ArrayList<>();
		for (int n = 1; n <= Math.min(5, nivel); n++) l.addAll(doNivel(n));
		for (int i = 0; i < Math.min(extra, elite.size()); i++) l.add(elite.get(i));
		List<Oferta> inovacoes = v.getAttached(ModRegistro.INOVACOES);
		if (inovacoes != null) l.addAll(inovacoes);
		Map<String, Integer> descontos = v.getAttached(ModRegistro.DESCONTOS);
		if (descontos != null && !descontos.isEmpty()) {
			l.replaceAll(o -> descontos.containsKey(o.chave()) ? o.otimizada(descontos.get(o.chave())) : o);
		}
		return l;
	}

	// ------------------------------------------------------------------ montar (ao abrir o mundo)

	static void montar(HolderLookup.Provider reg) {
		List<List<Oferta>> n = new ArrayList<>();
		List<Oferta> novato = precificar(NOVATO);
		novato.add(fixo(Items.DIAMOND, PRECO_DIAMANTE, USOS_RAROS, "c:minecraft:diamond"));
		novato.add(fixo(Items.NETHERITE_SCRAP, 288, USOS_RAROS, "c:minecraft:netherite_scrap"));
		novato.add(fixo(Items.NETHERITE_INGOT, 640, USOS_RAROS, "c:minecraft:netherite_ingot"));
		n.add(novato);
		n.add(precificar(APRENDIZ));
		n.add(precificar(QUALIFICADO));

		List<Oferta> especialista = precificar(ESPECIALISTA);
		especialista.add(livro(reg, Enchantments.EFFICIENCY, 3, 14));
		especialista.add(livro(reg, Enchantments.PROTECTION, 3, 14));
		especialista.add(livro(reg, Enchantments.SHARPNESS, 3, 14));
		especialista.add(livro(reg, Enchantments.UNBREAKING, 2, 12));
		especialista.add(livro(reg, Enchantments.FEATHER_FALLING, 3, 12));
		especialista.add(livro(reg, Enchantments.POWER, 3, 14));
		n.add(especialista);

		List<Oferta> mestre = new ArrayList<>();
		for (Object[] d : DIAMANTE) {
			Item item = (Item) d[0];
			mestre.add(caro(new ItemStack(item), PRECO_DIAMANTE * (Integer) d[1], 2, chave(new ItemStack(item))));
		}
		mestre.add(livro(reg, Enchantments.MENDING, 1, 40));
		mestre.add(livro(reg, Enchantments.SHARPNESS, 5, 32));
		mestre.add(livro(reg, Enchantments.EFFICIENCY, 5, 32));
		mestre.add(livro(reg, Enchantments.PROTECTION, 4, 28));
		mestre.add(livro(reg, Enchantments.UNBREAKING, 3, 24));
		mestre.add(livro(reg, Enchantments.FORTUNE, 3, 36));
		mestre.add(livro(reg, Enchantments.LOOTING, 3, 36));
		mestre.add(livro(reg, Enchantments.SILK_TOUCH, 1, 30));
		mestre.add(fixo(Items.SHULKER_SHELL, 64, 2, "c:minecraft:shulker_shell"));
		mestre.add(fixo(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 200, 1, "c:minecraft:netherite_upgrade_smithing_template"));
		mestre.add(fixo(Items.ANCIENT_DEBRIS, 300, 1, "c:minecraft:ancient_debris"));
		n.add(mestre);

		List<Oferta> e = new ArrayList<>();
		e.add(fixo(Items.HEART_OF_THE_SEA, 300, 1, "e:heart_of_the_sea"));
		e.add(fixo(Items.TOTEM_OF_UNDYING, 320, 1, "e:totem"));
		e.add(fixo(Items.TRIDENT, 400, 1, "e:trident"));
		e.add(fixo(Items.ENCHANTED_GOLDEN_APPLE, 450, 1, "e:enchanted_golden_apple"));
		e.add(fixo(Items.ELYTRA, 600, 1, "e:elytra"));
		e.add(livro(reg, Enchantments.SWIFT_SNEAK, 3, 150, "e:swift_sneak"));
		e.add(livro(reg, Enchantments.SOUL_SPEED, 3, 120, "e:soul_speed"));
		e.add(livro(reg, Enchantments.WIND_BURST, 3, 150, "e:wind_burst"));
		e.add(livro(reg, Enchantments.DENSITY, 5, 120, "e:density"));
		e.add(livro(reg, Enchantments.BREACH, 4, 120, "e:breach"));
		e.add(fixo(Items.HEAVY_CORE, 400, 1, "e:heavy_core"));
		e.add(fixo(Items.CONDUIT, 500, 1, "e:conduit"));
		e.add(fixo(Items.SHULKER_BOX, 200, 1, "e:shulker_box"));
		e.add(fixo(Items.DRAGON_HEAD, 400, 1, "e:dragon_head"));
		e.add(caro(encantado(reg, Items.NETHERITE_HELMET, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1), 640, 1, "e:netherite_helmet"));
		e.add(caro(encantado(reg, Items.NETHERITE_CHESTPLATE, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1), 640, 1, "e:netherite_chestplate"));
		e.add(caro(encantado(reg, Items.NETHERITE_LEGGINGS, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1), 640, 1, "e:netherite_leggings"));
		e.add(caro(encantado(reg, Items.NETHERITE_BOOTS, Enchantments.PROTECTION, 4, Enchantments.FEATHER_FALLING, 4, Enchantments.MENDING, 1), 640, 1, "e:netherite_boots"));
		e.add(caro(encantado(reg, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5, Enchantments.LOOTING, 3, Enchantments.MENDING, 1), 640, 1, "e:netherite_sword"));
		e.add(caro(encantado(reg, Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 5, Enchantments.FORTUNE, 3, Enchantments.MENDING, 1), 640, 1, "e:netherite_pickaxe"));
		e.add(fixo(Items.MACE, 640, 1, "e:mace"));
		e.add(fixo(Items.BEACON, 640, 1, "e:beacon"));
		e.add(fixo(Items.NETHER_STAR, 640, 1, "e:nether_star"));
		e.add(fixo(Items.DRAGON_EGG, 640, 1, "e:dragon_egg"));
		e.removeIf(o -> o == null);

		niveis = List.copyOf(n.stream().map(l -> {
			l.removeIf(o -> o == null);
			return List.copyOf(l);
		}).toList());
		elite = List.copyOf(e);
	}

	private static String chave(ItemStack s) {
		return "c:" + BuiltInRegistries.ITEM.getKey(s.getItem());
	}

	private static List<Oferta> precificar(Item[] itens) {
		List<Oferta> l = new ArrayList<>();
		for (Item item : itens) {
			Oferta o = precificar(item);
			if (o != null) l.add(o);
		}
		return l;
	}

	private static Oferta precificar(Item item) {
		String chave = "c:" + BuiltInRegistries.ITEM.getKey(item);
		// 1) algum aldeão ou o mascate já vende: mesma troca
		TrocasAldeoes.Taxa t = TrocasAldeoes.VENDEM.get(item);
		if (t != null && t.esmeraldas() <= 64 && t.itens() <= item.getDefaultMaxStackSize()) {
			return new Oferta(new ItemStack(Items.EMERALD, t.esmeraldas()), ItemStack.EMPTY, new ItemStack(item, t.itens()), USOS, chave);
		}
		// 2) preço justo: 25% acima do que o próprio Mercador pagaria
		double valor = PrecoMercador.valorBase(item);
		if (valor <= 0) return null;
		double porItem = valor * MARGEM;
		if (porItem < PrecoMercador.UMA_ESMERALDA) {
			int itens = (int) Math.max(1, Math.min(item.getDefaultMaxStackSize(), Math.floor(PrecoMercador.UMA_ESMERALDA / porItem)));
			return new Oferta(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(item, itens), USOS, chave);
		}
		int esm = (int) Math.ceil(porItem / PrecoMercador.UMA_ESMERALDA);
		return caro(new ItemStack(item), esm, USOS, chave);
	}

	/** Item com preço fixo (caro de propósito) — mas se algum aldeão vende, é a mesma troca do aldeão. */
	private static Oferta fixo(Item item, int esmeraldas, int usos, String chave) {
		TrocasAldeoes.Taxa t = TrocasAldeoes.VENDEM.get(item);
		if (t != null && t.esmeraldas() <= 64 && t.itens() <= item.getDefaultMaxStackSize()) {
			return new Oferta(new ItemStack(Items.EMERALD, t.esmeraldas()), ItemStack.EMPTY, new ItemStack(item, t.itens()), usos, chave);
		}
		return caro(new ItemStack(item), esmeraldas, usos, chave);
	}

	/** Livro encantado por um preço fixo (nunca abaixo do que o Mercador pagaria por ele). */
	private static Oferta livro(HolderLookup.Provider reg, ResourceKey<Enchantment> chave, int nivel, int preco) {
		return livro(reg, chave, nivel, preco, "l:" + chave.identifier().getPath() + "_" + nivel);
	}

	private static Oferta livro(HolderLookup.Provider reg, ResourceKey<Enchantment> chave, int nivel, int preco, String id) {
		Holder<Enchantment> e = reg.lookupOrThrow(Registries.ENCHANTMENT).get(chave).orElse(null);
		if (e == null) return null;
		ItemStack livro = EnchantmentHelper.createBook(new EnchantmentInstance(e, nivel));
		return caro(livro, justo(livro, preco), 3, id);
	}

	private static ItemStack encantado(HolderLookup.Provider reg, Item item, Object... pares) {
		ItemStack s = new ItemStack(item);
		for (int i = 0; i < pares.length; i += 2) {
			@SuppressWarnings("unchecked")
			ResourceKey<Enchantment> k = (ResourceKey<Enchantment>) pares[i];
			int nivel = (Integer) pares[i + 1];
			reg.lookupOrThrow(Registries.ENCHANTMENT).get(k).ifPresent(e -> s.enchant(e, nivel));
		}
		return s;
	}

	/** Preço nunca abaixo de 25% acima do que o Mercador pagaria pelo item (não dá lucro revender). */
	private static int justo(ItemStack s, int preco) {
		int piso = (int) Math.ceil(PrecoMercador.valorUnitario(s) * s.getCount() * MARGEM / PrecoMercador.UMA_ESMERALDA);
		return Math.max(preco, Math.max(1, piso));
	}

	/** Preço em esmeraldas; acima de 64 vira blocos de esmeralda + esmeraldas (máximo 64 blocos + 64). */
	static Oferta caro(ItemStack resultado, int esmeraldas, int usos, String chave) {
		esmeraldas = Math.min(64 * 9 + 64, justo(resultado, esmeraldas));
		if (esmeraldas <= 64) {
			return new Oferta(new ItemStack(Items.EMERALD, esmeraldas), ItemStack.EMPTY, resultado, usos, chave);
		}
		int blocos = Math.min(64, esmeraldas / 9);
		int resto = Math.min(64, esmeraldas - blocos * 9);
		return new Oferta(new ItemStack(Items.EMERALD_BLOCK, blocos),
			resto > 0 ? new ItemStack(Items.EMERALD, resto) : ItemStack.EMPTY, resultado, usos, chave);
	}

	// ------------------------------------------------------------------ depois de Mestre: inovar

	private static final Item[] EQUIPAMENTOS = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
		Items.NETHERITE_BOOTS, Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL,
		Items.MACE, Items.TRIDENT, Items.BOW, Items.CROSSBOW, Items.ELYTRA, Items.FISHING_ROD};

	/** Uma troca nova sorteada para a loja deste Mercador. */
	public static Oferta inovar(Villager v, int numero) {
		RandomSource r = v.getRandom();
		HolderLookup.Provider reg = v.level().registryAccess();
		List<Holder<Enchantment>> encs = new ArrayList<>();
		reg.lookupOrThrow(Registries.ENCHANTMENT).listElements().filter(e -> !e.is(EnchantmentTags.CURSE)).forEach(encs::add);
		String id = "i:" + numero;
		switch (r.nextInt(3)) {
			case 0 -> {
				Holder<Enchantment> e = encs.get(r.nextInt(encs.size()));
				ItemStack livro = EnchantmentHelper.createBook(new EnchantmentInstance(e, e.value().getMaxLevel()));
				return caro(livro, 30 + r.nextInt(40), 3, id);
			}
			case 1 -> {
				ItemStack item = new ItemStack(EQUIPAMENTOS[r.nextInt(EQUIPAMENTOS.length)]);
				List<Holder<Enchantment>> cabem = new ArrayList<>();
				for (Holder<Enchantment> e : encs) if (e.value().canEnchant(item)) cabem.add(e);
				for (int i = 0; i < 4 && !cabem.isEmpty(); i++) {
					Holder<Enchantment> e = cabem.remove(r.nextInt(cabem.size()));
					if (EnchantmentHelper.isEnchantmentCompatible(EnchantmentHelper.getEnchantmentsForCrafting(item).keySet(), e)) {
						item.enchant(e, e.value().getMaxLevel());
					}
				}
				return caro(item, 500 + r.nextInt(141), 1, id);
			}
			default -> {
				Oferta base = elite.get(r.nextInt(elite.size()));
				ItemStack venda = base.resultado().copy();
				if (venda.getMaxStackSize() < venda.getCount() * 2) return caro(venda, base.esmeraldas(), base.maxUsos() + 1, id);
				venda.setCount(venda.getCount() * 2);
				return caro(venda, (int) Math.ceil(base.esmeraldas() * 1.6), base.maxUsos(), id);
			}
		}
	}

	/** Usado pelos níveis depois de Mestre: quantas vezes cada troca forte já foi otimizada. */
	public static Map<String, Integer> novoMapa(Map<String, Integer> antigo) {
		return antigo == null ? new HashMap<>() : new HashMap<>(antigo);
	}
}
