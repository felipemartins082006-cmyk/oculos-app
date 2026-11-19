package br.bolsavillager.net;

import br.bolsavillager.BolsaMod;
import br.bolsavillager.market.Account;
import br.bolsavillager.market.LimitOrder;
import br.bolsavillager.market.MarketData;
import br.bolsavillager.market.MarketRules;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.market.StockState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servidor -> cliente: cotações de todas as ações, a conta do jogador, as regras do servidor,
 * quanto as guildas já compraram hoje no balcão e se há um raid acontecendo.
 */
public record MarketSyncPayload(List<StockView> stocks, AccountView account, MarketRules rules,
		Map<String, Integer> soldToday, boolean raidOngoing) implements CustomPacketPayload {
	public static final Type<MarketSyncPayload> TYPE = new Type<>(BolsaMod.id("market_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MarketSyncPayload> CODEC =
			CustomPacketPayload.codec(MarketSyncPayload::write, MarketSyncPayload::read);

	/** Retrato de uma ação para a interface. */
	public record StockView(String id, double price, double dayOpen, float[] history, int population, int volumeDay, int tradesDay) {
	}

	/** Retrato da conta do jogador. */
	public record AccountView(Map<String, Integer> shares, Map<String, Integer> costBasis,
			int lastDividend, int totalDividends, List<LimitOrder> orders) {
		public static final AccountView EMPTY = new AccountView(Map.of(), Map.of(), 0, 0, List.of());

		public int reserved() {
			int total = 0;
			for (LimitOrder o : this.orders) {
				total += o.reserved();
			}
			return total;
		}
	}

	public static MarketSyncPayload of(MarketData data, Account account, MarketRules rules, boolean raidOngoing) {
		List<StockView> stocks = new ArrayList<>();
		for (StockDef def : StockDef.values()) {
			StockState s = data.stock(def);
			float[] history = new float[s.history.size()];
			for (int i = 0; i < history.length; i++) {
				history[i] = s.history.get(i).floatValue();
			}
			stocks.add(new StockView(def.id, s.price, s.dayOpen, history, s.population, s.volumeDay, s.tradesDay));
		}
		return new MarketSyncPayload(stocks, new AccountView(Map.copyOf(account.shares),
				Map.copyOf(account.costBasis), account.lastDividend, account.totalDividends, List.copyOf(account.orders)),
				rules, Map.copyOf(data.soldToday()), raidOngoing);
	}

	public static void writeOrder(RegistryFriendlyByteBuf buf, LimitOrder order) {
		buf.writeVarInt(order.type().ordinal());
		buf.writeUtf(order.stockId(), 64);
		buf.writeVarInt(order.quantity());
		buf.writeVarInt(order.target());
		buf.writeVarInt(order.reserved());
	}

	public static LimitOrder readOrder(RegistryFriendlyByteBuf buf) {
		LimitOrder.Type[] types = LimitOrder.Type.values();
		int type = Math.floorMod(buf.readVarInt(), types.length);
		return new LimitOrder(types[type], buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(this.stocks.size());
		for (StockView s : this.stocks) {
			buf.writeUtf(s.id());
			buf.writeDouble(s.price());
			buf.writeDouble(s.dayOpen());
			buf.writeVarInt(s.history().length);
			for (float v : s.history()) {
				buf.writeFloat(v);
			}
			buf.writeVarInt(s.population());
			buf.writeVarInt(s.volumeDay());
			buf.writeVarInt(s.tradesDay());
		}
		buf.writeVarInt(this.account.shares().size());
		this.account.shares().forEach((id, qty) -> {
			buf.writeUtf(id);
			buf.writeVarInt(qty);
			buf.writeVarInt(this.account.costBasis().getOrDefault(id, 0));
		});
		buf.writeVarInt(this.account.lastDividend());
		buf.writeVarInt(this.account.totalDividends());
		buf.writeVarInt(this.account.orders().size());
		for (LimitOrder order : this.account.orders()) {
			writeOrder(buf, order);
		}
		this.rules.write(buf);
		buf.writeVarInt(this.soldToday.size());
		this.soldToday.forEach((key, sold) -> {
			buf.writeUtf(key);
			buf.writeVarInt(sold);
		});
		buf.writeBoolean(this.raidOngoing);
	}

	private static MarketSyncPayload read(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<StockView> stocks = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String id = buf.readUtf();
			double price = buf.readDouble();
			double dayOpen = buf.readDouble();
			float[] history = new float[Math.min(buf.readVarInt(), StockState.HISTORY_SIZE * 2)];
			for (int h = 0; h < history.length; h++) {
				history[h] = buf.readFloat();
			}
			stocks.add(new StockView(id, price, dayOpen, history, buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
		}
		int held = buf.readVarInt();
		Map<String, Integer> shares = new HashMap<>();
		Map<String, Integer> cost = new HashMap<>();
		for (int i = 0; i < held; i++) {
			String id = buf.readUtf();
			shares.put(id, buf.readVarInt());
			cost.put(id, buf.readVarInt());
		}
		int lastDividend = buf.readVarInt();
		int totalDividends = buf.readVarInt();
		int orderCount = Math.min(buf.readVarInt(), 64);
		List<LimitOrder> orders = new ArrayList<>();
		for (int i = 0; i < orderCount; i++) {
			orders.add(readOrder(buf));
		}
		MarketRules rules = MarketRules.read(buf);
		int soldCount = buf.readVarInt();
		Map<String, Integer> soldToday = new HashMap<>();
		for (int i = 0; i < soldCount; i++) {
			soldToday.put(buf.readUtf(), buf.readVarInt());
		}
		boolean raid = buf.readBoolean();
		return new MarketSyncPayload(stocks,
				new AccountView(shares, cost, lastDividend, totalDividends, orders), rules, soldToday, raid);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
