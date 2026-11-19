package br.bolsavillager.test;

import br.bolsavillager.BolsaConfig;
import br.bolsavillager.BolsaMod;
import br.bolsavillager.client.BolsaClient;
import br.bolsavillager.client.ClientMarket;
import br.bolsavillager.client.screen.BolsaScreen;
import br.bolsavillager.market.ItemDemand;
import br.bolsavillager.market.MarketData;
import br.bolsavillager.market.MarketEngine;
import br.bolsavillager.market.MarketRules;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.market.StockState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * Abre o jogo de verdade (em português), cria um mundo com villagers e testa: tecla B, abas da bolsa,
 * compra/venda, ordens programadas, balcão de itens, carteira (depósito/saque), Herói da Vila,
 * painel na tela de trocas, raid e persistência.
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

		// Jogo em português.
		ctx.runOnClient(mc -> {
			mc.options.languageCode = "pt_br";
			mc.getLanguageManager().setSelected("pt_br");
			mc.reloadResourcePacks();
		});
		ctx.waitTicks(40);

		UUID[] playerId = new UUID[1];
		int[] censusBefore = new int[1];
		TestWorldSave save;
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			ctx.waitTicks(40);
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("difficulty normal");
			check(Files.exists(BolsaConfig.path()), "config/bolsavillager.json deveria ter sido criado");

			sp.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				playerId[0] = player.getUUID();
				ServerLevel level = player.level();
				BlockPos front = player.blockPosition().relative(player.getDirection(), 3);
				spawn(level, front.relative(player.getDirection().getClockWise(), 2), VillagerProfession.FARMER);
				spawn(level, front.relative(player.getDirection().getClockWise(), 3), VillagerProfession.FARMER);
				spawn(level, front.relative(player.getDirection().getClockWise(), 4), VillagerProfession.FARMER);
				spawn(level, front.relative(player.getDirection().getCounterClockWise(), 2), VillagerProfession.LIBRARIAN);
				spawn(level, front.relative(player.getDirection().getCounterClockWise(), 3), VillagerProfession.LIBRARIAN);
				spawn(level, front.relative(player.getDirection().getCounterClockWise(), 4), VillagerProfession.ARMORER);
				player.getInventory().add(new ItemStack(Items.EMERALD, 64));
				player.getInventory().add(new ItemStack(Items.EMERALD, 64));
				player.getInventory().add(new ItemStack(Items.WHEAT, 64));
				player.getInventory().add(new ItemStack(Items.PAPER, 48));
				player.getInventory().add(new ItemStack(Items.COAL, 30));
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
					MarketEngine.runSession(server);
				}
				MarketData data = MarketEngine.data(server);
				check(data.stock(StockDef.FARMER).population == 3, "censo deveria ter 3 fazendeiros");
				check(data.stock(StockDef.LIBRARIAN).population == 2, "censo deveria ter 2 bibliotecários");
			});

			// ---- Tecla B abre a bolsa em qualquer lugar ----
			clearToasts(ctx);
			ctx.getInput().pressKey(BolsaClient.OPEN_KEY);
			ctx.waitForScreen(BolsaScreen.class);
			ctx.waitTicks(5);
			ctx.takeScreenshot("bolsa_acoes");

			// Quantidade: "+" três vezes = 10 ações.
			ctx.clickScreenButton("+");
			ctx.clickScreenButton("+");
			ctx.clickScreenButton("+");
			ctx.clickScreenButton("Comprar 10");
			ctx.waitTicks(10);
			check(shares(sp, playerId[0]) == 10, "deveria ter 10 ações FAZD3 após comprar na tela");
			ctx.takeScreenshot("bolsa_acoes_comprou");

			// ---- Ordens programadas: vender se subir até +5% ----
			ctx.clickScreenButton("Ordens");
			ctx.waitTicks(2);
			ctx.clickScreenButton("Tipo: Comprar se cair até");
			ctx.clickScreenButton("Criar ordem");
			ctx.waitTicks(10);
			int orders = sp.getServer().computeOnServer(s -> MarketEngine.data(s).account(playerId[0]).orders.size());
			check(orders == 1, "deveria ter 1 ordem programada, tem " + orders);
			ctx.takeScreenshot("bolsa_ordens");

			// O preço sobe 20% e o pregão executa a ordem sozinho.
			sp.getServer().runOnServer(s -> {
				StockState st = MarketEngine.data(s).stock(StockDef.FARMER);
				st.price *= 1.2;
				BolsaConfig.get().volatility = 0;
				MarketEngine.runSession(s);
			});
			ctx.waitTicks(10);
			check(shares(sp, playerId[0]) == 0, "a ordem de venda deveria ter vendido as 10 ações");
			check(sp.getServer().computeOnServer(s -> MarketEngine.data(s).account(playerId[0]).orders.isEmpty()),
					"a ordem executada deveria sair da lista");
			ctx.takeScreenshot("bolsa_ordem_executada");

			// ---- Vender itens no balcão: clica no trigo (primeiro item da lista) ----
			ctx.clickScreenButton("Vender itens");
			ctx.waitTicks(2);
			clickInScreen(ctx, (screenW, screenH) -> new double[]{
					(screenW - BolsaScreen.W) / 2.0 + 6 + 40, (screenH - BolsaScreen.H) / 2.0 + 38 + BolsaScreen.ITEM_ROW_H / 2.0});
			ctx.waitTicks(3);
			ctx.takeScreenshot("bolsa_vender_itens");
			double before = balance(sp, playerId[0]);
			double expected = sp.getServer().computeOnServer(s -> {
				MarketData data = MarketEngine.data(s);
				StockState st = data.stock(StockDef.FARMER);
				ItemDemand.Buyer buyer = ItemDemand.buyer(new ItemStack(Items.WHEAT), StockDef.FARMER);
				return MarketRules.current().saleTotal(buyer, st.price, st.population,
						data.soldToday().getOrDefault(buyer.saturationKey(), 0), 64, -1);
			});
			check(expected > 0, "os fazendeiros deveriam oferecer algo pelo trigo");
			ctx.clickScreenButton("Vender por " + MarketEngine.fmt(expected) + "◆");
			ctx.waitTicks(10);
			double got = balance(sp, playerId[0]) - before;
			check(Math.abs(got - expected) < 1e-6, "venda do trigo pagou " + got + ", esperado " + expected);
			int wheatLeft = sp.getServer().computeOnServer(s -> player(s).getInventory().countItem(Items.WHEAT));
			check(wheatLeft == 0, "o trigo deveria ter saído da mochila");
			// Segunda venda do mesmo item paga menos (a guilda já comprou hoje).
			double second = sp.getServer().computeOnServer(s -> {
				MarketData data = MarketEngine.data(s);
				StockState st = data.stock(StockDef.FARMER);
				ItemDemand.Buyer buyer = ItemDemand.buyer(new ItemStack(Items.WHEAT), StockDef.FARMER);
				return MarketRules.current().saleTotal(buyer, st.price, st.population,
						data.soldToday().getOrDefault(buyer.saturationKey(), 0), 64, -1);
			});
			check(second < expected, "a guilda deveria pagar menos depois de já ter comprado trigo hoje");
			BolsaMod.LOGGER.info("[teste] 64 trigos: {} (segunda leva valeria {})", expected, second);

			// ---- Carteira: depositar e sacar ----
			ctx.clickScreenButton("Carteira");
			ctx.waitTicks(2);
			ctx.clickScreenButton("Depositar ◆");
			ctx.waitTicks(10);
			check(sp.getServer().computeOnServer(s -> player(s).getInventory().countItem(Items.EMERALD)) == 0,
					"o depósito deveria levar todas as esmeraldas");
			ctx.takeScreenshot("bolsa_carteira");
			ctx.clickScreenButton("Sacar ◆");
			ctx.waitTicks(10);
			check(balance(sp, playerId[0]) < 1.0, "o saque deveria esvaziar o saldo");

			// ---- Herói da Vila: corretagem mais barata e sem pagar a alta da bolsa nas trocas ----
			sp.getServer().runOnServer(s -> player(s).addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 600, 0)));
			ctx.waitTicks(5);
			ctx.clickScreenButton("Ações");
			ctx.waitTicks(3);
			ctx.takeScreenshot("bolsa_heroi");
			ctx.setScreen(() -> null);
			ctx.waitTicks(3);

			// ---- Painel na tela de trocas ----
			openMerchant(ctx, sp);
			ctx.takeScreenshot("bolsa_villager");
			ctx.runOnClient(mc -> mc.player.closeContainer());
			ctx.waitTicks(5);

			// ---- Raid: começa e a bolsa da vila cai ----
			// Um raid só acontece numa vila de verdade: gera uma vila ao redor do jogador.
			sp.getServer().runCommand("execute at @p run place structure minecraft:village_plains ~ ~ ~");
			ctx.waitTicks(40);
			sp.getServer().runOnServer(MarketEngine::runSession); // zera a pressão e conta os villagers da vila nova
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				p.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
				Raid raid = p.level().getRaids().createOrExtendRaid(p, p.blockPosition());
				check(raid != null, "não conseguiu criar o raid");
			});
			ctx.waitFor(mc -> ClientMarket.raidOngoing(), 20 * 60);
			double raidPressure = sp.getServer().computeOnServer(s -> MarketEngine.pendingPressure(StockDef.FARMER));
			check(raidPressure <= -BolsaConfig.get().raidStartShock + 1e-9,
					"o começo do raid deveria pressionar FAZD3 para baixo, pressão = " + raidPressure);
			ctx.waitTicks(10);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_B);
			ctx.waitForScreen(BolsaScreen.class);
			clearToasts(ctx);
			ctx.waitTicks(5);
			ctx.takeScreenshot("bolsa_raid");
			ctx.setScreen(() -> null);
			sp.getServer().runOnServer(MarketEngine::runSession);

			// Vitória: força o fim do raid como vitória e confere a alta.
			sp.getServer().runOnServer(s -> {
				for (Raid raid : ((br.bolsavillager.mixin.RaidsAccessor) s.overworld().getRaids()).bolsavillager$getRaidMap().values()) {
					setRaidStatus(raid, "VICTORY");
				}
			});
			ctx.waitFor(mc -> !ClientMarket.raidOngoing(), 20 * 10);
			double victoryPressure = sp.getServer().computeOnServer(s -> MarketEngine.pendingPressure(StockDef.FARMER));
			check(victoryPressure >= BolsaConfig.get().raidVictoryRally - 1e-9,
					"a vitória no raid deveria puxar FAZD3 para cima, pressão = " + victoryPressure);
			ctx.takeScreenshot("bolsa_raid_vitoria");
			sp.getServer().runOnServer(MarketEngine::runSession);
			sp.getServer().runCommand("kill @e[type=#minecraft:raiders]");

			// ---- Dividendos: pagos com aviso no canto da tela ----
			ctx.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				mc.gui.hud.getChat().clearMessages(false);
			});
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getInventory().add(new ItemStack(Items.EMERALD, 64));
				br.bolsavillager.market.MarketEngine.executeTrade(p, StockDef.LIBRARIAN, 3);
				MarketEngine.payDividends(s, MarketEngine.data(s), MarketEngine.data(s).day);
				MarketEngine.syncEveryone(s);
			});
			ctx.waitTicks(10);
			check(sp.getServer().computeOnServer(s -> MarketEngine.data(s).account(playerId[0]).lastDividend) > 0,
					"deveria ter recebido dividendos");
			ctx.takeScreenshot("bolsa_dividendos");

			censusBefore[0] = sp.getServer().computeOnServer(s -> MarketEngine.data(s).stock(StockDef.FARMER).population);
			save = sp.getWorldSave();
		}

		// ---- Persistência: o mundo foi fechado; reabre e confere ----
		try (TestSingleplayerContext again = save.open()) {
			ctx.waitTicks(20);
			int libr = again.getServer().computeOnServer(s -> MarketEngine.data(s).account(playerId[0]).sharesOf(StockDef.LIBRARIAN));
			check(libr == 3, "ações deveriam continuar salvas após reabrir o mundo");
			int farmers = again.getServer().computeOnServer(s -> MarketEngine.data(s).stock(StockDef.FARMER).population);
			check(farmers == censusBefore[0] && farmers >= 3,
					"censo deveria continuar igual após reabrir: " + censusBefore[0] + " -> " + farmers);
			BolsaMod.LOGGER.info("[teste] Tudo certo: tecla, abas, ordens, balcão, carteira, herói, raid, dividendos e persistência OK");
		}
	}

	private interface CursorTarget {
		double[] at(int screenW, int screenH);
	}

	/** Clica num ponto da tela em coordenadas da GUI (converte para pixels da janela). */
	private static void clickInScreen(ClientGameTestContext ctx, CursorTarget target) {
		double[] pos = ctx.computeOnClient(mc -> {
			double[] p = target.at(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
			double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
			return new double[]{p[0] * scale, p[1] * scale};
		});
		ctx.getInput().setCursorPos(pos[0], pos[1]);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	private static void setRaidStatus(Raid raid, String status) {
		try {
			Field field = Raid.class.getDeclaredField("status");
			field.setAccessible(true);
			for (Object constant : field.getType().getEnumConstants()) {
				if (((Enum<?>) constant).name().equals(status)) {
					field.set(raid, constant);
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void openMerchant(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		ctx.runOnClient(mc -> ClientMarket.setLastMerchantStock(StockDef.FARMER));
		sp.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Villager farmer = villagers(server, VillagerProfession.FARMER).getFirst();
			farmer.mobInteract(player, InteractionHand.MAIN_HAND);
		});
		ctx.waitForScreen(MerchantScreen.class);
		clearToasts(ctx);
		ctx.waitTicks(5);
	}

	private static void clearToasts(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
	}

	private static int shares(TestSingleplayerContext sp, UUID player) {
		return sp.getServer().computeOnServer(s -> MarketEngine.data(s).account(player).sharesOf(StockDef.FARMER));
	}

	private static double balance(TestSingleplayerContext sp, UUID player) {
		return sp.getServer().computeOnServer(s -> MarketEngine.data(s).account(player).balance);
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

	@SuppressWarnings("unused")
	private static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
