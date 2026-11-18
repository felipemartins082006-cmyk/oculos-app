package br.bolsavillager.test;

import br.bolsavillager.BolsaMod;
import br.bolsavillager.client.ClientMarket;
import br.bolsavillager.client.screen.TerminalScreen;
import br.bolsavillager.market.MarketData;
import br.bolsavillager.market.MarketEngine;
import br.bolsavillager.market.MarketRules;
import br.bolsavillager.BolsaConfig;
import net.minecraft.client.gui.screens.ChatScreen;
import java.nio.file.Files;
import br.bolsavillager.market.StockDef;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;
import java.util.UUID;

/**
 * Abre o jogo de verdade, cria um mundo, coloca villagers e o terminal, simula um pregão,
 * compra ações pelo terminal e pela tela de trocas, e confere se tudo ficou salvo ao reabrir o mundo.
 * Rode com: ./gradlew runClientGameTest (screenshots em build/run/clientGameTest/screenshots).
 */
public class BolsaClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		// Comprar 64 e vender 64 logo em seguida não pode dar lucro (antes da corretagem, empata).
		MarketRules rules = MarketRules.DEFAULT;
		double buy = rules.orderValue(10.0, 64);
		double sell = rules.orderValue(10.0 * Math.exp(rules.impactPerShare() * 64), -64);
		check(Math.abs(buy - sell) < 1e-6, "ida e volta deveria empatar antes da corretagem: " + buy + " vs " + sell);

		UUID[] playerId = new UUID[1];
		TestWorldSave save;
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			ctx.waitTicks(40);
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");

			sp.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				playerId[0] = player.getUUID();
				ServerLevel level = player.level();
				Direction dir = player.getDirection();
				BlockPos front = player.blockPosition().relative(dir, 3);
				level.setBlockAndUpdate(front, BolsaMod.TERMINAL.defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, dir.getOpposite()));

				// Uma pequena vila: 3 fazendeiros, 2 bibliotecários, 1 armeiro.
				spawn(level, front.relative(dir.getClockWise(), 2), VillagerProfession.FARMER);
				spawn(level, front.relative(dir.getClockWise(), 3), VillagerProfession.FARMER);
				spawn(level, front.relative(dir.getClockWise(), 4), VillagerProfession.FARMER);
				spawn(level, front.relative(dir.getCounterClockWise(), 2), VillagerProfession.LIBRARIAN);
				spawn(level, front.relative(dir.getCounterClockWise(), 3), VillagerProfession.LIBRARIAN);
				spawn(level, front.relative(dir.getCounterClockWise(), 4), VillagerProfession.ARMORER);

				player.getInventory().add(new ItemStack(Items.EMERALD, 64));
				player.getInventory().add(new ItemStack(Items.EMERALD, 64));
			});
			ctx.waitTicks(10);

			// Simula 40 pregões com trocas reais passando pelo mixin de notifyTrade.
			sp.getServer().runOnServer(server -> {
				List<Villager> farmers = villagers(server, VillagerProfession.FARMER);
				for (int session = 0; session < 40; session++) {
					if (session % 3 == 0) {
						for (Villager farmer : farmers) {
							MerchantOffer offer = farmer.getOffers().getFirst();
							farmer.notifyTrade(offer);
						}
					}
					if (session == 25) {
						villagers(server, VillagerProfession.ARMORER).forEach(Villager::restock);
					}
					MarketEngine.runSession(server);
				}
				MarketData data = MarketEngine.data(server);
				check(data.stock(StockDef.FARMER).population == 3, "censo deveria ter 3 fazendeiros, tem " + data.stock(StockDef.FARMER).population);
				check(data.stock(StockDef.LIBRARIAN).population == 2, "censo deveria ter 2 bibliotecários");
				check(data.stock(StockDef.FARMER).history.size() >= 40, "histórico deveria ter 40+ pontos");
				// Os pregões zeram o volume ao virar o dia; o volume do dia só é testado se não virou.
				BolsaMod.LOGGER.info("[teste] FAZD3 = {} (vol {} / trocas {})", data.stock(StockDef.FARMER).price,
						data.stock(StockDef.FARMER).volumeDay, data.stock(StockDef.FARMER).tradesDay);
			});

			// Verifica que a troca real alimenta o volume do dia.
			int tradesBefore = sp.getServer().computeOnServer(s -> MarketEngine.data(s).stock(StockDef.FARMER).tradesDay);
			sp.getServer().runOnServer(server -> {
				Villager farmer = villagers(server, VillagerProfession.FARMER).getFirst();
				farmer.notifyTrade(farmer.getOffers().getFirst());
			});
			int tradesAfter = sp.getServer().computeOnServer(s -> MarketEngine.data(s).stock(StockDef.FARMER).tradesDay);
			check(tradesAfter == tradesBefore + 1, "mixin de troca não registrou a troca (" + tradesBefore + " -> " + tradesAfter + ")");

			// ---- O terminal no mundo ----
			clearToasts(ctx);
			ctx.waitTicks(5);
			ctx.takeScreenshot("bolsa_mundo");

			// ---- Terminal da Bolsa ----
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				BlockPos front = player.blockPosition().relative(player.getDirection(), 3);
				MarketEngine.onTerminalUsed(player, front);
			});
			ctx.waitForScreen(TerminalScreen.class);
			clearToasts(ctx);
			ctx.waitTicks(5);
			ctx.takeScreenshot("bolsa_terminal");

			ctx.clickScreenButton("Buy 10");
			ctx.waitTicks(10);
			check(shares(sp, playerId[0]) == 10, "deveria ter 10 ações FAZD3 após comprar no terminal");
			ctx.takeScreenshot("bolsa_terminal_comprou");
			ctx.setScreen(() -> null);
			ctx.waitTicks(5);

			// ---- Painel na tela de trocas, GUI escala 2 em 854x480: faixa em cima da tela ----
			openMerchant(ctx, sp);
			ctx.takeScreenshot("bolsa_villager_faixa");
			ctx.clickScreenButton("+1");
			ctx.waitTicks(10);
			check(shares(sp, playerId[0]) == 11, "deveria ter 11 ações após comprar pela faixa da tela do villager");
			closeMerchant(ctx);
			ctx.waitTicks(5);

			// ---- GUI escala 1: sobra espaço, painel lateral completo ----
			ctx.runOnClient(mc -> {
				mc.options.guiScale().set(1);
				mc.resizeGui();
			});
			openMerchant(ctx, sp);
			ctx.takeScreenshot("bolsa_villager_painel");
			ctx.clickScreenButton("bolsavillager.button.sell_short");
			ctx.waitTicks(10);
			check(shares(sp, playerId[0]) == 10, "deveria ter 10 ações após vender pelo painel lateral");
			closeMerchant(ctx);
			ctx.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			ctx.waitTicks(5);

			// ---- Comando /bolsa ----
			check(Files.exists(BolsaConfig.path()), "config/bolsavillager.json deveria ter sido criado");
			clearChat(ctx);
			playerCommand(sp, "bolsa");
			ctx.waitTicks(5);
			ctx.setScreen(() -> new ChatScreen("", false));
			ctx.waitTicks(5);
			ctx.takeScreenshot("bolsa_chat_visao_geral");
			ctx.setScreen(() -> null);

			clearChat(ctx);
			playerCommand(sp, "bolsa fazd3");
			ctx.waitTicks(5);
			ctx.setScreen(() -> new ChatScreen("", false));
			ctx.waitTicks(5);
			ctx.takeScreenshot("bolsa_chat_detalhes");
			ctx.setScreen(() -> null);

			// Negociar pelo chat (o jogador está a 3 blocos do terminal).
			playerCommand(sp, "bolsa comprar FAZD3 2");
			ctx.waitTicks(3);
			check(shares(sp, playerId[0]) == 12, "deveria ter 12 ações após /bolsa comprar FAZD3 2");
			playerCommand(sp, "bolsa vender fazd3 2");
			ctx.waitTicks(3);
			check(shares(sp, playerId[0]) == 10, "deveria voltar a 10 ações após /bolsa vender fazd3 2");

			// ---- Dividendos ----
			double before = sp.getServer().computeOnServer(s -> MarketEngine.data(s).account(playerId[0]).balance);
			double expected = sp.getServer().computeOnServer(s -> {
				var st = MarketEngine.data(s).stock(StockDef.FARMER);
				return 10 * MarketRules.current().dividendPerShare(StockDef.FARMER, st.price, st.population, st.tradesDay);
			});
			check(expected > 0, "fazendeiros com villagers e trocas deveriam pagar dividendo");
			clearChat(ctx);
			sp.getServer().runOnServer(s -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "bolsa admin dividendos"));
			ctx.waitTicks(5);
			double after = sp.getServer().computeOnServer(s -> MarketEngine.data(s).account(playerId[0]).balance);
			check(Math.abs(after - before - expected) < 1e-6, "dividendo pago errado: " + (after - before) + " vs " + expected);
			BolsaMod.LOGGER.info("[teste] Dividendo pago: {}", after - before);

			clearChat(ctx);
			playerCommand(sp, "bolsa carteira");
			ctx.waitTicks(5);
			ctx.setScreen(() -> new ChatScreen("", false));
			ctx.waitTicks(5);
			ctx.takeScreenshot("bolsa_chat_carteira");
			ctx.setScreen(() -> null);

			// Recarregar config pelo comando.
			sp.getServer().runOnServer(s -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "bolsa admin recarregar"));

			// Terminal com o layout novo (dividendos).
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				MarketEngine.onTerminalUsed(player, player.blockPosition().relative(player.getDirection(), 3));
			});
			ctx.waitForScreen(TerminalScreen.class);
			clearToasts(ctx);
			ctx.waitTicks(5);
			ctx.takeScreenshot("bolsa_terminal_dividendos");
			ctx.setScreen(() -> null);
			ctx.waitTicks(3);

			// Sacar pelo comando: o saldo vira esmeraldas.
			playerCommand(sp, "bolsa sacar");
			ctx.waitTicks(5);
			double balance = sp.getServer().computeOnServer(s -> MarketEngine.data(s).account(playerId[0]).balance);
			check(balance < 1.0, "saldo deveria ter sido sacado, sobrou " + balance);

			save = sp.getWorldSave();
		}

		// ---- Persistência: o mundo foi fechado; reabre e confere ----
		try (TestSingleplayerContext again = save.open()) {
			ctx.waitTicks(20);
			check(shares(again, playerId[0]) == 10, "ações deveriam continuar salvas após reabrir o mundo");
			int farmers = again.getServer().computeOnServer(s -> MarketEngine.data(s).stock(StockDef.FARMER).population);
			check(farmers == 3, "censo deveria continuar com 3 fazendeiros após reabrir");
			BolsaMod.LOGGER.info("[teste] Tudo certo: terminal, painel do villager, saque e persistência OK");
		}
	}

	private static void openMerchant(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		ctx.runOnClient(mc -> ClientMarket.setLastMerchantStock(StockDef.FARMER));
		sp.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Villager farmer = villagers(server, VillagerProfession.FARMER).getFirst();
			MarketEngine.onMerchantInteract(player, farmer);
			farmer.mobInteract(player, InteractionHand.MAIN_HAND);
		});
		ctx.waitForScreen(MerchantScreen.class);
		clearToasts(ctx);
		ctx.waitTicks(5);
	}

	/** Fecha a tela de trocas avisando o servidor (como apertar ESC). */
	private static void closeMerchant(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.player.closeContainer());
		ctx.waitTicks(5);
	}

	private static void playerCommand(TestSingleplayerContext sp, String command) {
		sp.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
		});
	}

	private static void clearChat(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
	}

	private static void clearToasts(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
	}

	private static int shares(TestSingleplayerContext sp, UUID player) {
		return sp.getServer().computeOnServer(s -> MarketEngine.data(s).account(player).sharesOf(StockDef.FARMER));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void spawn(ServerLevel level, BlockPos pos, ResourceKey<VillagerProfession> profession) {
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		if (villager == null) {
			throw new AssertionError("não criou villager");
		}
		villager.setVillagerData(villager.getVillagerData()
				.withProfession(level.registryAccess(), profession)
				.withLevel(2));
		villager.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180F, 0F);
		villager.setNoAi(true);
		level.addFreshEntity(villager);
	}

	private static List<Villager> villagers(MinecraftServer server, ResourceKey<VillagerProfession> profession) {
		return server.overworld().getEntities(EntityTypeTest.forClass(Villager.class),
				v -> v.getVillagerData().profession().is(profession)).stream().map(v -> (Villager) v).toList();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
