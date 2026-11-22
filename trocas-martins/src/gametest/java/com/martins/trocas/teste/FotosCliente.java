package com.martins.trocas.teste;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.mercador.InteracaoMercador;
import com.martins.trocas.mercador.MercadorScreenHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/**
 * Só para desenvolvimento: com -Dtrocasmartins.foto=true abre o jogo, cria um mundo,
 * monta a cena com o Mercador e tira fotos da tela. Não vai no .jar do mod.
 */
public class FotosCliente implements ClientModInitializer {
	private int tick;
	private int etapa;
	private int espera;
	private VillagerEntity mercador;
	private VillagerEntity bibliotecario;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("trocasmartins.foto")) return;
		ClientTickEvents.END_CLIENT_TICK.register(this::aoTick);
	}

	private void foto(MinecraftClient c, String nome) {
		ScreenshotRecorder.saveScreenshot(c.runDirectory, nome + ".png", c.getFramebuffer(), t -> System.out.println("[foto] " + t.getString()));
	}

	private void mouse(MinecraftClient c, double guiX, double guiY) {
		double s = c.getWindow().getScaleFactor();
		GLFW.glfwSetCursorPos(c.getWindow().getHandle(), guiX * s, guiY * s);
		try {
			var m = net.minecraft.client.Mouse.class.getDeclaredMethod("onCursorPos", long.class, double.class, double.class);
			m.setAccessible(true);
			m.invoke(c.mouse, c.getWindow().getHandle(), guiX * s, guiY * s);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private void cmd(MinecraftServer server, ServerPlayerEntity p, String comando) {
		server.getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(4).withSilent(), comando);
	}

	private void aoTick(MinecraftClient c) {
		tick++;
		if (espera > 0) {
			espera--;
			return;
		}
		MinecraftServer server = c.getServer();
		c.getToastManager().clear();
		if (c.inGameHud != null) c.inGameHud.getChatHud().clear(false);
		switch (etapa) {
			case 0 -> {
				if (c.currentScreen instanceof TitleScreen && tick > 40) {
					LevelInfo info = new LevelInfo("Fotos Mercador", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
						new GameRules(), DataConfiguration.SAFE_MODE);
					c.createIntegratedServerLoader().createAndStart("fotos_mercador", info, new GeneratorOptions(20261122L, false, false),
						WorldPresets::createDemoOptions, new TitleScreen());
					etapa = 1;
				}
			}
			case 1 -> {
				if (c.player != null && c.world != null && server != null) {
					etapa = 2;
					espera = 60;
				}
			}
			case 2 -> {
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(c.player.getUuid());
				server.execute(() -> {
					cmd(server, p, "gamerule doDaylightCycle false");
					cmd(server, p, "gamerule doWeatherCycle false");
					cmd(server, p, "time set 6000");
					cmd(server, p, "weather clear");
					BlockPos b = p.getBlockPos();
					int y = p.getServerWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, b.getX(), b.getZ());
					cmd(server, p, String.format("fill %d %d %d %d %d %d air", b.getX() - 6, y, b.getZ() - 9, b.getX() + 6, y + 6, b.getZ() + 2));
					cmd(server, p, String.format("fill %d %d %d %d %d %d grass_block", b.getX() - 6, y - 1, b.getZ() - 9, b.getX() + 6, y - 1, b.getZ() + 2));
					cmd(server, p, String.format("tp @s %d %d %d 180 12", b.getX(), y, b.getZ() + 1));
					cmd(server, p, String.format("setblock %d %d %d trocasmartins:balcao_do_mercador[facing=south]", b.getX(), y, b.getZ() - 4));
					cmd(server, p, String.format("setblock %d %d %d minecraft:lectern[facing=south]", b.getX() + 3, y, b.getZ() - 4));
					cmd(server, p, String.format("setblock %d %d %d minecraft:lantern", b.getX(), y + 1, b.getZ() - 4));

					mercador = EntityType.VILLAGER.create(p.getServerWorld());
					mercador.refreshPositionAndAngles(b.getX() + 0.5 - 1.4, y, b.getZ() - 3.2, 20, 0);
					mercador.setVillagerData(mercador.getVillagerData().withProfession(ModRegistro.MERCADOR).withLevel(3));
					mercador.setAiDisabled(true);
					mercador.setHeadYaw(20);
					mercador.setBodyYaw(20);
					p.getServerWorld().spawnEntity(mercador);

					bibliotecario = EntityType.VILLAGER.create(p.getServerWorld());
					bibliotecario.refreshPositionAndAngles(b.getX() + 0.5 + 2.0, y, b.getZ() - 2.6, 160, 0);
					bibliotecario.setVillagerData(bibliotecario.getVillagerData().withProfession(VillagerProfession.LIBRARIAN).withLevel(1));
					bibliotecario.setAiDisabled(true);
					bibliotecario.setHeadYaw(160);
					bibliotecario.setBodyYaw(160);
					p.getServerWorld().spawnEntity(bibliotecario);
					// sobe o bibliotecário até o nível 5 para mostrar as trocas novas de todos os níveis
					bibliotecario.getOffers();
					for (int nivel = 2; nivel <= 5; nivel++) subirNivel(bibliotecario);
				});
				c.options.hudHidden = true;
				etapa = 3;
				espera = 80;
			}
			case 3 -> {
				foto(c, "1_mundo");
				c.options.hudHidden = false;
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(c.player.getUuid());
				server.execute(() -> {
					p.getInventory().clear();
					p.getInventory().setStack(0, new ItemStack(Items.DIRT, 64));
					p.getInventory().setStack(1, new ItemStack(Items.DIRT, 36));
					p.getInventory().setStack(2, new ItemStack(Items.STICK, 40));
					p.getInventory().setStack(3, new ItemStack(Items.WHEAT, 40));
					p.getInventory().setStack(4, new ItemStack(Items.IRON_INGOT, 20));
					p.getInventory().setStack(5, new ItemStack(Items.OAK_LOG, 12));
					p.getInventory().setStack(6, new ItemStack(Items.EMERALD, 30));
					p.getInventory().setStack(7, new ItemStack(Items.EMERALD_BLOCK, 3));
					InteracaoMercador.aoInteragir(p, p.getWorld(), Hand.MAIN_HAND, mercador, null);
				});
				etapa = 4;
				espera = 20;
			}
			case 4 -> {
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(c.player.getUuid());
				server.execute(() -> {
					if (p.currentScreenHandler instanceof MercadorScreenHandler h) h.escolherVenda(new ItemStack(Items.DIRT));
				});
				int x = (c.getWindow().getScaledWidth() - 276) / 2, y = (c.getWindow().getScaledHeight() - 166) / 2;
				mouse(c, x + 197, y + 45);
				etapa = 5;
				espera = 20;
			}
			case 5 -> {
				foto(c, "2_vender_faltam");
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(c.player.getUuid());
				server.execute(() -> {
					if (p.currentScreenHandler instanceof MercadorScreenHandler h) h.escolherVenda(new ItemStack(Items.STICK));
				});
				mouse(c, 1, 1);
				etapa = 6;
				espera = 20;
			}
			case 6 -> {
				foto(c, "3_vender_gravetos");
				tela(c, "pesquisa", "ferro");
				etapa = 7;
				espera = 10;
			}
			case 7 -> {
				foto(c, "4_pesquisa");
				tela(c, "pesquisa", "");
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(c.player.getUuid());
				server.execute(() -> {
					if (p.currentScreenHandler instanceof MercadorScreenHandler h) {
						for (int i = 0; i < h.getLoja().size(); i++)
							if (h.getLoja().get(i).resultado().isOf(Items.BREAD)) h.escolherCompra(i);
					}
				});
				etapa = 8;
				espera = 20;
			}
			case 8 -> {
				foto(c, "5_comprar");
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(c.player.getUuid());
				server.execute(() -> {
					if (p.currentScreenHandler instanceof MercadorScreenHandler h) {
						for (int i = 0; i < h.getLoja().size(); i++)
							if (h.getLoja().get(i).resultado().isOf(Items.DIAMOND)) h.escolherCompra(i);
					}
				});
				chamarRolar(c, 200);
				etapa = 9;
				espera = 20;
			}
			case 9 -> {
				foto(c, "6_comprar_diamante");
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(c.player.getUuid());
				server.execute(() -> {
					p.closeHandledScreen();
					bibliotecario.setExperience(com.martins.trocas.trocas.NiveisExtras.xpPara(2) + 120);
					com.martins.trocas.trocas.NiveisExtras.verificar(bibliotecario);
					bibliotecario.interactMob(p, Hand.MAIN_HAND);
				});
				etapa = 11;
				espera = 30;
			}
			case 11 -> {
				foto(c, "7_mestre_iii");
				etapa = 10;
				espera = 5;
			}
			case 10 -> {
				c.scheduleStop();
				etapa = 12;
			}
			default -> {
			}
		}
	}

	private static void tela(MinecraftClient c, String campo, String texto) {
		try {
			var f = com.martins.trocas.client.MercadorScreen.class.getDeclaredField(campo);
			f.setAccessible(true);
			((net.minecraft.client.gui.widget.TextFieldWidget) f.get(c.currentScreen)).setText(texto);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static void chamar(MinecraftClient c, String metodo, boolean arg) {
		try {
			var m = com.martins.trocas.client.MercadorScreen.class.getDeclaredMethod(metodo, boolean.class);
			m.setAccessible(true);
			m.invoke(c.currentScreen, arg);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static void chamarRolar(MinecraftClient c, int linhas) {
		c.currentScreen.mouseScrolled(0, 0, 0, -linhas);
	}

	private static void subirNivel(VillagerEntity v) {
		try {
			var m = VillagerEntity.class.getDeclaredMethod("levelUp");
			m.setAccessible(true);
			m.invoke(v);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
