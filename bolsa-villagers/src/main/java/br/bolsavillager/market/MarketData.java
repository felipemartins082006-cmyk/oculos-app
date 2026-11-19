package br.bolsavillager.market;

import br.bolsavillager.BolsaMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tudo o que a bolsa salva no mundo (arquivo data/bolsavillager_market.dat). */
public final class MarketData extends SavedData {
	public static final Codec<MarketData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, StockState.CODEC).optionalFieldOf("stocks", Map.of()).forGetter(MarketData::stocksById),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Account.CODEC).optionalFieldOf("accounts", Map.of()).forGetter(d -> d.accounts),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("census", Map.of()).forGetter(d -> d.census),
			Codec.LONG.optionalFieldOf("day", -1L).forGetter(d -> d.day),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("sold_today", Map.of()).forGetter(d -> d.soldToday)
	).apply(i, MarketData::new));

	public static final SavedDataType<MarketData> TYPE = new SavedDataType<>(
			BolsaMod.id("market"), MarketData::new, CODEC, null
	);

	private final EnumMap<StockDef, StockState> stocks = new EnumMap<>(StockDef.class);
	private final Map<UUID, Account> accounts;
	/** Censo de villagers conhecidos: UUID -> id da ação (profissão). Sobrevive a chunks descarregados. */
	private final Map<UUID, String> census;
	public long day;
	/** Quanto cada guilda já comprou de cada item hoje no balcão ("guilda|item" -> unidades). Zera todo dia. */
	private final Map<String, Integer> soldToday;

	public MarketData() {
		this(Map.of(), Map.of(), Map.of(), -1L, Map.of());
	}

	private MarketData(Map<String, StockState> stocks, Map<UUID, Account> accounts, Map<UUID, String> census, long day,
			Map<String, Integer> soldToday) {
		for (StockDef def : StockDef.values()) {
			StockState state = stocks.get(def.id);
			this.stocks.put(def, state != null ? state : StockState.fresh(def));
		}
		this.accounts = new HashMap<>(accounts);
		this.census = new HashMap<>(census);
		this.day = day;
		this.soldToday = new HashMap<>(soldToday);
	}

	public Map<String, Integer> soldToday() {
		return this.soldToday;
	}

	private Map<String, StockState> stocksById() {
		Map<String, StockState> map = new HashMap<>();
		this.stocks.forEach((def, state) -> map.put(def.id, state));
		return map;
	}

	public StockState stock(StockDef def) {
		return this.stocks.get(def);
	}

	public Account account(UUID player) {
		return this.accounts.computeIfAbsent(player, uuid -> new Account());
	}

	public Map<UUID, Account> accounts() {
		return this.accounts;
	}

	public Map<UUID, String> census() {
		return this.census;
	}
}
