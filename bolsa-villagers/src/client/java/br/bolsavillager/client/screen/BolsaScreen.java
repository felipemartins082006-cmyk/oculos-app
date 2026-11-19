package br.bolsavillager.client.screen;

import br.bolsavillager.client.ClientMarket;
import br.bolsavillager.market.ItemDemand;
import br.bolsavillager.market.LimitOrder;
import br.bolsavillager.market.MarketEngine;
import br.bolsavillager.market.MarketRules;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.net.CancelOrderPayload;
import br.bolsavillager.net.DepositPayload;
import br.bolsavillager.net.MarketSyncPayload;
import br.bolsavillager.net.PlaceOrderPayload;
import br.bolsavillager.net.SellItemsPayload;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A tela da bolsa, aberta pela tecla (padrão B) em qualquer lugar. Quatro abas:
 * Ações (comprar/vender na hora), Ordens (programadas), Vender itens (balcão das guildas) e Carteira.
 */
public class BolsaScreen extends Screen {
	public static final int W = 330;
	public static final int H = 216;
	public static final int LIST_W = 122;
	private static final int ROW_H = 12;
	public static final int ITEM_ROW_H = 18;
	private static final int ITEM_ROWS = 9;
	private static final int[] QTY = {1, 2, 5, 10, 16, 32, 64};
	private static final int[] SALE_QTY = {1, 4, 8, 16, 32, 64};

	public enum Tab {
		STOCKS("bolsavillager.tab.stocks"),
		ORDERS("bolsavillager.tab.orders"),
		SELL("bolsavillager.tab.sell"),
		WALLET("bolsavillager.tab.wallet");

		final String key;

		Tab(String key) {
			this.key = key;
		}
	}

	// Estado lembrado entre aberturas
	private static Tab tab = Tab.STOCKS;
	private static StockDef selected = StockDef.FARMER;
	private static int qtyIndex = 0;
	private static LimitOrder.Type orderType = LimitOrder.Type.BUY_BELOW;
	private static double orderOffset = -0.05;
	private static @Nullable Item saleItem;
	private static @Nullable StockDef saleGuild;
	private static int saleQtyIndex = 2;
	private static boolean saleAll = true;

	private int x0;
	private int y0;
	private int ct;
	private int rx;
	private int rw;
	private int saleScroll;
	private int seenVersion = -1;
	private int seenOrders = -1;
	private final List<Runnable> updaters = new ArrayList<>();

	public BolsaScreen() {
		super(Component.translatable("bolsavillager.screen.title"));
	}

	public static void openTab(Tab t) {
		tab = t;
	}

	// ---------------------------------------------------------------------------------------------
	// Widgets
	// ---------------------------------------------------------------------------------------------

	@Override
	protected void init() {
		this.x0 = (this.width - W) / 2;
		this.y0 = (this.height - H) / 2;
		this.ct = this.y0 + 38;
		this.rx = this.x0 + LIST_W + 12;
		this.rw = W - LIST_W - 18;
		this.updaters.clear();

		int tw = (W - 12 - 6) / 4;
		for (Tab t : Tab.values()) {
			Button b = this.addRenderableWidget(Button.builder(Component.translatable(t.key), btn -> {
				tab = t;
				this.click();
				this.rebuildWidgets();
			}).bounds(this.x0 + 6 + t.ordinal() * (tw + 2), this.y0 + 18, tw, 16).build());
			b.active = t != tab;
		}

		switch (tab) {
			case STOCKS -> this.initStocks();
			case ORDERS -> this.initOrders();
			case SELL -> this.initSell();
			case WALLET -> this.initWallet();
		}
		this.seenOrders = ClientMarket.account().orders().size();
		this.updateWidgets();
	}

	private Button button(Component label, int x, int y, int w, int h, Button.OnPress onPress) {
		return this.addRenderableWidget(Button.builder(label, onPress).bounds(x, y, w, h).build());
	}

	private int qty() {
		return QTY[qtyIndex];
	}

	/** Linha "[-] 10 [+]" usada em Ações e Ordens. Retorna a coordenada x logo depois. */
	private int qtySelector(int x, int y) {
		this.button(Component.literal("-"), x, y, 16, 16, b -> {
			qtyIndex = Math.max(0, qtyIndex - 1);
			this.updateWidgets();
		});
		this.button(Component.literal("+"), x + 58, y, 16, 16, b -> {
			qtyIndex = Math.min(QTY.length - 1, qtyIndex + 1);
			this.updateWidgets();
		});
		return x + 76;
	}

