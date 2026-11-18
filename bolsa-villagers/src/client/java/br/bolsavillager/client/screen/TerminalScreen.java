package br.bolsavillager.client.screen;

import br.bolsavillager.client.ClientMarket;
import br.bolsavillager.market.MarketEngine;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.net.MarketSyncPayload;
import br.bolsavillager.net.TradeSharesPayload;
import br.bolsavillager.net.WithdrawPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** O "home broker": lista de ações, gráfico, carteira e botões de compra/venda. */
public class TerminalScreen extends Screen {
	private static final int W = 330;
	private static final int H = 214;
	private static final int ROW_H = 13;
	private static final int LIST_W = 122;
	private static final int HEADER_H = 18;

	/** Lembra a ação selecionada entre aberturas do terminal. */
	private static StockDef selected = StockDef.FARMER;

	private int x0;
	private int y0;
	private Button buy1, buy10, sell1, sell10, withdraw;

	public TerminalScreen() {
		super(Component.translatable("bolsavillager.terminal.title"));
	}

	@Override
	protected void init() {
		this.x0 = (this.width - W) / 2;
		this.y0 = (this.height - H) / 2;
		int rx = this.x0 + LIST_W + 10;
		int by = this.y0 + 146;
		int bw = 46;
		this.buy1 = this.addRenderableWidget(Button.builder(Component.translatable("bolsavillager.button.buy", 1), b -> order(1))
				.bounds(rx, by, bw, 18).build());
		this.buy10 = this.addRenderableWidget(Button.builder(Component.translatable("bolsavillager.button.buy", 10), b -> order(10))
				.bounds(rx + bw + 2, by, bw, 18).build());
		this.sell1 = this.addRenderableWidget(Button.builder(Component.translatable("bolsavillager.button.sell", 1), b -> order(-1))
				.bounds(rx + (bw + 2) * 2, by, bw, 18).build());
		this.sell10 = this.addRenderableWidget(Button.builder(Component.translatable("bolsavillager.button.sell", 10), b -> order(-10))
				.bounds(rx + (bw + 2) * 3, by, bw, 18).build());
		this.withdraw = this.addRenderableWidget(Button.builder(Component.translatable("bolsavillager.button.withdraw"),
						b -> ClientPlayNetworking.send(WithdrawPayload.INSTANCE))
				.bounds(this.x0 + W - 6 - 70, this.y0 + 178, 70, 18)
				.tooltip(Tooltip.create(Component.translatable("bolsavillager.button.withdraw.tooltip")))
				.build());
		this.updateButtons();
	}

	private void order(int quantity) {
		ClientPlayNetworking.send(new TradeSharesPayload(selected.id, quantity));
	}

	@Override
	public void tick() {
		this.updateButtons();
	}

