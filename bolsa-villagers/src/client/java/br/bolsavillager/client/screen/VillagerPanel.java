package br.bolsavillager.client.screen;

import br.bolsavillager.client.ClientMarket;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.net.MarketSyncPayload;
import br.bolsavillager.net.TradeSharesPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;

/**
 * Painel da bolsa que aparece grudado na tela de trocas do villager:
 * cotação da guilda dele, mini-gráfico, efeito nos preços das trocas e botões de comprar/vender ação.
 */
public final class VillagerPanel {
	private static final int MERCHANT_W = 276;
	private static final int MERCHANT_H = 166;
	private static final int PANEL_W = 92;
	private static final int PANEL_H = 140;

	private enum Mode { SIDE, STRIP }

	private VillagerPanel() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof MerchantScreen) {
				attach(screen, width, height);
			}
		});
	}

	private static void attach(Screen screen, int width, int height) {
		StockDef def = ClientMarket.lastMerchantStock();
		if (def == null) {
			return;
		}
		int left = (width - MERCHANT_W) / 2;
		int top = (height - MERCHANT_H) / 2;
		Mode mode;
		int px, py, pw, ph;
		if (left >= PANEL_W + 6) {
			mode = Mode.SIDE;
			px = left + MERCHANT_W + 4;
			py = top;
			pw = PANEL_W;
			ph = PANEL_H;
		} else if (top >= 26) {
			mode = Mode.STRIP;
			px = left;
			py = top - 24;
			pw = MERCHANT_W;
			ph = 22;
		} else {
			return; // sem espaço na tela (GUI muito grande)
		}

		Button buy;
		Button sell;
		if (mode == Mode.SIDE) {
			int bw = (pw - 10) / 2;
			buy = Button.builder(Component.translatable("bolsavillager.button.buy_short"), b -> order(def, 1))
					.bounds(px + 4, py + ph - 22, bw, 18).build();
			sell = Button.builder(Component.translatable("bolsavillager.button.sell_short"), b -> order(def, -1))
					.bounds(px + 6 + bw, py + ph - 22, bw, 18).build();
		} else {
			buy = Button.builder(Component.literal("+1"), b -> order(def, 1)).bounds(px + pw - 62, py + 3, 28, 16).build();
			sell = Button.builder(Component.literal("-1"), b -> order(def, -1)).bounds(px + pw - 31, py + 3, 28, 16).build();
		}
		Screens.getWidgets(screen).add(buy);
		Screens.getWidgets(screen).add(sell);
		updateButtons(def, buy, sell);

		ScreenEvents.afterTick(screen).register(s -> updateButtons(def, buy, sell));
		// Desenha no estrato do fundo, para os botões ficarem por cima do painel.
		ScreenEvents.afterBackground(screen).register((s, g, mouseX, mouseY, a) -> {
			Font font = s.getFont();
			if (mode == Mode.SIDE) {
				extractSide(g, font, def, px, py, pw, ph);
			} else {
				extractStrip(g, font, def, px, py, pw, ph);
			}
		});
	}

	private static void order(StockDef def, int quantity) {
		ClientPlayNetworking.send(new TradeSharesPayload(def.id, quantity));
	}

	private static void updateButtons(StockDef def, Button buy, Button sell) {
		MarketSyncPayload.StockView view = ClientMarket.get(def);
		int owned = ClientMarket.account().shares().getOrDefault(def.id, 0);
		double funds = ClientMarket.account().balance() + ClientMarket.emeraldsInInventory();
		double cost = view == null ? Double.MAX_VALUE : ClientMarket.rules().buyCost(view.price(), 1);
		buy.active = funds >= cost;
		sell.active = owned > 0;
		if (view != null) {
			buy.setTooltip(Tooltip.create(Component.translatable("bolsavillager.panel.buy_tooltip",
					def.ticker, MarketUi.money(cost))));
			sell.setTooltip(Tooltip.create(Component.translatable("bolsavillager.panel.sell_tooltip",
					def.ticker, owned)));
		}
	}

	/** Mesmo cálculo que o servidor usa nas trocas, com as regras recebidas dele. */
	private static double priceEffectPercent(StockDef def, MarketSyncPayload.StockView view) {
		return ClientMarket.rules().tradePriceFactor(def, view.price(), view.population()) * 100.0;
	}

	private static void extractSide(GuiGraphicsExtractor g, Font font, StockDef def, int x, int y, int w, int h) {
		MarketUi.panel(g, x, y, w, h);
		g.fill(x + 1, y + 1, x + w - 1, y + 13, MarketUi.BG_SOFT);
		g.text(font, Component.translatable("bolsavillager.panel.title"), x + 5, y + 3, MarketUi.ACCENT, false);
		MarketUi.textRight(g, font, def.ticker, x + w - 5, y + 3, MarketUi.TEXT);

		MarketSyncPayload.StockView view = ClientMarket.get(def);
		if (view == null) {
			g.text(font, Component.translatable("bolsavillager.terminal.loading"), x + 5, y + 20, MarketUi.TEXT_DIM, false);
			return;
		}
		double change = ClientMarket.changePercent(view);
		int cy = y + 17;
		g.text(font, font.plainSubstrByWidth(MarketUi.name(def).getString(), w - 10), x + 5, cy, MarketUi.TEXT_DIM, false);
		cy += 11;
		g.text(font, MarketUi.money(view.price()) + MarketUi.EMERALD, x + 5, cy, MarketUi.TEXT, false);
		MarketUi.textRight(g, font, MarketUi.arrow(change) + " " + MarketUi.percent(change), x + w - 5, cy, MarketUi.changeColor(change));
		cy += 11;
		MarketUi.chart(g, view.history(), x + 5, cy, w - 10, 34, false);
		cy += 38;

		double effect = priceEffectPercent(def, view);
		String effectText = (effect >= 0 ? "+" : "") + String.format(java.util.Locale.ROOT, "%.0f%%", effect);
		g.text(font, Component.translatable("bolsavillager.panel.trade_prices"), x + 5, cy, MarketUi.TEXT_DIM, false);
		// Para o jogador, trocas mais caras são ruins: vermelho quando sobe.
		MarketUi.textRight(g, font, effectText, x + w - 5, cy, MarketUi.changeColor(-effect));
		cy += 11;
		int owned = ClientMarket.account().shares().getOrDefault(def.id, 0);
		g.text(font, Component.translatable("bolsavillager.panel.owned", owned), x + 5, cy, owned > 0 ? 0xFFFFE27A : MarketUi.TEXT_DIM, false);
		cy += 11;
		g.text(font, Component.translatable("bolsavillager.panel.balance", MarketUi.money(ClientMarket.account().balance())),
				x + 5, cy, MarketUi.TEXT_DIM, false);
	}

	private static void extractStrip(GuiGraphicsExtractor g, Font font, StockDef def, int x, int y, int w, int h) {
		MarketUi.panel(g, x, y, w, h);
		int used = MarketUi.quote(g, font, def, x + 5, y + 7);
		MarketSyncPayload.StockView view = ClientMarket.get(def);
		int chartX = x + 10 + used;
		int chartW = x + w - 68 - chartX;
		if (view != null && chartW > 20) {
			MarketUi.chart(g, view.history(), chartX, y + 3, chartW, h - 6, false);
		}
	}
}