	private void initStocks() {
		int y = this.ct + 108;
		int after = this.qtySelector(this.rx, y);
		Button max = this.button(Component.translatable("bolsavillager.button.max"), after, y, 30, 16, b -> {
			MarketSyncPayload.StockView v = ClientMarket.get(selected);
			if (v == null) {
				return;
			}
			double funds = ClientMarket.account().balance() + ClientMarket.emeraldsInInventory();
			int n = ClientMarket.rules().maxAffordable(v.price(), funds, ClientMarket.heroLevel(), MarketEngine.MAX_ORDER);
			int best = 0;
			for (int i = 0; i < QTY.length; i++) {
				if (QTY[i] <= Math.max(1, n)) {
					best = i;
				}
			}
			qtyIndex = best;
			this.updateWidgets();
		});
		max.setTooltip(Tooltip.create(Component.translatable("bolsavillager.button.max.tooltip")));

		Button sellAll = this.button(Component.translatable("bolsavillager.button.sell_all"), after + 32, y,
				this.rx + this.rw - (after + 32), 16, b -> {
			int owned = ClientMarket.account().shares().getOrDefault(selected.id, 0);
			while (owned > 0) {
				int lot = Math.min(owned, MarketEngine.MAX_ORDER);
				this.trade(-lot);
				owned -= lot;
			}
		});

		int by = this.ct + 128;
		int bw = (this.rw - 2) / 2;
		Button buy = this.button(Component.empty(), this.rx, by, bw, 18, b -> this.trade(this.qty()));
		Button sell = this.button(Component.empty(), this.rx + bw + 2, by, this.rw - bw - 2, 18, b -> this.trade(-this.qty()));
		this.updaters.add(() -> {
			MarketSyncPayload.StockView v = ClientMarket.get(selected);
			int owned = ClientMarket.account().shares().getOrDefault(selected.id, 0);
			double funds = ClientMarket.account().balance() + ClientMarket.emeraldsInInventory();
			double cost = v == null ? Double.MAX_VALUE : ClientMarket.rules().buyCost(v.price(), this.qty(), ClientMarket.heroLevel());
			buy.setMessage(Component.translatable("bolsavillager.button.buy", this.qty()));
			sell.setMessage(Component.translatable("bolsavillager.button.sell", this.qty()));
			buy.active = funds >= cost;
			sell.active = owned >= this.qty();
			sellAll.active = owned > 0;
			if (v != null) {
				buy.setTooltip(Tooltip.create(Component.translatable("bolsavillager.button.cost", MarketUi.money(cost))));
				sell.setTooltip(Tooltip.create(Component.translatable("bolsavillager.button.proceeds",
						MarketUi.money(ClientMarket.rules().sellProceeds(v.price(), this.qty(), ClientMarket.heroLevel())))));
			}
		});
	}

	private void initOrders() {
		Button type = this.button(Component.empty(), this.rx, this.ct + 12, this.rw, 16, b -> {
			orderType = orderType.next();
			orderOffset = switch (orderType) {
				case BUY_BELOW -> -0.05;
				case SELL_ABOVE -> 0.05;
				case STOP_LOSS -> -0.10;
			};
			this.updateWidgets();
		});
		this.updaters.add(() -> type.setMessage(Component.translatable("bolsavillager.order.type",
				Component.translatable(orderType.translationKey()))));

		int y = this.ct + 31;
		this.button(Component.literal("-5%"), this.rx, y, 24, 16, b -> this.offset(-0.05));
		this.button(Component.literal("-1%"), this.rx + 26, y, 24, 16, b -> this.offset(-0.01));
		this.button(Component.literal("+1%"), this.rx + this.rw - 50, y, 24, 16, b -> this.offset(0.01));
		this.button(Component.literal("+5%"), this.rx + this.rw - 24, y, 24, 16, b -> this.offset(0.05));

		int y2 = this.ct + 50;
		this.qtySelector(this.rx, y2);
		Button create = this.button(Component.translatable("bolsavillager.order.create"), this.rx + this.rw - 76, y2, 76, 16, b -> {
			MarketSyncPayload.StockView v = ClientMarket.get(selected);
			if (v != null) {
				double target = Math.max(MarketEngine.MIN_PRICE, v.price() * (1 + orderOffset));
				ClientPlayNetworking.send(new PlaceOrderPayload(new LimitOrder(orderType, selected.id, this.qty(), target)));
			}
		});
		this.updaters.add(() -> {
			create.active = ClientMarket.account().orders().size() < LimitOrder.MAX_PER_PLAYER;
			create.setTooltip(Tooltip.create(Component.translatable(orderType.isBuy()
					? "bolsavillager.order.create.tooltip_buy" : "bolsavillager.order.create.tooltip_sell")));
		});

		List<LimitOrder> orders = ClientMarket.account().orders();
		for (int i = 0; i < orders.size(); i++) {
			int index = i;
			Button cancel = this.button(Component.literal("✕"), this.rx + this.rw - 12, this.ct + 86 + i * 14, 12, 12,
					b -> ClientPlayNetworking.send(new CancelOrderPayload(index)));
			cancel.setTooltip(Tooltip.create(Component.translatable("bolsavillager.order.cancel")));
		}
	}

