package br.bolsavillager.net;

import br.bolsavillager.BolsaMod;
import br.bolsavillager.market.Account;
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

/** Servidor -> cliente: cotações de todas as ações + a conta do jogador. Pode pedir para abrir o terminal. */
public record MarketSyncPayload(boolean openTerminal, List<StockView> stocks, AccountView account, MarketRules rules)
		implements CustomPacketPayload {
	public static final Type<MarketSyncPayload> TYPE = new Type<>(BolsaMod.id("market_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MarketSyncPayload> CODEC =
			CustomPacketPayload.codec(MarketSyncPayload::write, MarketSyncPayload::read);

	/** Retrato de uma ação para a interface. */
	public record StockView(String id, double price, double dayOpen, float[] history, int population, int volumeDay, int tradesDay) {
	}

	/** Retrato da conta do jogador. */
	public record AccountView(double balance, Map<String, Integer> shares, Map<String, Double> costBasis,
			double lastDividend, double totalDividends) {
		public static final AccountView EMPTY = new AccountView(0, Map.of(), Map.of(), 0, 0);
	}

	public static MarketSyncPayload of(MarketData data, Account account, MarketRules rules, boolean openTerminal) {
		List<StockView> stocks = new ArrayList<>();
		for (StockDef def : StockDef.values()) {
			StockState s = data.stock(def);
			float[] history = new float[s.history.size()];
			for (int i = 0; i < history.length; i++) {
				history[i] = s.history.get(i).floatValue();
			}
			stocks.add(new StockView(def.id, s.price, s.dayOpen, history, s.population, s.volumeDay, s.tradesDay));
		}
		return new MarketSyncPayload(openTerminal, stocks, new AccountView(account.balance, Map.copyOf(account.shares),
				Map.copyOf(account.costBasis), account.lastDividend, account.totalDividends), rules);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(this.openTerminal);
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
		buf.writeDouble(this.account.balance());
		buf.writeVarInt(this.account.shares().size());
		this.account.shares().forEach((id, qty) -> {
			buf.writeUtf(id);
			buf.writeVarInt(qty);
			buf.writeDouble(this.account.costBasis().getOrDefault(id, 0.0));
		});
		buf.writeDouble(this.account.lastDividend());
		buf.writeDouble(this.account.totalDividends());
		this.rules.write(buf);
	}

	private static MarketSyncPayload read(RegistryFriendlyByteBuf buf) {
		boolean open = buf.readBoolean();
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
		double balance = buf.readDouble();
		int held = buf.readVarInt();
		Map<String, Integer> shares = new HashMap<>();
		Map<String, Double> cost = new HashMap<>();
		for (int i = 0; i < held; i++) {
			String id = buf.readUtf();
			shares.put(id, buf.readVarInt());
			cost.put(id, buf.readDouble());
		}
		double lastDividend = buf.readDouble();
		double totalDividends = buf.readDouble();
		MarketRules rules = MarketRules.read(buf);
		return new MarketSyncPayload(open, stocks, new AccountView(balance, shares, cost, lastDividend, totalDividends), rules);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
