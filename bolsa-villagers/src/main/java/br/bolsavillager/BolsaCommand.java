package br.bolsavillager;

import br.bolsavillager.market.Account;
import br.bolsavillager.market.MarketData;
import br.bolsavillager.market.MarketEngine;
import br.bolsavillager.market.MarketRules;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.market.StockState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /bolsa                       visão geral do mercado (clique num ticker para detalhes)
 * /bolsa <ticker>              detalhes de uma ação
 * /bolsa carteira              sua carteira, saldo e dividendos
 * /bolsa comprar <ticker> [n]  compra ações (perto de um Terminal da Bolsa)
 * /bolsa vender <ticker> [n]   vende ações (perto de um Terminal da Bolsa)
 * /bolsa vender <ticker> tudo  vende todas as ações da empresa
 * /bolsa sacar                 transforma o saldo da corretora em esmeraldas
 * /bolsa admin pregao [n]      (op) roda n pregões agora
 * /bolsa admin dividendos      (op) paga os dividendos do dia agora
 * /bolsa admin recarregar      (op) relê config/bolsavillager.json
 */
public final class BolsaCommand {
	private static final int UP = 0x3DDC84;
	private static final int DOWN = 0xFF5C5C;
	private static final int FLAT = 0xB0B8C0;
	private static final int ACCENT = 0x4BE08A;
	private static final int DIM = 0xA0A8B0;
	private static final String SPARK = "▁▂▃▄▅▆▇█";

	private static final DynamicCommandExceptionType UNKNOWN_TICKER = new DynamicCommandExceptionType(
			t -> Component.translatable("bolsavillager.cmd.unknown_ticker", t));
	private static final SimpleCommandExceptionType NOT_NEAR_TERMINAL = new SimpleCommandExceptionType(
			Component.translatable("bolsavillager.cmd.need_terminal"));
	private static final SimpleCommandExceptionType NOTHING_TO_WITHDRAW = new SimpleCommandExceptionType(
			Component.translatable("bolsavillager.cmd.nothing_to_withdraw"));
	private static final SimpleCommandExceptionType ORDER_FAILED = new SimpleCommandExceptionType(
			Component.translatable("bolsavillager.cmd.order_failed"));