	private void offset(double delta) {
		orderOffset = Math.max(-0.9, Math.min(3.0, Math.round((orderOffset + delta) * 100) / 100.0));
		this.updateWidgets();
	}

	private void initSell() {
		int y = this.ct + 104;
		this.button(Component.literal("-"), this.rx, y, 16, 16, b -> {
			if (saleAll) {
				saleAll = false;
				saleQtyIndex = SALE_QTY.length - 1;
			} else {
				saleQtyIndex = Math.max(0, saleQtyIndex - 1);
			}
			this.updateWidgets();
		});
		this.button(Component.literal("+"), this.rx + 58, y, 16, 16, b -> {
			if (!saleAll) {
				if (saleQtyIndex == SALE_QTY.length - 1) {
					saleAll = true;
				} else {
					saleQtyIndex++;
				}
			}
			this.updateWidgets();
		});
		Button all = this.button(Component.translatable("bolsavillager.sale.all"), this.rx + 76, y, 36, 16, b -> {
			saleAll = true;
			this.updateWidgets();
		});
		Button sell = this.button(Component.empty(), this.rx, this.ct + 144, this.rw, 18, b -> {
			if (saleItem != null && saleGuild != null) {
				Identifier id = BuiltInRegistries.ITEM.getKey(saleItem);
				ClientPlayNetworking.send(new SellItemsPayload(id, saleGuild.id, this.saleQuantity()));
			}
		});
		this.updaters.add(() -> {
			all.active = !saleAll;
			ItemDemand.Buyer buyer = this.saleBuyer();
			double total = this.saleOffer(this.saleQuantity());
			sell.active = buyer != null && this.saleQuantity() > 0 && total > 0;
			sell.setMessage(saleGuild == null
					? Component.translatable("bolsavillager.sale.choose")
					: Component.translatable("bolsavillager.sale.sell_to", MarketUi.money(total)));
		});
	}

	private void initWallet() {
		int y = this.ct + 132;
		int bw = (this.rw - 2) / 2;
		Button deposit = this.button(Component.translatable("bolsavillager.button.deposit"), this.rx, y, bw, 18,
				b -> ClientPlayNetworking.send(new DepositPayload(0)));
		deposit.setTooltip(Tooltip.create(Component.translatable("bolsavillager.button.deposit.tooltip")));
		Button withdraw = this.button(Component.translatable("bolsavillager.button.withdraw"), this.rx + bw + 2, y, bw, 18,
				b -> ClientPlayNetworking.send(WithdrawPayload.INSTANCE));
		withdraw.setTooltip(Tooltip.create(Component.translatable("bolsavillager.button.withdraw.tooltip")));
		this.updaters.add(() -> {
			deposit.active = ClientMarket.emeraldsInInventory() > 0;
			withdraw.active = ClientMarket.account().balance() >= 1.0;
		});
	}

	private void trade(int quantity) {
		ClientPlayNetworking.send(new TradeSharesPayload(selected.id, quantity));
	}

	private void updateWidgets() {
		for (Runnable r : this.updaters) {
			r.run();
		}
	}

	@Override
	public void tick() {
		// Ordens novas/canceladas mudam os botões de cancelar: reconstrói a aba.
		if (tab == Tab.ORDERS && ClientMarket.account().orders().size() != this.seenOrders) {
			this.rebuildWidgets();
		}
		if (ClientMarket.version() != this.seenVersion) {
			this.seenVersion = ClientMarket.version();
		}
		this.updateWidgets();
	}

