package br.bolsavillager.market;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * O que cada guilda compra dos jogadores no balcão de vendas, e a referência de preço
 * (quantas unidades valem 1 esmeralda), tirada das trocas normais dos villagers.
 * O preço final quem decide é a guilda: depende da prosperidade dela (cotação), de quantos
 * villagers ela tem e de quanto desse item já comprou hoje.
 */
public final class ItemDemand {
	/** Uma guilda que compra um item: {@code unitsPerEmerald} unidades valem 1 esmeralda na referência. */
	public record Buyer(StockDef guild, String key, int unitsPerEmerald) {
		/** Chave usada para a saturação do dia: "guilda|item". */
		public String saturationKey() {
			return this.guild.id + "|" + this.key;
		}
	}

	private record Entry(StockDef guild, @Nullable Item item, @Nullable TagKey<Item> tag, String key, int unitsPerEmerald) {
		boolean matches(ItemStack stack) {
			return this.item != null ? stack.is(this.item) : this.tag != null && stack.is(this.tag);
		}
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();

	static {
		// Agricultor
		add(StockDef.FARMER, Items.WHEAT, 20);
		add(StockDef.FARMER, Items.POTATO, 26);
		add(StockDef.FARMER, Items.CARROT, 22);
		add(StockDef.FARMER, Items.BEETROOT, 15);
		add(StockDef.FARMER, Items.PUMPKIN, 6);
		add(StockDef.FARMER, Items.MELON, 4);
		// Pescador
		add(StockDef.FISHERMAN, Items.STRING, 20);
		add(StockDef.FISHERMAN, Items.COAL, 10);
		add(StockDef.FISHERMAN, Items.COD, 15);
		add(StockDef.FISHERMAN, Items.SALMON, 13);
		add(StockDef.FISHERMAN, Items.TROPICAL_FISH, 6);
		add(StockDef.FISHERMAN, Items.PUFFERFISH, 4);
		// Pastor
		addTag(StockDef.SHEPHERD, "wool", 18);
		// Flecheiro
		add(StockDef.FLETCHER, Items.STICK, 32);
		add(StockDef.FLETCHER, Items.FLINT, 26);
		add(StockDef.FLETCHER, Items.STRING, 14);
		add(StockDef.FLETCHER, Items.FEATHER, 24);
		add(StockDef.FLETCHER, Items.TRIPWIRE_HOOK, 8);
		// Bibliotecário
		add(StockDef.LIBRARIAN, Items.PAPER, 24);
		add(StockDef.LIBRARIAN, Items.BOOK, 4);
		add(StockDef.LIBRARIAN, Items.INK_SAC, 5);
		add(StockDef.LIBRARIAN, Items.WRITABLE_BOOK, 2);
		// Cartógrafo
		add(StockDef.CARTOGRAPHER, Items.PAPER, 24);
		add(StockDef.CARTOGRAPHER, Items.GLASS_PANE, 11);
		add(StockDef.CARTOGRAPHER, Items.COMPASS, 1);
		// Clérigo
		add(StockDef.CLERIC, Items.ROTTEN_FLESH, 32);
		add(StockDef.CLERIC, Items.GOLD_INGOT, 3);
		add(StockDef.CLERIC, Items.RABBIT_FOOT, 2);
		add(StockDef.CLERIC, Items.TURTLE_SCUTE, 4);
		add(StockDef.CLERIC, Items.GLASS_BOTTLE, 9);
		add(StockDef.CLERIC, Items.NETHER_WART, 22);
		// Armeiro (armaduras)
		add(StockDef.ARMORER, Items.COAL, 15);
		add(StockDef.ARMORER, Items.IRON_INGOT, 4);
		add(StockDef.ARMORER, Items.LAVA_BUCKET, 1);
		add(StockDef.ARMORER, Items.DIAMOND, 1);
		// Armeiro (armas)
		add(StockDef.WEAPONSMITH, Items.COAL, 15);
		add(StockDef.WEAPONSMITH, Items.IRON_INGOT, 4);
		add(StockDef.WEAPONSMITH, Items.FLINT, 24);
		add(StockDef.WEAPONSMITH, Items.DIAMOND, 1);
		// Ferramenteiro
		add(StockDef.TOOLSMITH, Items.COAL, 15);
		add(StockDef.TOOLSMITH, Items.IRON_INGOT, 4);
		add(StockDef.TOOLSMITH, Items.FLINT, 30);
		add(StockDef.TOOLSMITH, Items.DIAMOND, 1);
		// Açougueiro
		add(StockDef.BUTCHER, Items.CHICKEN, 14);
		add(StockDef.BUTCHER, Items.PORKCHOP, 7);
		add(StockDef.BUTCHER, Items.RABBIT, 4);
		add(StockDef.BUTCHER, Items.MUTTON, 7);
		add(StockDef.BUTCHER, Items.BEEF, 10);
		add(StockDef.BUTCHER, Items.COAL, 15);
		add(StockDef.BUTCHER, Items.DRIED_KELP_BLOCK, 10);
		add(StockDef.BUTCHER, Items.SWEET_BERRIES, 10);
		// Coureiro
		add(StockDef.LEATHERWORKER, Items.LEATHER, 6);
		add(StockDef.LEATHERWORKER, Items.FLINT, 26);
		add(StockDef.LEATHERWORKER, Items.RABBIT_HIDE, 9);
		add(StockDef.LEATHERWORKER, Items.TURTLE_SCUTE, 4);
		add(StockDef.LEATHERWORKER, Items.ARMADILLO_SCUTE, 4);
		// Pedreiro
		add(StockDef.MASON, Items.CLAY_BALL, 10);
		add(StockDef.MASON, Items.STONE, 20);
		add(StockDef.MASON, Items.GRANITE, 16);
		add(StockDef.MASON, Items.ANDESITE, 16);
		add(StockDef.MASON, Items.DIORITE, 16);
		add(StockDef.MASON, Items.QUARTZ, 12);
	}

	private ItemDemand() {
	}

	private static void add(StockDef guild, Item item, int unitsPerEmerald) {
		ENTRIES.add(new Entry(guild, item, null, BuiltInRegistries.ITEM.getKey(item).getPath(), unitsPerEmerald));
	}

	private static void addTag(StockDef guild, String tag, int unitsPerEmerald) {
		ENTRIES.add(new Entry(guild, null, TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace(tag)),
				"#" + tag, unitsPerEmerald));
	}

	/** Todas as guildas que compram esse item. */
	public static List<Buyer> buyersOf(ItemStack stack) {
		List<Buyer> buyers = new ArrayList<>();
		if (stack.isEmpty() || stack.isDamaged() || stack.isEnchanted()) {
			return buyers; // itens gastos ou encantados não entram no balcão
		}
		for (Entry e : ENTRIES) {
			if (e.matches(stack)) {
				buyers.add(new Buyer(e.guild, e.key, e.unitsPerEmerald));
			}
		}
		return buyers;
	}

	public static @Nullable Buyer buyer(ItemStack stack, StockDef guild) {
		for (Buyer b : buyersOf(stack)) {
			if (b.guild() == guild) {
				return b;
			}
		}
		return null;
	}
}
