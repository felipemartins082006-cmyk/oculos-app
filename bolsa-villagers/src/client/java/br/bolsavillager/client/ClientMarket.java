package br.bolsavillager.client;

import br.bolsavillager.market.MarketRules;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.net.MarketSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Última cotação recebida do servidor, guardada no cliente para as telas. */
public final class ClientMarket {
	private static final Map<StockDef, MarketSyncPayload.StockView> stocks = new EnumMap<>(StockDef.class);
	private static MarketSyncPayload.AccountView account = MarketSyncPayload.AccountView.EMPTY;
	/** Regras do servidor (corretagem, impacto, efeito nas trocas, dividendos). */
	private static MarketRules rules = MarketRules.DEFAULT;
	/** Ação do último villager/mercador com quem o jogador interagiu (para o painel na tela de trocas). */
	private static @Nullable StockDef lastMerchantStock;

	private ClientMarket() {
	}

	public static void accept(MarketSyncPayload payload) {
		for (MarketSyncPayload.StockView view : payload.stocks()) {
			StockDef def = StockDef.byId(view.id());
			if (def != null) {
				stocks.put(def, view);
			}
		}
		account = payload.account();
		rules = payload.rules();
	}

	public static MarketRules rules() {
		return rules;
	}

	public static void clear() {
		stocks.clear();
		account = MarketSyncPayload.AccountView.EMPTY;
		rules = MarketRules.DEFAULT;
		lastMerchantStock = null;
	}

	public static boolean hasData() {
		return !stocks.isEmpty();
	}

	public static MarketSyncPayload.@Nullable StockView get(StockDef def) {
		return stocks.get(def);
	}

	public static MarketSyncPayload.AccountView account() {
		return account;
	}

	public static @Nullable StockDef lastMerchantStock() {
		return lastMerchantStock;
	}

	public static void setLastMerchantStock(@Nullable StockDef def) {
		lastMerchantStock = def;
	}

	public static double changePercent(MarketSyncPayload.StockView view) {
		return view.dayOpen() > 0 ? (view.price() / view.dayOpen() - 1.0) * 100.0 : 0.0;
	}

	/** Índice IVIL: média das ações ponderada pelo preço base, começando em 1000 pontos. */
	public static double index() {
		double now = 0, base = 0;
		for (StockDef def : StockDef.values()) {
			MarketSyncPayload.StockView view = stocks.get(def);
			if (view != null) {
				now += view.price();
				base += def.basePrice;
			}
		}
		return base > 0 ? now / base * 1000.0 : 1000.0;
	}

	public static double indexChangePercent() {
		double now = 0, open = 0;
		for (MarketSyncPayload.StockView view : stocks.values()) {
			now += view.price();
			open += view.dayOpen();
		}
		return open > 0 ? (now / open - 1.0) * 100.0 : 0.0;
	}

	/** Valor de mercado da carteira do jogador. */
	public static double portfolioValue() {
		double total = 0;
		for (Map.Entry<String, Integer> e : account.shares().entrySet()) {
			StockDef def = StockDef.byId(e.getKey());
			MarketSyncPayload.StockView view = def == null ? null : stocks.get(def);
			if (view != null) {
				total += view.price() * e.getValue();
			}
		}
		return total;
	}

	public static int emeraldsInInventory() {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(Items.EMERALD)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
