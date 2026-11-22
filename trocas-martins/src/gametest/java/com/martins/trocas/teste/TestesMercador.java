package com.martins.trocas.teste;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.TrocasMartins;
import com.martins.trocas.mercador.Cotacao;
import com.martins.trocas.mercador.MercadorScreenHandler;
import com.martins.trocas.mercador.PrecoMercador;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;

import java.util.List;

public class TestesMercador implements FabricGameTest {

	private static void piso(TestContext ctx) {
		for (int x = 0; x < 8; x++)
			for (int z = 0; z < 8; z++)
				ctx.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
	}

	private static void igual(TestContext ctx, long esperado, long real, String oQue) {
		ctx.assertTrue(esperado == real, oQue + ": esperado " + esperado + ", veio " + real);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void precos(TestContext ctx) {
		ctx.assertTrue(ctx.getWorld().getRecipeManager().get(TrocasMartins.id("balcao_do_mercador")).isPresent(),
			"a receita do Balcão do Mercador deveria existir");
		igual(ctx, 8, PrecoMercador.valorUnitario(new ItemStack(Items.DIRT)), "terra");
		igual(ctx, 9, PrecoMercador.valorUnitario(new ItemStack(Items.OAK_PLANKS)), "tábua de carvalho (receita)");
		igual(ctx, 2430, PrecoMercador.valorUnitario(new ItemStack(Items.IRON_BLOCK)), "bloco de ferro (receita)");
		igual(ctx, 135, PrecoMercador.valorUnitario(new ItemStack(Items.BREAD)), "pão (receita)");
		igual(ctx, 5405, PrecoMercador.valorUnitario(new ItemStack(Items.DIAMOND_SWORD)), "espada de diamante");
		igual(ctx, 28805, PrecoMercador.valorUnitario(new ItemStack(Items.NETHERITE_SWORD)), "espada de netherite");

		Cotacao c = PrecoMercador.cotar(List.of(new ItemStack(Items.DIRT, 1)), 1.0);
		ctx.assertTrue(c.status() == Cotacao.Status.INSUFICIENTE, "1 terra deveria ser insuficiente: " + c);
		igual(ctx, 124, c.faltam(), "faltam para 1 terra");

		c = PrecoMercador.cotar(List.of(new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64)), 1.0);
		ctx.assertTrue(c.ok(), "128 terras deveriam valer 1 esmeralda: " + c);
		igual(ctx, 1, c.esmeraldas(), "esmeraldas por 128 terras");
		igual(ctx, 125, c.usados(), "terras usadas");
		igual(ctx, 122, c.faltam(), "faltam para a 2a esmeralda");

		c = PrecoMercador.cotar(List.of(new ItemStack(Items.DIAMOND, 2)), 1.0);
		igual(ctx, 6, c.esmeraldas(), "2 diamantes");

		c = PrecoMercador.cotar(List.of(new ItemStack(Items.DIRT, 5), new ItemStack(Items.STONE, 5)), 1.0);
		ctx.assertTrue(c.status() == Cotacao.Status.MISTURADO, "misturado: " + c);
		c = PrecoMercador.cotar(List.of(new ItemStack(Items.EMERALD, 5)), 1.0);
		ctx.assertTrue(c.status() == Cotacao.Status.NAO_COMPRA, "esmeralda: " + c);
		c = PrecoMercador.cotar(List.of(new ItemStack(Items.BEDROCK, 1)), 1.0);
		ctx.assertTrue(c.status() == Cotacao.Status.NAO_COMPRA, "bedrock: " + c);

		igual(ctx, 77, PrecoMercador.valorUnitario(new ItemStack(Items.COOKED_BEEF)), "bife (forno)");
		TradeOffer oferta = new TradeOffer(new TradedItem(Items.EMERALD, 1), new ItemStack(Items.DIAMOND_CHESTPLATE), 1, 1, 0f);
		igual(ctx, 500, PrecoMercador.valorUnitario(oferta.copySellItem()), "peitoral comprado de aldeão por 1 esmeralda");

		ItemStack picaretaGasta = new ItemStack(Items.IRON_PICKAXE);
		picaretaGasta.setDamage(picaretaGasta.getMaxDamage() / 2);
		ctx.assertTrue(PrecoMercador.valorUnitario(picaretaGasta) < PrecoMercador.valorUnitario(new ItemStack(Items.IRON_PICKAXE)),
			"picareta gasta deveria valer menos");

		for (var item : List.of(Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG, Items.WHEAT, Items.BREAD, Items.TORCH, Items.IRON_INGOT,
			Items.GOLD_BLOCK, Items.DIAMOND, Items.DIAMOND_PICKAXE, Items.ENCHANTING_TABLE, Items.BEACON, Items.CAKE, Items.GLASS,
			Items.STONE_BRICKS, Items.RED_BED, Items.ROTTEN_FLESH, Items.NETHERITE_INGOT, Items.ELYTRA, Items.MUSIC_DISC_CAT)) {
			Cotacao q = PrecoMercador.cotar(List.of(new ItemStack(item, 64)), 1.0);
			TrocasMartins.LOGGER.info("[preço] {} = {} milésimos | 64 un. -> {} esmeraldas ({} usados)", item,
				PrecoMercador.valorUnitario(new ItemStack(item)), q.esmeraldas(), q.usados());
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void vendaNoBalcao(TestContext ctx) {
		piso(ctx);
		VillagerEntity v = ctx.spawnEntity(EntityType.VILLAGER, new BlockPos(2, 1, 2));
		v.setVillagerData(v.getVillagerData().withProfession(ModRegistro.MERCADOR).withLevel(1));
		FakePlayer p = FakePlayer.get(ctx.getWorld());
		p.getInventory().clear();
		p.refreshPositionAndAngles(v.getX() + 1, v.getY(), v.getZ(), 0, 0);
		v.setCustomer(p);
		MercadorScreenHandler h = new MercadorScreenHandler(1, p.getInventory(), v);
		int saida = MercadorScreenHandler.SAIDA;

		// 100 terras nos 2 espaços de pagamento: faltam 25 para 1 esmeralda
		h.getSlot(0).setStack(new ItemStack(Items.DIRT, 64));
		h.getSlot(1).setStack(new ItemStack(Items.DIRT, 36));
		Cotacao c = h.cotacaoCliente();
		ctx.assertTrue(c.status() == Cotacao.Status.INSUFICIENTE, "100 terras: insuficiente, veio " + c);
		igual(ctx, 25, c.faltam(), "faltam com 100 terras");
		ctx.assertTrue(h.getSlot(saida).getStack().isEmpty(), "sem proposta não pode haver esmeralda na saída");
		ctx.assertFalse(h.getSlot(saida).canTakeItems(p), "não pode pegar a saída sem proposta");

		h.getSlot(1).setStack(new ItemStack(Items.DIRT, 64));
		c = h.cotacaoCliente();
		ctx.assertTrue(c.ok() && c.esmeraldas() == 1 && c.usados() == 125, "128 terras: 1 esmeralda usando 125, veio " + c);

		int xpAntes = v.getExperience();
		h.onSlotClick(saida, 0, SlotActionType.PICKUP, p);
		ctx.assertTrue(h.getCursorStack().isOf(Items.EMERALD) && h.getCursorStack().getCount() == 1, "cursor com 1 esmeralda");
		igual(ctx, 3, h.getSlot(0).getStack().getCount() + h.getSlot(1).getStack().getCount(), "terras que sobraram");
		ctx.assertTrue(v.getExperience() > xpAntes, "o Mercador deveria ganhar experiência");
		h.setCursorStack(ItemStack.EMPTY);
		h.getSlot(0).setStack(ItemStack.EMPTY);
		h.getSlot(1).setStack(ItemStack.EMPTY);

		// lista de trocas montada a partir do inventário
		p.getInventory().insertStack(new ItemStack(Items.DIRT, 64));
		p.getInventory().insertStack(new ItemStack(Items.DIRT, 64));
		p.getInventory().insertStack(new ItemStack(Items.DIRT, 64));
		p.getInventory().insertStack(new ItemStack(Items.DIRT, 64));
		p.getInventory().insertStack(new ItemStack(Items.DIRT, 44));
		p.getInventory().insertStack(new ItemStack(Items.BEDROCK, 1));
		h.sendContentUpdates();
		var ofertas = h.getOfertas();
		igual(ctx, 1, ofertas.size(), "só a terra deveria aparecer na lista (bedrock não)");
		var o = ofertas.get(0);
		ctx.assertTrue(o.item().isOf(Items.DIRT) && o.quantidade() == 125 && o.esmeraldas() == 1 && o.temJogador() == 300,
			"oferta da terra: " + o);

		// clicar na troca enche os espaços; shift-clique vende tudo o que dá
		h.escolherOferta(0);
		igual(ctx, 128, h.getSlot(0).getStack().getCount() + h.getSlot(1).getStack().getCount(), "espaços cheios ao escolher");
		h.onSlotClick(saida, 0, SlotActionType.QUICK_MOVE, p);
		igual(ctx, 2, p.getInventory().count(Items.EMERALD), "esmeraldas depois do shift-clique");
		igual(ctx, 50, p.getInventory().count(Items.DIRT) + h.getSlot(0).getStack().getCount() + h.getSlot(1).getStack().getCount(),
			"terras que sobraram depois do shift-clique");

		h.getSlot(0).setStack(new ItemStack(Items.STONE, 3));
		h.getSlot(1).setStack(new ItemStack(Items.DIRT, 3));
		ctx.assertTrue(h.cotacaoCliente().status() == Cotacao.Status.MISTURADO, "itens misturados");

		h.onClosed(p);
		ctx.assertTrue(v.getCustomer() == null, "o Mercador deveria ficar livre ao fechar");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void trocasNovas(TestContext ctx) {
		piso(ctx);
		for (VillagerProfession prof : List.of(VillagerProfession.LIBRARIAN, VillagerProfession.ARMORER, VillagerProfession.FARMER,
			VillagerProfession.WEAPONSMITH, VillagerProfession.CLERIC, VillagerProfession.FISHERMAN)) {
			for (int nivel = 1; nivel <= 5; nivel++) {
				VillagerEntity v = ctx.spawnEntity(EntityType.VILLAGER, new BlockPos(3, 1, 3));
				v.setVillagerData(v.getVillagerData().withProfession(prof).withLevel(nivel));
				TradeOfferList ofertas = v.getOffers();
				ctx.assertTrue(ofertas.size() >= 2, prof + " nível " + nivel + " deveria ter troca nova, tem " + ofertas.size());
				TrocasMartins.LOGGER.info("[troca] {} nv{}: {}", prof, nivel, ofertas.get(ofertas.size() - 1).getSellItem());
				v.discard();
			}
		}
		VillagerEntity m = ctx.spawnEntity(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		m.setVillagerData(m.getVillagerData().withProfession(ModRegistro.MERCADOR));
		ctx.assertTrue(m.getOffers().isEmpty(), "o Mercador não usa a tela de trocas normal");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 2400)
	public void aldeaoViraMercador(TestContext ctx) {
		piso(ctx);
		ctx.setBlockState(new BlockPos(5, 1, 5), ModRegistro.BALCAO_DO_MERCADOR);
		VillagerEntity v = ctx.spawnEntity(EntityType.VILLAGER, new BlockPos(2, 1, 2));
		ctx.addInstantFinalTask(() -> ctx.assertTrue(v.getVillagerData().getProfession() == ModRegistro.MERCADOR,
			"aldeão ainda é " + v.getVillagerData().getProfession()));
	}
}
