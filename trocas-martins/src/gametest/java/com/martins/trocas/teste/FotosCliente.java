package com.martins.trocas.teste;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.mercador.InteracaoMercador;
import com.martins.trocas.mercador.MercadorMenu;
import com.martins.trocas.trocas.NiveisExtras;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.levelgen.Heightmap;

/** Só para desenvolvimento: abre o jogo de verdade, monta a cena e tira fotos (./gradlew runFotos). */
public class FotosCliente implements FabricClientGameTest {
	private Villager mercador;
	private Villager bibliotecario;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("time set 6000");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runOnServer(this::montarCena);
			ctx.waitTicks(40);
			sp.getConnection().waitForChunksRender();
			ctx.getInput().pressKey(o -> o.keyToggleGui);
			ctx.waitTick();
			ctx.takeScreenshot("1_mundo");
			ctx.getInput().pressKey(o -> o.keyToggleGui);

			// tela do Mercador: terra que não fecha 1 esmeralda
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = jogador(s);
				p.getInventory().clearContent();
				p.getInventory().add(new ItemStack(Items.DIRT, 64));
				p.getInventory().add(new ItemStack(Items.DIRT, 36));
				p.getInventory().add(new ItemStack(Items.STICK, 40));
				p.getInventory().add(new ItemStack(Items.WHEAT, 40));
				p.getInventory().add(new ItemStack(Items.IRON_INGOT, 20));
				p.getInventory().add(new ItemStack(Items.OAK_LOG, 12));
				p.getInventory().add(new ItemStack(Items.EMERALD, 30));
				p.getInventory().add(new ItemStack(Items.EMERALD_BLOCK, 3));
				InteracaoMercador.aoInteragir(p, p.level(), InteractionHand.MAIN_HAND, mercador, null);
			});
			ctx.waitTicks(10);
			sp.getServer().runOnServer(s -> {
				if (jogador(s).containerMenu instanceof MercadorMenu m) m.escolherVenda(new ItemStack(Items.DIRT));
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("2_vender_faltam");

			sp.getServer().runOnServer(s -> {
				if (jogador(s).containerMenu instanceof MercadorMenu m) m.escolherVenda(new ItemStack(Items.STICK));
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("3_vender_gravetos");

			ctx.runOnClient(mc -> {
				for (var w : mc.gui.screen().children()) if (w instanceof EditBox e) e.setValue("ferro");
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("4_pesquisa");
			ctx.runOnClient(mc -> {
				for (var w : mc.gui.screen().children()) if (w instanceof EditBox e) e.setValue("");
			});

			sp.getServer().runOnServer(s -> {
				if (jogador(s).containerMenu instanceof MercadorMenu m) {
					for (int i = 0; i < m.getLoja().size(); i++)
						if (m.getLoja().get(i).resultado().is(Items.BREAD)) m.escolherCompra(i);
				}
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("5_comprar");

			sp.getServer().runOnServer(s -> {
				if (jogador(s).containerMenu instanceof MercadorMenu m) {
					for (int i = 0; i < m.getLoja().size(); i++)
						if (m.getLoja().get(i).resultado().is(Items.DIAMOND)) m.escolherCompra(i);
				}
			});
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.gui.screen().mouseScrolled(0, 0, 0, -200));
			ctx.waitTicks(2);
			ctx.takeScreenshot("6_comprar_diamante");

			// bibliotecário em nível bem alto
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = jogador(s);
				p.closeContainer();
				bibliotecario.setVillagerXp(NiveisExtras.xpPara(26) + 2000);
				NiveisExtras.verificar(bibliotecario);
				bibliotecario.mobInteract(p, InteractionHand.MAIN_HAND);
			});
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.gui.screen().mouseScrolled(0, 0, 0, -12));
			ctx.waitTicks(2);
			ctx.takeScreenshot("7_mestre_alto");
		}
	}

	private static ServerPlayer jogador(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void montarCena(MinecraftServer s) {
		ServerPlayer p = jogador(s);
		ServerLevel w = p.level();
		BlockPos b = p.blockPosition();
		int y = w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getX(), b.getZ());
		s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(),
			String.format("tp %s %d %d %d 180 12", p.getScoreboardName(), b.getX(), y, b.getZ() + 1));
		w.setBlockAndUpdate(new BlockPos(b.getX(), y, b.getZ() - 4), ModRegistro.BALCAO_DO_MERCADOR.defaultBlockState()
			.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.SOUTH));
		w.setBlockAndUpdate(new BlockPos(b.getX(), y + 1, b.getZ() - 4), net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState());

		mercador = EntityTypes.VILLAGER.create(w, EntitySpawnReason.COMMAND);
		mercador.snapTo(b.getX() + 0.5 - 1.4, y, b.getZ() - 3.2, 20, 0);
		mercador.setVillagerData(mercador.getVillagerData().withProfession(w.registryAccess(), ModRegistro.MERCADOR_KEY).withLevel(5));
		mercador.setVillagerXp(NiveisExtras.xpPara(3) + 300);
		NiveisExtras.verificar(mercador);
		mercador.setNoAi(true);
		mercador.setYHeadRot(20);
		mercador.setYBodyRot(20);
		w.addFreshEntity(mercador);

		bibliotecario = EntityTypes.VILLAGER.create(w, EntitySpawnReason.COMMAND);
		bibliotecario.snapTo(b.getX() + 0.5 + 2.0, y, b.getZ() - 2.6, 160, 0);
		bibliotecario.setVillagerData(bibliotecario.getVillagerData().withProfession(w.registryAccess(), VillagerProfession.LIBRARIAN).withLevel(5));
		bibliotecario.setNoAi(true);
		bibliotecario.setYHeadRot(160);
		bibliotecario.setYBodyRot(160);
		w.addFreshEntity(bibliotecario);
		bibliotecario.getOffers();
	}
}
