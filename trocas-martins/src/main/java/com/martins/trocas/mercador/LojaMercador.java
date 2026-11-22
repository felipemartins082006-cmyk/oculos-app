package com.martins.trocas.mercador;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * O que o Mercador VENDE para o jogador.
 * - Se algum aldeão ou o mascate vende o item, o Mercador cobra exatamente o mesmo.
 * - Se ninguém vende, cobra 25% a mais do que ele mesmo pagaria pelo item (comprar e revender
 *   nunca dá lucro).
 * - Itens raros (diamante, netherite) custam muito caro de propósito, em blocos de esmeralda.
 * Nada de élitro, totem, estrela do Nether etc.: perderia a graça do jogo.
 */
public final class LojaMercador {
	/** Uma troca de venda: paga custoA (+ custoB) e recebe resultado. */
	public record Oferta(ItemStack custoA, ItemStack custoB, ItemStack resultado, int maxUsos) {
		public static final PacketCodec<RegistryByteBuf, Oferta> CODEC = PacketCodec.tuple(
			ItemStack.PACKET_CODEC, Oferta::custoA,
			ItemStack.OPTIONAL_PACKET_CODEC, Oferta::custoB,
			ItemStack.PACKET_CODEC, Oferta::resultado,
			net.minecraft.network.codec.PacketCodecs.VAR_INT, Oferta::maxUsos,
			Oferta::new);

		/** Chave usada para contar os usos desta troca. */
		public String chave() {
			return "c:" + net.minecraft.registry.Registries.ITEM.getId(resultado.getItem());
		}

		/** Custo total em esmeraldas (blocos contam 9). */
		public int esmeraldas() {
			return emEsmeraldas(custoA) + emEsmeraldas(custoB);
		}
	}

	public static int emEsmeraldas(ItemStack s) {
		if (s.isOf(Items.EMERALD)) return s.getCount();
		if (s.isOf(Items.EMERALD_BLOCK)) return s.getCount() * 9;
		return 0;
	}

	private static final double MARGEM = 1.25;
	/** Igual à maioria das trocas dos aldeões: 12 vezes até repor. */
	public static final int USOS = 12;
	/** Diamante e netherite: só 1 por reposição. */
	public static final int USOS_RAROS = 1;
	/** Vendas para o Mercador: 16 trocas de cada item até repor (igual às compras dos aldeões). */
	public static final int USOS_VENDA = 16;

	private static final Item[] CATALOGO = {
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
		Items.EXPERIENCE_BOTTLE, Items.WHITE_WOOL,
		// utilidades
		Items.TORCH, Items.LANTERN, Items.BUCKET, Items.SHEARS, Items.COMPASS, Items.CLOCK, Items.MAP, Items.ARROW, Items.LEAD,
		Items.SADDLE, Items.NAME_TAG, Items.BELL,
	};

	/** Itens raros: preço absurdo de propósito (em esmeraldas). */
	private static final Object[][] RAROS = {
		{Items.DIAMOND, 108},          // 12 blocos de esmeralda
		{Items.NETHERITE_SCRAP, 288},  // 32 blocos
		{Items.NETHERITE_INGOT, 640},  // 64 blocos + 64 esmeraldas
	};

	private static List<Oferta> ofertas = List.of();

	private LojaMercador() {
	}

	public static List<Oferta> ofertas() {
		return ofertas;
	}

	static void montar() {
		List<Oferta> lista = new ArrayList<>();
		for (Item item : CATALOGO) {
			Oferta o = precificar(item);
			if (o != null) lista.add(o);
		}
		for (Object[] raro : RAROS) {
			lista.add(caro(new ItemStack((Item) raro[0]), (Integer) raro[1], USOS_RAROS));
		}
		ofertas = List.copyOf(lista);
	}

	private static Oferta precificar(Item item) {
		// 1) algum aldeão ou o mascate já vende: mesma troca
		TrocasAldeoes.Taxa t = TrocasAldeoes.VENDEM.get(item);
		if (t != null && t.esmeraldas() <= 64 && t.itens() <= item.getMaxCount()) {
			return new Oferta(new ItemStack(Items.EMERALD, t.esmeraldas()), ItemStack.EMPTY, new ItemStack(item, t.itens()), USOS);
		}
		// 2) preço justo: 25% acima do que o próprio Mercador pagaria
		double valor = PrecoMercador.valorBase(item);
		if (valor <= 0) return null;
		double porItem = valor * MARGEM;
		if (porItem < PrecoMercador.UMA_ESMERALDA) {
			int itens = (int) Math.max(1, Math.min(item.getMaxCount(), Math.floor(PrecoMercador.UMA_ESMERALDA / porItem)));
			return new Oferta(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(item, itens), USOS);
		}
		int esm = (int) Math.ceil(porItem / PrecoMercador.UMA_ESMERALDA);
		return caro(new ItemStack(item), esm, USOS);
	}

	/** Preço em esmeraldas; acima de 64 vira blocos de esmeralda + esmeraldas. */
	private static Oferta caro(ItemStack resultado, int esmeraldas, int usos) {
		if (esmeraldas <= 64) {
			return new Oferta(new ItemStack(Items.EMERALD, esmeraldas), ItemStack.EMPTY, resultado, usos);
		}
		int blocos = Math.min(64, esmeraldas / 9);
		int resto = Math.min(64, esmeraldas - blocos * 9);
		return new Oferta(new ItemStack(Items.EMERALD_BLOCK, blocos),
			resto > 0 ? new ItemStack(Items.EMERALD, resto) : ItemStack.EMPTY, resultado, usos);
	}
}