	private void updateButtons() {
		if (this.buy1 == null) {
			return;
		}
		MarketSyncPayload.StockView view = ClientMarket.get(selected);
		int owned = ClientMarket.account().shares().getOrDefault(selected.id, 0);
		double funds = ClientMarket.account().balance() + ClientMarket.emeraldsInInventory();
		double cost1 = view == null ? Double.MAX_VALUE : MarketEngine.orderValue(view.price(), 1) * (1 + MarketEngine.FEE);
		double cost10 = view == null ? Double.MAX_VALUE : MarketEngine.orderValue(view.price(), 10) * (1 + MarketEngine.FEE);
		this.buy1.active = funds >= cost1;
		this.buy10.active = funds >= cost10;
		this.sell1.active = owned >= 1;
		this.sell10.active = owned >= 10;
		this.withdraw.active = ClientMarket.account().balance() >= 1.0;
		if (view != null) {
			this.buy1.setTooltip(Tooltip.create(Component.translatable("bolsavillager.button.cost", MarketUi.money(cost1))));
			this.buy10.setTooltip(Tooltip.create(Component.translatable("bolsavillager.button.cost", MarketUi.money(cost10))));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int listX = this.x0 + 6;
		int listY = this.y0 + HEADER_H + 6;
		if (event.x() >= listX && event.x() < listX + LIST_W) {
			int row = (int) Math.floor((event.y() - listY) / ROW_H);
			if (event.y() >= listY && row >= 0 && row < StockDef.values().length) {
				selected = StockDef.values()[row];
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4F));
				this.updateButtons();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		MarketUi.panel(g, this.x0, this.y0, W, H);

		// Cabeçalho com o índice IVIL
		g.fill(this.x0 + 1, this.y0 + 1, this.x0 + W - 1, this.y0 + HEADER_H, MarketUi.BG_SOFT);
		g.text(this.font, Component.translatable("bolsavillager.terminal.title").withStyle(ChatFormatting.BOLD),
				this.x0 + 6, this.y0 + 5, MarketUi.ACCENT, false);
		double idxChange = ClientMarket.indexChangePercent();
		String idx = "IVIL " + String.format(java.util.Locale.ROOT, "%.0f", ClientMarket.index()) + " ";
		String idxDelta = MarketUi.arrow(idxChange) + " " + MarketUi.percent(idxChange);
		int right = this.x0 + W - 6;
		MarketUi.textRight(g, this.font, idxDelta, right, this.y0 + 5, MarketUi.changeColor(idxChange));
		MarketUi.textRight(g, this.font, idx, right - this.font.width(idxDelta), this.y0 + 5, MarketUi.TEXT);

		if (!ClientMarket.hasData()) {
			g.centeredText(this.font, Component.translatable("bolsavillager.terminal.loading"),
					this.x0 + W / 2, this.y0 + H / 2, MarketUi.TEXT_DIM);
			super.extractRenderState(g, mouseX, mouseY, a);
			return;
		}

		this.extractList(g, mouseX, mouseY);
		this.extractDetails(g);
		this.extractFooter(g);
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void extractList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = this.x0 + 6;
		int y = this.y0 + HEADER_H + 6;
		g.fill(x - 1, y - 1, x + LIST_W + 1, y + ROW_H * StockDef.values().length + 1, 0x40000000);
		for (StockDef def : StockDef.values()) {
			boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + ROW_H;
			if (def == selected) {
				g.fill(x, y, x + LIST_W, y + ROW_H, 0x501FA35A);
			} else if (hover) {
				g.fill(x, y, x + LIST_W, y + ROW_H, 0x20FFFFFF);
			}
			g.fill(x + 2, y + 4, x + 6, y + 8, def.color);
			int owned = ClientMarket.account().shares().getOrDefault(def.id, 0);
			g.text(this.font, def.ticker, x + 9, y + 3, owned > 0 ? 0xFFFFE27A : MarketUi.TEXT, false);
			MarketSyncPayload.StockView view = ClientMarket.get(def);
			if (view != null) {
				double change = ClientMarket.changePercent(view);
				MarketUi.textRight(g, this.font, MarketUi.money(view.price()), x + 78, y + 3, MarketUi.TEXT);
				MarketUi.textRight(g, this.font, MarketUi.percent(change), x + LIST_W - 2, y + 3, MarketUi.changeColor(change));
			}
			y += ROW_H;
		}
	}

	private void extractDetails(GuiGraphicsExtractor g) {
		int x = this.x0 + LIST_W + 10;
		int w = W - LIST_W - 16;
		int y = this.y0 + HEADER_H + 6;
		MarketSyncPayload.StockView view = ClientMarket.get(selected);
		if (view == null) {
			return;
		}
		double change = ClientMarket.changePercent(view);

		g.fill(x, y + 1, x + 3, y + 8, selected.color);
		g.text(this.font, MarketUi.name(selected), x + 6, y, MarketUi.TEXT, false);
		MarketUi.textRight(g, this.font, selected.ticker, x + w, y, MarketUi.ACCENT);

		y += 12;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(1.5F, 1.5F);
		g.text(this.font, MarketUi.money(view.price()) + MarketUi.EMERALD, 0, 0, MarketUi.TEXT, false);
		g.pose().popMatrix();
		MarketUi.textRight(g, this.font, MarketUi.arrow(change) + " " + MarketUi.percent(change) + " " +
				Component.translatable("bolsavillager.terminal.today").getString(), x + w, y + 3, MarketUi.changeColor(change));

		y += 18;
		MarketUi.chart(g, view.history(), x, y, w, 64, true);
		g.text(this.font, Component.translatable("bolsavillager.terminal.last_hour"), x + 2, y + 2, MarketUi.TEXT_DIM, false);

		y += 68;
		g.text(this.font, Component.translatable(selected == StockDef.WANDERING_TRADER
						? "bolsavillager.terminal.stats_wandering" : "bolsavillager.terminal.stats",
				view.population(), view.volumeDay(), view.tradesDay()), x, y, MarketUi.TEXT_DIM, false);

		y += 11;
		int owned = ClientMarket.account().shares().getOrDefault(selected.id, 0);
		if (owned > 0) {
			double basis = ClientMarket.account().costBasis().getOrDefault(selected.id, 0.0);
			double avg = basis / owned;
			double pl = view.price() * owned - basis;
			Component line = Component.translatable("bolsavillager.terminal.position", owned, MarketUi.money(avg));
			g.text(this.font, line, x, y, MarketUi.TEXT, false);
			String plText = (pl >= 0 ? "+" : "") + MarketUi.money(pl) + MarketUi.EMERALD;
			MarketUi.textRight(g, this.font, plText, x + w, y, MarketUi.changeColor(pl));
		} else {
			g.text(this.font, Component.translatable("bolsavillager.terminal.no_position"), x, y, MarketUi.TEXT_DIM, false);
		}
	}

	private void extractFooter(GuiGraphicsExtractor g) {
		int y = this.y0 + 172;
		int x = this.x0 + LIST_W + 10;
		g.horizontalLine(x, this.x0 + W - 6, y - 5, 0x40FFFFFF);
		MarketSyncPayload.AccountView acc = ClientMarket.account();
		g.text(this.font, Component.translatable("bolsavillager.terminal.balance", MarketUi.money(acc.balance())),
				x, y, MarketUi.TEXT, false);
		g.text(this.font, Component.translatable("bolsavillager.terminal.wallet",
				MarketUi.money(ClientMarket.portfolioValue())), x, y + 11, MarketUi.TEXT_DIM, false);
		g.text(this.font, Component.translatable("bolsavillager.terminal.inventory",
				ClientMarket.emeraldsInInventory()), x, y + 22, MarketUi.TEXT_DIM, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
