package br.bolsavillager.market;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import org.jspecify.annotations.Nullable;

/**
 * As "empresas" listadas na bolsa: uma por profissão de villager, mais o mercador ambulante.
 * O preço base é em esmeraldas por ação.
 */
public enum StockDef {
	ARMORER("armorer", "ARMR3", 14.0, VillagerProfession.ARMORER, 0xFF9AA4AE),
	BUTCHER("butcher", "ACOU3", 7.0, VillagerProfession.BUTCHER, 0xFFC0504D),
	CARTOGRAPHER("cartographer", "CART3", 11.0, VillagerProfession.CARTOGRAPHER, 0xFFE3C88A),
	CLERIC("cleric", "CLER3", 13.0, VillagerProfession.CLERIC, 0xFFB57EDC),
	FARMER("farmer", "FAZD3", 5.0, VillagerProfession.FARMER, 0xFF7CC04B),
	FISHERMAN("fisherman", "PESC3", 6.0, VillagerProfession.FISHERMAN, 0xFF4FA3D9),
	FLETCHER("fletcher", "FLEC3", 6.5, VillagerProfession.FLETCHER, 0xFFD9B26F),
	LEATHERWORKER("leatherworker", "CURT3", 7.5, VillagerProfession.LEATHERWORKER, 0xFFA0673A),
	LIBRARIAN("librarian", "BIBL3", 16.0, VillagerProfession.LIBRARIAN, 0xFFE8E0C8),
	MASON("mason", "PEDR3", 6.0, VillagerProfession.MASON, 0xFFB0A89A),
	SHEPHERD("shepherd", "PAST3", 5.5, VillagerProfession.SHEPHERD, 0xFFF2F2F2),
	TOOLSMITH("toolsmith", "FERR3", 10.0, VillagerProfession.TOOLSMITH, 0xFF8FB3C9),
	WEAPONSMITH("weaponsmith", "ESPD3", 12.0, VillagerProfession.WEAPONSMITH, 0xFFD96F6F),
	WANDERING_TRADER("wandering_trader", "MERC3", 9.0, null, 0xFF3F8FD2);

	public final String id;
	public final String ticker;
	public final double basePrice;
	public final @Nullable ResourceKey<VillagerProfession> profession;
	/** Cor ARGB usada nos gráficos. */
	public final int color;

	StockDef(String id, String ticker, double basePrice, @Nullable ResourceKey<VillagerProfession> profession, int color) {
		this.id = id;
		this.ticker = ticker;
		this.basePrice = basePrice;
		this.profession = profession;
		this.color = color;
	}

	public String translationKey() {
		return "bolsavillager.stock." + this.id;
	}

	public static @Nullable StockDef byId(String id) {
		for (StockDef def : values()) {
			if (def.id.equals(id)) {
				return def;
			}
		}
		return null;
	}

	public static @Nullable StockDef byProfession(Holder<VillagerProfession> profession) {
		for (StockDef def : values()) {
			if (def.profession != null && profession.is(def.profession)) {
				return def;
			}
		}
		return null;
	}

	/** A ação ligada a uma entidade (villager com profissão ou mercador ambulante), se houver. */
	public static @Nullable StockDef forEntity(Entity entity) {
		if (entity instanceof Villager villager) {
			return byProfession(villager.getVillagerData().profession());
		}
		if (entity instanceof WanderingTrader) {
			return WANDERING_TRADER;
		}
		return null;
	}
}