	private static final SuggestionProvider<CommandSourceStack> TICKERS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(StockDef.values()).map(d -> d.ticker), builder);

	private BolsaCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("bolsa")
				.executes(ctx -> overview(ctx.getSource()))
				.then(Commands.literal("carteira").executes(ctx -> portfolio(ctx.getSource())))
				.then(Commands.literal("sacar").executes(ctx -> withdraw(ctx.getSource())))
				.then(Commands.literal("comprar")
						.then(Commands.argument("ticker", StringArgumentType.word()).suggests(TICKERS)
								.executes(ctx -> order(ctx, 1))
								.then(Commands.argument("quantidade", IntegerArgumentType.integer(1, MarketEngine.MAX_ORDER))
										.executes(ctx -> order(ctx, IntegerArgumentType.getInteger(ctx, "quantidade"))))))
				.then(Commands.literal("vender")
						.then(Commands.argument("ticker", StringArgumentType.word()).suggests(TICKERS)
								.executes(ctx -> order(ctx, -1))
								.then(Commands.literal("tudo").executes(BolsaCommand::sellAll))
								.then(Commands.argument("quantidade", IntegerArgumentType.integer(1, MarketEngine.MAX_ORDER))
										.executes(ctx -> order(ctx, -IntegerArgumentType.getInteger(ctx, "quantidade"))))))
				.then(Commands.literal("admin")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("pregao")
								.executes(ctx -> forceSessions(ctx.getSource(), 1))
								.then(Commands.argument("vezes", IntegerArgumentType.integer(1, 200))
										.executes(ctx -> forceSessions(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "vezes")))))
						.then(Commands.literal("dividendos").executes(ctx -> forceDividends(ctx.getSource())))
						.then(Commands.literal("recarregar").executes(ctx -> reload(ctx.getSource()))))
				.then(Commands.argument("ticker", StringArgumentType.word()).suggests(TICKERS)
						.executes(ctx -> details(ctx.getSource(), ticker(ctx)))));
	}

	// ---------------------------------------------------------------------------------------------
	// Consultas
	// ---------------------------------------------------------------------------------------------

	private static int overview(CommandSourceStack source) {
		MarketData data = MarketEngine.data(source.getServer());
		double now = 0, open = 0, base = 0;
		for (StockDef def : StockDef.values()) {
			StockState s = data.stock(def);
			now += s.price;
			open += s.dayOpen;
			base += def.basePrice;
		}
		double idxChange = pct(now, open);

		List<MutableComponent> lines = new java.util.ArrayList<>();
		lines.add(header(Component.translatable("bolsavillager.terminal.title"))
				.append(Component.literal("  IVIL " + String.format(Locale.ROOT, "%.0f", now / base * 1000) + " ").withColor(0xFFFFFF))
				.append(change(idxChange)));

		for (StockDef def : StockDef.values()) {
			lines.add(quote(data, def).append(Component.literal("  ").append(Component.translatable(def.translationKey())).withColor(DIM)));
		}

		ServerPlayer player = source.getPlayer();
		if (player != null) {
			Account account = data.account(player.getUUID());
			lines.add(Component.translatable("bolsavillager.cmd.summary",
							money(account.balance), money(portfolioValue(data, account)))
					.withColor(DIM)
					.append(Component.literal("  "))
					.append(button(Component.translatable("bolsavillager.cmd.see_portfolio"), "/bolsa carteira", true)));
		}
		lines.add(Component.translatable("bolsavillager.cmd.overview_hint").withColor(DIM).withStyle(ChatFormatting.ITALIC));
		send(source, lines);
		return 1;
	}

	private static int details(CommandSourceStack source, StockDef def) {
		MarketData data = MarketEngine.data(source.getServer());
		MarketRules rules = MarketRules.current();
		StockState s = data.stock(def);
		List<MutableComponent> lines = new java.util.ArrayList<>();

		lines.add(header(Component.literal(def.ticker + " · ").append(Component.translatable(def.translationKey()))));
		lines.add(Component.translatable("bolsavillager.cmd.price", money(s.price)).withColor(0xFFFFFF)
				.append(Component.literal("  ")).append(change(pct(s.price, s.dayOpen)))
				.append(Component.translatable("bolsavillager.cmd.open", money(s.dayOpen)).withColor(DIM)));
		lines.add(Component.literal(sparkline(s.history, 32) + " ").withColor(s.price >= s.history.getFirst() ? UP : DOWN)
				.append(Component.translatable("bolsavillager.terminal.last_hour").withColor(DIM)));
		lines.add(Component.translatable(def == StockDef.WANDERING_TRADER
						? "bolsavillager.terminal.stats_wandering" : "bolsavillager.terminal.stats",
				s.population, s.volumeDay, s.tradesDay).withColor(DIM));

		double effect = rules.tradePriceFactor(def, s.price, s.population) * 100;
		lines.add(Component.translatable("bolsavillager.cmd.fair_value", money(MarketRules.fairValue(def, s.population)))
				.withColor(DIM)
				.append(Component.translatable("bolsavillager.cmd.trade_effect",
						(effect >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.0f%%", effect))
						.withColor(effect > 0.5 ? DOWN : effect < -0.5 ? UP : FLAT)));
		if (rules.dividendsEnabled()) {
			lines.add(Component.translatable("bolsavillager.cmd.dividend_forecast",
					money(rules.dividendPerShare(def, s.price, s.population, s.tradesDay))).withColor(0xFFD166));
		}

		ServerPlayer player = source.getPlayer();
		if (player != null) {
			Account account = data.account(player.getUUID());
			int owned = account.sharesOf(def);
			if (owned > 0) {
				double basis = account.costBasis.getOrDefault(def.id, 0.0);
				double pl = s.price * owned - basis;
				lines.add(Component.translatable("bolsavillager.terminal.position", owned, money(basis / owned))
						.withColor(0xFFE27A)
						.append(Component.literal("  " + (pl >= 0 ? "+" : "") + money(pl) + "◆").withColor(pl >= 0 ? UP : DOWN)));
			} else {
				lines.add(Component.translatable("bolsavillager.terminal.no_position").withColor(DIM));
			}
			lines.add(button(Component.translatable("bolsavillager.cmd.buy_button"), "/bolsa comprar " + def.ticker + " 1", false)
					.append(Component.literal(" "))
					.append(button(Component.translatable("bolsavillager.cmd.sell_button"), "/bolsa vender " + def.ticker + " 1", false))
					.append(Component.literal(" "))
					.append(button(Component.translatable("bolsavillager.cmd.back_button"), "/bolsa", true)));
		}
		send(source, lines);
		return 1;
	}

	private static int portfolio(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MarketData data = MarketEngine.data(source.getServer());
		Account account = data.account(player.getUUID());
		List<MutableComponent> lines = new java.util.ArrayList<>();
		lines.add(header(Component.translatable("bolsavillager.cmd.portfolio_title")));

		double invested = 0, value = 0;
		for (StockDef def : StockDef.values()) {
			int owned = account.sharesOf(def);
			if (owned <= 0) {
				continue;
			}
			StockState s = data.stock(def);
			double basis = account.costBasis.getOrDefault(def.id, 0.0);
			double v = s.price * owned;
			invested += basis;
			value += v;
			double pl = v - basis;
			lines.add(Component.literal(def.ticker).withStyle(style -> style.withColor(ACCENT)
							.withClickEvent(new ClickEvent.RunCommand("/bolsa " + def.ticker))
							.withHoverEvent(new HoverEvent.ShowText(Component.translatable(def.translationKey()))))
					.append(Component.translatable("bolsavillager.cmd.holding", owned, money(v)).withColor(0xFFFFFF))
					.append(Component.literal(" " + (pl >= 0 ? "+" : "") + money(pl) + "◆ ("
							+ String.format(Locale.ROOT, "%+.1f%%", basis > 0 ? pl / basis * 100 : 0) + ")").withColor(pl >= 0 ? UP : DOWN)));
		}
		if (value == 0) {
			lines.add(Component.translatable("bolsavillager.cmd.no_holdings").withColor(DIM));
		}
		lines.add(Component.translatable("bolsavillager.cmd.totals", money(account.balance), money(value),
				money(account.balance + value)).withColor(0xFFFFFF));
		if (value > 0) {
			double pl = value - invested;
			lines.add(Component.translatable("bolsavillager.cmd.total_pl", (pl >= 0 ? "+" : "") + money(pl)).withColor(pl >= 0 ? UP : DOWN));
		}
		lines.add(Component.translatable("bolsavillager.cmd.dividends_summary",
				money(account.lastDividend), money(account.totalDividends)).withColor(0xFFD166));
		if (account.balance >= 1) {
			lines.add(button(Component.translatable("bolsavillager.button.withdraw"), "/bolsa sacar", true));
		}
		send(source, lines);
		return 1;
	}

	// ---------------------------------------------------------------------------------------------
	// Ações do jogador
	// ---------------------------------------------------------------------------------------------

	private static int order(CommandContext<CommandSourceStack> ctx, int quantity) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		StockDef def = ticker(ctx);
		if (!MarketEngine.canTradeFromChat(player)) {
			throw NOT_NEAR_TERMINAL.create();
		}
		if (!MarketEngine.executeTrade(player, def, quantity, false)) {
			throw ORDER_FAILED.create();
		}
		return 1;
	}

	private static int sellAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		StockDef def = ticker(ctx);
		int owned = MarketEngine.data(ctx.getSource().getServer()).account(player.getUUID()).sharesOf(def);
		if (owned <= 0) {
			player.sendSystemMessage(Component.translatable("bolsavillager.msg.no_shares", def.ticker).withStyle(ChatFormatting.RED));
			return 0;
		}
		if (!MarketEngine.canTradeFromChat(player)) {
			throw NOT_NEAR_TERMINAL.create();
		}
		// Ordens grandes são quebradas em lotes do tamanho máximo.
		int left = owned;
		while (left > 0) {
			int lot = Math.min(left, MarketEngine.MAX_ORDER);
			if (!MarketEngine.executeTrade(player, def, -lot, false)) {
				break;
			}
			left -= lot;
		}
		return owned - left;
	}

	private static int withdraw(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		int amount = MarketEngine.withdraw(player);
		if (amount <= 0) {
			throw NOTHING_TO_WITHDRAW.create();
		}
		source.sendSuccess(() -> Component.translatable("bolsavillager.cmd.withdrew", amount).withColor(UP), false);
		return amount;
	}

	// ---------------------------------------------------------------------------------------------
	// Admin
	// ---------------------------------------------------------------------------------------------

	private static int forceSessions(CommandSourceStack source, int times) {
		for (int i = 0; i < times; i++) {
			MarketEngine.runSession(source.getServer());
		}
		source.sendSuccess(() -> Component.translatable("bolsavillager.cmd.sessions_done", times), true);
		return times;
	}

	private static int forceDividends(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		MarketData data = MarketEngine.data(server);
		double total = MarketEngine.payDividends(server, data, data.day);
		MarketEngine.syncEveryone(server);
		source.sendSuccess(() -> Component.translatable("bolsavillager.cmd.dividends_done", money(total)), true);
		return 1;
	}

	private static int reload(CommandSourceStack source) {
		String error = BolsaConfig.load();
		if (error != null) {
			source.sendFailure(Component.translatable("bolsavillager.cmd.reload_failed", error));
			return 0;
		}
		MarketEngine.syncEveryone(source.getServer());
		source.sendSuccess(() -> Component.translatable("bolsavillager.cmd.reloaded", BolsaConfig.path().toString()), true);
		return 1;
	}

	// ---------------------------------------------------------------------------------------------
	// Formatação
	// ---------------------------------------------------------------------------------------------

	private static StockDef ticker(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String text = StringArgumentType.getString(ctx, "ticker");
		for (StockDef def : StockDef.values()) {
			if (def.ticker.equalsIgnoreCase(text) || def.id.equalsIgnoreCase(text)) {
				return def;
			}
		}
		throw UNKNOWN_TICKER.create(text);
	}

	private static MutableComponent quote(MarketData data, StockDef def) {
		StockState s = data.stock(def);
		return Component.literal(def.ticker).withStyle(style -> style.withColor(ACCENT)
						.withClickEvent(new ClickEvent.RunCommand("/bolsa " + def.ticker))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable(def.translationKey())
								.append("\n").append(Component.translatable("bolsavillager.cmd.click_details")))))
				.append(Component.literal(" " + money(s.price) + "◆ ").withColor(0xFFFFFF))
				.append(change(pct(s.price, s.dayOpen)));
	}

	private static MutableComponent header(Component title) {
		return Component.literal("━━ ").withColor(0x1FA35A)
				.append(title.copy().withColor(ACCENT).withStyle(ChatFormatting.BOLD))
				.append(Component.literal(" ━━").withColor(0x1FA35A));
	}

	private static MutableComponent change(double percent) {
		String arrow = percent > 0.005 ? "▲" : percent < -0.005 ? "▼" : "■";
		return Component.literal(arrow + String.format(Locale.ROOT, "%+.2f%%", percent))
				.withColor(percent > 0.005 ? UP : percent < -0.005 ? DOWN : FLAT);
	}

	/** Botão clicável no chat. run = executa direto; senão só preenche o comando para o jogador ajustar. */
	private static MutableComponent button(Component label, String command, boolean run) {
		return Component.literal("[").append(label).append("]").withStyle(style -> style
				.withColor(0x4FA3D9)
				.withClickEvent(run ? new ClickEvent.RunCommand(command) : new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	private static String sparkline(List<Double> history, int points) {
		int n = Math.min(points, history.size());
		List<Double> tail = history.subList(history.size() - n, history.size());
		double min = tail.stream().mapToDouble(Double::doubleValue).min().orElse(0);
		double max = tail.stream().mapToDouble(Double::doubleValue).max().orElse(1);
		StringBuilder sb = new StringBuilder();
		for (double v : tail) {
			int level = max - min < 1e-9 ? 3 : (int) Math.round((v - min) / (max - min) * (SPARK.length() - 1));
			sb.append(SPARK.charAt(level));
		}
		return sb.toString();
	}

	private static double portfolioValue(MarketData data, Account account) {
		double total = 0;
		for (StockDef def : StockDef.values()) {
			total += account.sharesOf(def) * data.stock(def).price;
		}
		return total;
	}

	private static double pct(double now, double open) {
		return open > 0 ? (now / open - 1) * 100 : 0;
	}

	private static String money(double v) {
		return MarketEngine.fmt(v);
	}

	private static void send(CommandSourceStack source, List<MutableComponent> lines) {
		MutableComponent all = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				all.append("\n");
			}
			all.append(lines.get(i));
		}
		source.sendSuccess(() -> all, false);
	}
}
