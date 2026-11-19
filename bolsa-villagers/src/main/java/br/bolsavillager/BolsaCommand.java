package br.bolsavillager;

import br.bolsavillager.market.MarketData;
import br.bolsavillager.market.MarketEngine;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Só comandos de administração (op). O jogador usa a bolsa pela tecla (padrão: B), sem chat.
 *
 * /bolsa pregao [vezes]   roda pregões agora
 * /bolsa dividendos       paga os dividendos do dia agora
 * /bolsa recarregar       relê config/bolsavillager.json
 */
public final class BolsaCommand {
	private BolsaCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("bolsa")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("pregao")
						.executes(ctx -> forceSessions(ctx.getSource(), 1))
						.then(Commands.argument("vezes", IntegerArgumentType.integer(1, 200))
								.executes(ctx -> forceSessions(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "vezes")))))
				.then(Commands.literal("dividendos").executes(ctx -> forceDividends(ctx.getSource())))
				.then(Commands.literal("recarregar").executes(ctx -> reload(ctx.getSource()))));
	}

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
		int total = MarketEngine.payDividends(server, data, data.day);
		MarketEngine.syncEveryone(server);
		source.sendSuccess(() -> Component.translatable("bolsavillager.cmd.dividends_done", total), true);
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
}