	private void click() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4F));
	}

	// ---------------------------------------------------------------------------------------------
	// Balcão de vendas: o que tem na mochila que alguma guilda compra
	// ---------------------------------------------------------------------------------------------

	private record SaleItem(Item item, ItemStack sample, int count) {
	}

	private List<SaleItem> saleItems() {
		Player player = this.minecraft.player;
		Map<Item, int[]> counts = new LinkedHashMap<>();
		Map<Item, ItemStack> samples = new LinkedHashMap<>();
		if (player != null) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (!ItemDemand.buyersOf(stack).isEmpty()) {
					counts.computeIfAbsent(stack.getItem(), k -> new int[1])[0] += stack.getCount();
					samples.putIfAbsent(stack.getItem(), stack.copyWithCount(1));
				}
			}
		}
		List<SaleItem> list = new ArrayList<>();
		counts.forEach((item, c) -> list.add(new SaleItem(item, samples.get(item), c[0])));
		return list;
	}

	private int ownedOf(@Nullable Item item) {
		for (SaleItem s : this.saleItems()) {
			if (s.item() == item) {
				return s.count();
			}
		}
		return 0;
	}

	private int saleQuantity() {
		int owned = this.ownedOf(saleItem);
		return saleAll ? owned : Math.min(SALE_QTY[saleQtyIndex], owned);
	}

	private ItemDemand.@Nullable Buyer saleBuyer() {
		return saleItem == null || saleGuild == null ? null : ItemDemand.buyer(new ItemStack(saleItem), saleGuild);
	}

	private double saleOffer(ItemDemand.Buyer buyer, int quantity) {
		MarketSyncPayload.StockView v = ClientMarket.get(buyer.guild());
		if (v == null) {
			return 0;
		}
		return ClientMarket.rules().saleTotal(buyer, v.price(), v.population(), ClientMarket.soldToday(buyer), quantity,
				ClientMarket.heroLevel());
	}

	private double saleOffer(int quantity) {
		ItemDemand.Buyer buyer = this.saleBuyer();
		return buyer == null ? 0 : this.saleOffer(buyer, quantity);
	}

	/** Escolhe a guilda que paga melhor pelo item. */
	private void pickBestGuild() {
		saleGuild = null;
		if (saleItem == null) {
			return;
		}
		double best = -1;
		for (ItemDemand.Buyer b : ItemDemand.buyersOf(new ItemStack(saleItem))) {
			double offer = this.saleOffer(b, 1);
			if (offer > best) {
				best = offer;
				saleGuild = b.guild();
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Mouse
	// ---------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int listX = this.x0 + 6;
		if ((tab == Tab.STOCKS || tab == Tab.ORDERS) && event.x() >= listX && event.x() < listX + LIST_W && event.y() >= this.ct) {
			int row = (int) Math.floor((event.y() - this.ct) / ROW_H);
			if (row >= 0 && row < StockDef.values().length) {
				selected = StockDef.values()[row];
				this.click();
				this.updateWidgets();
				return true;
			}
		}
		if (tab == Tab.SELL) {
			if (event.x() >= listX && event.x() < listX + LIST_W && event.y() >= this.ct) {
				int row = (int) Math.floor((event.y() - this.ct) / ITEM_ROW_H) + this.saleScroll;
				List<SaleItem> items = this.saleItems();
				if (row >= 0 && row < items.size() && row - this.saleScroll < ITEM_ROWS) {
					saleItem = items.get(row).item();
					this.pickBestGuild();
					this.click();
					this.updateWidgets();
					return true;
				}
			}
			// Clique numa guilda compradora
			if (saleItem != null && event.x() >= this.rx && event.x() < this.rx + this.rw) {
				List<ItemDemand.Buyer> buyers = ItemDemand.buyersOf(new ItemStack(saleItem));
				int row = (int) Math.floor((event.y() - (this.ct + 34)) / 12);
				if (event.y() >= this.ct + 34 && row >= 0 && row < Math.min(5, buyers.size())) {
					saleGuild = buyers.get(row).guild();
					this.click();
					this.updateWidgets();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (tab == Tab.SELL) {
			int max = Math.max(0, this.saleItems().size() - ITEM_ROWS);
			this.saleScroll = Math.max(0, Math.min(max, this.saleScroll - (int) Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	// ---------------------------------------------------------------------------------------------
	// Desenho
	// ---------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		MarketUi.panel(g, this.x0, this.y0, W, H);
		this.extractHeader(g);

		if (!ClientMarket.hasData()) {
			g.centeredText(this.font, Component.translatable("bolsavillager.screen.loading"),
					this.x0 + W / 2, this.y0 + H / 2, MarketUi.TEXT_DIM);
		} else {
			switch (tab) {
				case STOCKS -> {
					this.extractStockList(g, mouseX, mouseY);
					this.extractStockDetails(g);
				}
				case ORDERS -> {
					this.extractStockList(g, mouseX, mouseY);
					this.extractOrders(g);
				}
				case SELL -> this.extractSell(g, mouseX, mouseY);
				case WALLET -> this.extractWallet(g);
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void extractHeader(GuiGraphicsExtractor g) {
		g.fill(this.x0 + 1, this.y0 + 1, this.x0 + W - 1, this.y0 + 15, MarketUi.BG_SOFT);
		g.text(this.font, Component.translatable("bolsavillager.screen.title").withStyle(ChatFormatting.BOLD),
				this.x0 + 6, this.y0 + 4, MarketUi.ACCENT, false);
		double idxChange = ClientMarket.indexChangePercent();
		String idx = "IVIL " + String.format(Locale.ROOT, "%.0f", ClientMarket.index()) + " ";
		String idxDelta = MarketUi.arrow(idxChange) + " " + MarketUi.percent(idxChange);
		int right = this.x0 + W - 6;
		MarketUi.textRight(g, this.font, idxDelta, right, this.y0 + 4, MarketUi.changeColor(idxChange));
		MarketUi.textRight(g, this.font, idx, right - this.font.width(idxDelta), this.y0 + 4, MarketUi.TEXT);
		if (ClientMarket.raidOngoing()) {
			String raid = Component.translatable("bolsavillager.screen.raid").getString();
			int rxp = right - this.font.width(idxDelta) - this.font.width(idx) - 6 - this.font.width(raid);
			boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
			g.text(this.font, raid, rxp, this.y0 + 4, blink ? MarketUi.DOWN : 0xFFFFB0B0, false);
		}
	}

	private void extractStockList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = this.x0 + 6;
		int y = this.ct;
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
			g.text(this.font, def.ticker, x + 9, y + 2, owned > 0 ? 0xFFFFE27A : MarketUi.TEXT, false);
			MarketSyncPayload.StockView view = ClientMarket.get(def);
			if (view != null) {
				double change = ClientMarket.changePercent(view);
				MarketUi.textRight(g, this.font, MarketUi.money(view.price()), x + 76, y + 2, MarketUi.TEXT);
				MarketUi.textRight(g, this.font, MarketUi.percent(change), x + LIST_W - 2, y + 2, MarketUi.changeColor(change));
			}
			if (hover) {
				g.setTooltipForNextFrame(this.font, MarketUi.name(def), mouseX, mouseY);
			}
			y += ROW_H;
		}
	}

	private void extractStockDetails(GuiGraphicsExtractor g) {
		int x = this.rx;
		int w = this.rw;
		int y = this.ct;
		MarketSyncPayload.StockView view = ClientMarket.get(selected);
		if (view == null) {
			return;
		}
		MarketRules rules = ClientMarket.rules();
		double change = ClientMarket.changePercent(view);

		g.fill(x, y + 1, x + 3, y + 8, selected.color);
		g.text(this.font, MarketUi.name(selected), x + 6, y, MarketUi.TEXT, false);
		MarketUi.textRight(g, this.font, selected.ticker, x + w, y, MarketUi.ACCENT);

		y += 11;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(1.5F, 1.5F);
		g.text(this.font, MarketUi.money(view.price()) + MarketUi.EMERALD, 0, 0, MarketUi.TEXT, false);
		g.pose().popMatrix();
		MarketUi.textRight(g, this.font, MarketUi.arrow(change) + " " + MarketUi.percent(change) + " " +
				Component.translatable("bolsavillager.screen.today").getString(), x + w, y + 3, MarketUi.changeColor(change));

		y += 16;
		MarketUi.chart(g, view.history(), x, y, w, 44, true);
		g.text(this.font, Component.translatable("bolsavillager.screen.last_hour"), x + 2, y + 2, MarketUi.TEXT_DIM, false);

		y += 47;
		g.text(this.font, Component.translatable(selected == StockDef.WANDERING_TRADER
						? "bolsavillager.screen.stats_wandering" : "bolsavillager.screen.stats",
				view.population(), view.volumeDay(), view.tradesDay()), x, y, MarketUi.TEXT_DIM, false);

		y += 11;
		if (rules.dividendsEnabled()) {
			double dps = rules.dividendPerShare(selected, view.price(), view.population(), view.tradesDay());
			g.text(this.font, Component.translatable("bolsavillager.screen.dividend_forecast", MarketUi.money(dps)),
					x, y, MarketUi.GOLD, false);
		}

		y += 11;
		int owned = ClientMarket.account().shares().getOrDefault(selected.id, 0);
		if (owned > 0) {
			double basis = ClientMarket.account().costBasis().getOrDefault(selected.id, 0.0);
			double pl = view.price() * owned - basis;
			g.text(this.font, Component.translatable("bolsavillager.screen.position", owned, MarketUi.money(basis / owned)),
					x, y, MarketUi.TEXT, false);
			MarketUi.textRight(g, this.font, (pl >= 0 ? "+" : "") + MarketUi.money(pl) + MarketUi.EMERALD, x + w, y, MarketUi.changeColor(pl));
		} else {
			g.text(this.font, Component.translatable("bolsavillager.screen.no_position"), x, y, MarketUi.TEXT_DIM, false);
		}

		// Quantidade escolhida (entre os botões - e +)
		g.centeredText(this.font, Component.literal(String.valueOf(this.qty())), x + 37, this.ct + 112, MarketUi.TEXT);

		this.extractMoneyFooter(g, this.ct + 152);
	}

	private void extractMoneyFooter(GuiGraphicsExtractor g, int y) {
		int x = this.rx;
		g.horizontalLine(x, x + this.rw, y - 3, 0x40FFFFFF);
		g.text(this.font, Component.translatable("bolsavillager.screen.balance_line",
						MarketUi.money(ClientMarket.account().balance()), ClientMarket.emeraldsInInventory()),
				x, y + 1, MarketUi.TEXT, false);
		int hero = ClientMarket.heroLevel();
		if (hero >= 0) {
			MarketRules rules = ClientMarket.rules();
			g.text(this.font, Component.translatable("bolsavillager.screen.hero",
							String.format(Locale.ROOT, "%.0f", rules.heroFeeDiscount() * 100),
							String.format(Locale.ROOT, "%.0f", (rules.heroSaleMultiplier(hero) - 1) * 100)),
					x, y + 12, MarketUi.GOLD, false);
		}
	}

	private void extractOrders(GuiGraphicsExtractor g) {
		int x = this.rx;
		int w = this.rw;
		MarketSyncPayload.StockView view = ClientMarket.get(selected);
		if (view == null) {
			return;
		}
		g.text(this.font, Component.translatable("bolsavillager.order.new", selected.ticker), x, this.ct + 1, MarketUi.TEXT, false);
		MarketUi.textRight(g, this.font, MarketUi.money(view.price()) + MarketUi.EMERALD, x + w, this.ct + 1, MarketUi.TEXT_DIM);

		double target = Math.max(MarketEngine.MIN_PRICE, view.price() * (1 + orderOffset));
		g.centeredText(this.font, Component.literal(MarketUi.money(target) + MarketUi.EMERALD + " (" + MarketUi.percent0(orderOffset * 100) + ")"),
				x + w / 2, this.ct + 35, MarketUi.GOLD);
		g.centeredText(this.font, Component.literal(String.valueOf(this.qty())), x + 37, this.ct + 54, MarketUi.TEXT);

		g.horizontalLine(x, x + w, this.ct + 71, 0x40FFFFFF);
		List<LimitOrder> orders = ClientMarket.account().orders();
		g.text(this.font, Component.translatable("bolsavillager.order.list", orders.size(), LimitOrder.MAX_PER_PLAYER),
				x, this.ct + 75, MarketUi.TEXT_DIM, false);
		if (orders.isEmpty()) {
			g.text(this.font, Component.translatable("bolsavillager.order.none"), x, this.ct + 88, MarketUi.TEXT_DIM, false);
		}
		for (int i = 0; i < orders.size(); i++) {
			LimitOrder o = orders.get(i);
			int y = this.ct + 88 + i * 14;
			int color = o.type().isBuy() ? MarketUi.UP : o.type() == LimitOrder.Type.STOP_LOSS ? MarketUi.DOWN : MarketUi.GOLD;
			String symbol = o.type() == LimitOrder.Type.SELL_ABOVE ? "≥" : "≤";
			String verb = Component.translatable(o.type().isBuy() ? "bolsavillager.order.short.buy" : "bolsavillager.order.short.sell").getString();
			String line = verb + " " + o.quantity() + " " + (o.stock() == null ? "?" : o.stock().ticker) + " " + symbol + " "
					+ MarketUi.money(o.target()) + MarketUi.EMERALD;
			g.fill(x, y - 2, x + 3, y + 8, color);
			g.text(this.font, line, x + 6, y, MarketUi.TEXT, false);
		}
	}

	private void extractSell(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = this.x0 + 6;
		List<SaleItem> items = this.saleItems();
		g.fill(x - 1, this.ct - 1, x + LIST_W + 1, this.ct + ITEM_ROW_H * ITEM_ROWS + 1, 0x40000000);
		if (items.isEmpty()) {
			g.textWithWordWrap(this.font, Component.translatable("bolsavillager.sale.empty"), x + 3, this.ct + 4, LIST_W - 6, MarketUi.TEXT_DIM, false);
		}
		this.saleScroll = Math.max(0, Math.min(this.saleScroll, Math.max(0, items.size() - ITEM_ROWS)));
		for (int i = 0; i < ITEM_ROWS && i + this.saleScroll < items.size(); i++) {
			SaleItem s = items.get(i + this.saleScroll);
			int y = this.ct + i * ITEM_ROW_H;
			boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + ITEM_ROW_H;
			if (s.item() == saleItem) {
				g.fill(x, y, x + LIST_W, y + ITEM_ROW_H, 0x501FA35A);
			} else if (hover) {
				g.fill(x, y, x + LIST_W, y + ITEM_ROW_H, 0x20FFFFFF);
			}
			g.item(s.sample(), x + 1, y + 1);
			String count = "×" + s.count();
			String name = this.font.plainSubstrByWidth(s.sample().getHoverName().getString(), LIST_W - 24 - this.font.width(count));
			g.text(this.font, name, x + 20, y + 5, MarketUi.TEXT, false);
			MarketUi.textRight(g, this.font, count, x + LIST_W - 2, y + 5, MarketUi.TEXT_DIM);
		}
		if (items.size() > ITEM_ROWS) {
			g.text(this.font, Component.translatable("bolsavillager.sale.scroll"), x, this.ct + ITEM_ROW_H * ITEM_ROWS + 3, MarketUi.TEXT_DIM, false);
		}

		// Lado direito
		int rx = this.rx;
		int w = this.rw;
		if (saleItem == null || this.ownedOf(saleItem) == 0) {
			g.textWithWordWrap(this.font, Component.translatable("bolsavillager.sale.help"), rx, this.ct + 2, w, MarketUi.TEXT_DIM, false);
			if (saleItem != null && this.ownedOf(saleItem) == 0) {
				saleItem = null;
				saleGuild = null;
			}
			this.extractMoneyFooter(g, this.ct + 167);
			return;
		}
		ItemStack sample = new ItemStack(saleItem);
		g.item(sample, rx, this.ct);
		g.text(this.font, sample.getHoverName(), rx + 20, this.ct + 1, MarketUi.TEXT, false);
		g.text(this.font, Component.translatable("bolsavillager.sale.you_have", this.ownedOf(saleItem)), rx + 20, this.ct + 10, MarketUi.TEXT_DIM, false);

		g.text(this.font, Component.translatable("bolsavillager.sale.buyers"), rx, this.ct + 23, MarketUi.TEXT_DIM, false);
		List<ItemDemand.Buyer> buyers = ItemDemand.buyersOf(sample);
		for (int i = 0; i < Math.min(5, buyers.size()); i++) {
			ItemDemand.Buyer b = buyers.get(i);
			int y = this.ct + 34 + i * 12;
			boolean hover = mouseX >= rx && mouseX < rx + w && mouseY >= y && mouseY < y + 12;
			if (b.guild() == saleGuild) {
				g.fill(rx, y - 1, rx + w, y + 11, 0x501FA35A);
			} else if (hover) {
				g.fill(rx, y - 1, rx + w, y + 11, 0x20FFFFFF);
			}
			MarketSyncPayload.StockView v = ClientMarket.get(b.guild());
			int pop = v == null ? 0 : v.population();
			double unit = this.saleOffer(b, 1);
			g.fill(rx + 2, y + 3, rx + 6, y + 7, b.guild().color);
			String name = this.font.plainSubstrByWidth(MarketUi.name(b.guild()).getString(), w - 70);
			g.text(this.font, name, rx + 9, y + 1, pop > 0 ? MarketUi.TEXT : MarketUi.TEXT_DIM, false);
			String price = pop > 0 ? MarketUi.money(unit) + MarketUi.EMERALD + "/un"
					: Component.translatable("bolsavillager.sale.no_villagers_short").getString();
			MarketUi.textRight(g, this.font, price, rx + w - 2, y + 1, pop > 0 ? MarketUi.GOLD : MarketUi.DOWN);
		}

		// Quantidade e oferta
		int qty = this.saleQuantity();
		g.centeredText(this.font, Component.literal(String.valueOf(qty)), rx + 37, this.ct + 108, MarketUi.TEXT);
		ItemDemand.Buyer buyer = this.saleBuyer();
		if (buyer != null) {
			MarketSyncPayload.StockView v = ClientMarket.get(buyer.guild());
			int pop = v == null ? 0 : v.population();
			double interest = ClientMarket.rules().saleInterest(buyer, pop, ClientMarket.soldToday(buyer));
			int barX = rx + 116;
			int barW = w - 116;
			g.fill(barX, this.ct + 109, barX + barW, this.ct + 115, 0x60000000);
			int fillColor = interest > 0.66 ? MarketUi.UP : interest > 0.33 ? MarketUi.GOLD : MarketUi.DOWN;
			g.fill(barX, this.ct + 109, barX + (int) Math.round(barW * interest), this.ct + 115, fillColor);
			g.text(this.font, Component.translatable("bolsavillager.sale.interest"), barX, this.ct + 99, MarketUi.TEXT_DIM, false);

			double total = this.saleOffer(qty);
			g.text(this.font, Component.translatable("bolsavillager.sale.offer", MarketUi.name(buyer.guild()), MarketUi.money(total)),
					rx, this.ct + 121, MarketUi.TEXT, false);
			if (qty > 0) {
				g.text(this.font, Component.translatable("bolsavillager.sale.each", MarketUi.money(total / qty)),
						rx, this.ct + 132, MarketUi.TEXT_DIM, false);
			}
		}
		this.extractMoneyFooter(g, this.ct + 167);
	}

	private void extractWallet(GuiGraphicsExtractor g) {
		int x = this.x0 + 6;
		int y = this.ct;
		MarketSyncPayload.AccountView acc = ClientMarket.account();
		g.fill(x - 1, y - 1, x + LIST_W + 1, y + ROW_H * StockDef.values().length + 1, 0x40000000);
		double value = 0, invested = 0;
		int rows = 0;
		for (StockDef def : StockDef.values()) {
			int owned = acc.shares().getOrDefault(def.id, 0);
			MarketSyncPayload.StockView v = ClientMarket.get(def);
			if (owned <= 0 || v == null) {
				continue;
			}
			double basis = acc.costBasis().getOrDefault(def.id, 0.0);
			double val = v.price() * owned;
			value += val;
			invested += basis;
			int ry = y + rows * ROW_H;
			g.fill(x + 2, ry + 4, x + 6, ry + 8, def.color);
			g.text(this.font, def.ticker + " ×" + owned, x + 9, ry + 2, MarketUi.TEXT, false);
			double pl = val - basis;
			MarketUi.textRight(g, this.font, (pl >= 0 ? "+" : "") + MarketUi.money(pl), x + LIST_W - 2, ry + 2, MarketUi.changeColor(pl));
			rows++;
		}
		if (rows == 0) {
			g.textWithWordWrap(this.font, Component.translatable("bolsavillager.wallet.empty"), x + 3, y + 4, LIST_W - 6, MarketUi.TEXT_DIM, false);
		}

		int rx = this.rx;
		int ry = this.ct;
		g.text(this.font, Component.translatable("bolsavillager.wallet.title").withStyle(ChatFormatting.BOLD), rx, ry, MarketUi.ACCENT, false);
		ry += 14;
		ry = this.walletLine(g, rx, ry, "bolsavillager.wallet.balance", MarketUi.money(acc.balance()) + MarketUi.EMERALD, MarketUi.TEXT);
		ry = this.walletLine(g, rx, ry, "bolsavillager.wallet.shares", MarketUi.money(value) + MarketUi.EMERALD, MarketUi.TEXT);
		ry = this.walletLine(g, rx, ry, "bolsavillager.wallet.total", MarketUi.money(acc.balance() + value) + MarketUi.EMERALD, MarketUi.ACCENT);
		double pl = value - invested;
		ry = this.walletLine(g, rx, ry, "bolsavillager.wallet.pl", (pl >= 0 ? "+" : "") + MarketUi.money(pl) + MarketUi.EMERALD, MarketUi.changeColor(pl));
		ry += 6;
		ry = this.walletLine(g, rx, ry, "bolsavillager.wallet.last_dividend", "+" + MarketUi.money(acc.lastDividend()) + MarketUi.EMERALD, MarketUi.GOLD);
		ry = this.walletLine(g, rx, ry, "bolsavillager.wallet.total_dividends", "+" + MarketUi.money(acc.totalDividends()) + MarketUi.EMERALD, MarketUi.GOLD);
		ry = this.walletLine(g, rx, ry, "bolsavillager.wallet.orders", String.valueOf(acc.orders().size()), MarketUi.TEXT);
		ry += 6;
		g.text(this.font, Component.translatable("bolsavillager.wallet.inventory", ClientMarket.emeraldsInInventory()), rx, ry, MarketUi.TEXT_DIM, false);

		int hero = ClientMarket.heroLevel();
		if (hero >= 0) {
			MarketRules rules = ClientMarket.rules();
			g.text(this.font, Component.translatable("bolsavillager.screen.hero",
							String.format(Locale.ROOT, "%.0f", rules.heroFeeDiscount() * 100),
							String.format(Locale.ROOT, "%.0f", (rules.heroSaleMultiplier(hero) - 1) * 100)),
					rx, this.ct + 156, MarketUi.GOLD, false);
		}
		if (ClientMarket.raidOngoing()) {
			g.text(this.font, Component.translatable("bolsavillager.wallet.raid"), rx, this.ct + 167, MarketUi.DOWN, false);
		}
	}

	private int walletLine(GuiGraphicsExtractor g, int x, int y, String key, String value, int color) {
		g.text(this.font, Component.translatable(key), x, y, MarketUi.TEXT_DIM, false);
		MarketUi.textRight(g, this.font, value, x + this.rw, y, color);
		return y + 11;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
