package com.martins.trocas.teste;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.TrocasMartins;
import com.martins.trocas.mercador.Cotacao;
import com.martins.trocas.mercador.LojaMercador;
import com.martins.trocas.mercador.Taxa;
import com.martins.trocas.mercador.TrocasAldeoes;
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

		// igual aos aldeões: flecheiro compra 32 gravetos, fazendeiro 20 trigos, armeiro 1 diamante por 1 esmeralda
		Cotacao c = PrecoMercador.cotar(List.of(new ItemStack(Items.STICK, 32)), 1.0);
		ctx.assertTrue(c.ok() && c.esmeraldas() == 1, "32 gravetos = 1 esmeralda, veio " + c);
		c = PrecoMercador.cotar(List.of(new ItemStack(Items.STICK, 31)), 1.0);
		ctx.assertTrue(c.status() == Cotacao.Status.INSUFICIENTE && c.faltam() == 1, "31 gravetos: falta 1, veio " + c);
		c = PrecoMercador.cotar(List.of(new ItemStack(Items.WHEAT, 20)), 1.0);
		ctx.assertTrue(c.ok() && c.esmeraldas() == 1, "20 trigos = 1 esmeralda, veio " + c);
		igual(ctx, 1000, Math.round(PrecoMercador.valorUnitario(new ItemStack(Items.DIAMOND))), "diamante (armeiro paga 1)");
		igual(ctx, 250, Math.round(PrecoMercador.valorUnitario(new ItemStack(Items.IRON_INGOT))), "ferro (armeiro paga 1 por 4)");

		// tronco: 1 tronco = 4 tábuas = 8 gravetos = 0,25 esmeralda no flecheiro; o Mercador paga um pouco menos
		double tronco = PrecoMercador.valorUnitario(new ItemStack(Items.OAK_LOG));
		ctx.assertTrue(tronco > 200 && tronco < 250, "tronco deveria valer entre 0,2 e 0,25 esmeralda, veio " + tronco);
		double tabua = PrecoMercador.valorUnitario(new ItemStack(Items.OAK_PLANKS));
		ctx.assertTrue(tabua * 4 <= 250 && tronco <= tabua * 4, "tábuas e tronco coerentes: " + tabua + " / " + tronco);

		// nenhum aldeão vende algo mais barato do que o Mercador paga (não dá lucro)
		for (var e : TrocasAldeoes.VENDEM.entrySet()) {
			double pago = PrecoMercador.valorUnitario(new ItemStack(e.getKey()));
			ctx.assertTrue(pago <= e.getValue().porItem() * 1000 * 0.9 + 1,
				"lucro possível com " + e.getKey() + ": aldeão vende a " + e.getValue() + ", Mercador paga " + pago);
		}
		// o que aldeões compram, o Mercador paga igual (ou um pouco menos, se der lucro)
		for (var e : TrocasAldeoes.COMPRAM.entrySet()) {
			double pago = PrecoMercador.valorUnitario(new ItemStack(e.getKey()));
			ctx.assertTrue(pago <= e.getValue().porItem() * 1000 + 0.01,
				e.getKey() + ": aldeão paga " + e.getValue() + ", Mercador paga mais: " + pago);
		}
		// o que o Mercador vende não dá para revender com lucro (nem para ele, nem para aldeões)
		ctx.assertTrue(!LojaMercador.ofertas().isEmpty(), "a loja do Mercador está vazia");
		for (LojaMercador.Oferta o : LojaMercador.ofertas()) {
			double custoPorItem = o.esmeraldas() * 1000.0 / o.resultado().getCount();
			double revenda = PrecoMercador.valorUnitario(o.resultado());
			ctx.assertTrue(revenda <= custoPorItem + 1e-6, "revender " + o.resultado() + " dá lucro: " + custoPorItem + " < " + revenda);
			var compram = TrocasAldeoes.COMPRAM.get(o.resultado().getItem());
			if (compram != null) ctx.assertTrue(compram.porItem() * 1000 <= custoPorItem + 0.01,
				"revender " + o.resultado() + " a um aldeão dá lucro");
			ctx.assertFalse(o.resultado().isOf(Items.ELYTRA) || o.resultado().isOf(Items.TOTEM_OF_UNDYING), "item apelão na loja");
		}
		// itens raros com preço absurdo
		for (LojaMercador.Oferta o : LojaMercador.ofertas()) {
			if (o.resultado().isOf(Items.DIAMOND)) ctx.assertTrue(o.esmeraldas() >= 100, "diamante barato demais");
			if (o.resultado().isOf(Items.NETHERITE_INGOT)) ctx.assertTrue(o.esmeraldas() >= 500, "netherite barato demais");
			// mesmo preço do aldeão quando algum aldeão vende
			var vendem = TrocasAldeoes.VENDEM.get(o.resultado().getItem());
			if (vendem != null && vendem.esmeraldas() <= 64)
				ctx.assertTrue(o.esmeraldas() == vendem.esmeraldas() && o.resultado().getCount() == vendem.itens(),
					o.resultado() + ": deveria ser a mesma troca dos aldeões " + vendem);
		}

		c = PrecoMercador.cotar(List.of(new ItemStack(Items.DIRT, 5), new ItemStack(Items.STONE, 5)), 1.0);
		ctx.assertTrue(c.status() == Cotacao.Status.MISTURADO, "misturado: " + c);
		c = PrecoMercador.cotar(List.of(new ItemStack(Items.EMERALD, 5)), 1.0);
		ctx.assertTrue(c.status() == Cotacao.Status.NAO_COMPRA, "esmeralda: " + c);
		c = PrecoMercador.cotar(List.of(new ItemStack(Items.BEDROCK, 1)), 1.0);
		ctx.assertTrue(c.status() == Cotacao.Status.NAO_COMPRA, "bedrock: " + c);

		TradeOffer oferta = new TradeOffer(new TradedItem(Items.EMERALD, 1), new ItemStack(Items.DIAMOND_CHESTPLATE), 1, 1, 0f);
		ctx.assertTrue(PrecoMercador.valorUnitario(oferta.copySellItem()) <= 500, "peitoral comprado de aldeão por 1 esmeralda");
		ItemStack picaretaGasta = new ItemStack(Items.IRON_PICKAXE);
		picaretaGasta.setDamage(picaretaGasta.getMaxDamage() / 2);
		ctx.assertTrue(PrecoMercador.valorUnitario(picaretaGasta) < PrecoMercador.valorUnitario(new ItemStack(Items.IRON_PICKAXE)),
			"picareta gasta deveria valer menos");

		for (var item : List.of(Items.STICK, Items.OAK_LOG, Items.OAK_PLANKS, Items.DIRT, Items.COBBLESTONE, Items.WHEAT, Items.BREAD,
			Items.IRON_INGOT, Items.IRON_BLOCK, Items.RAW_IRON, Items.DIAMOND, Items.DIAMOND_PICKAXE, Items.ROTTEN_FLESH, Items.PAPER,
			Items.BOOK, Items.BOOKSHELF, Items.ARROW, Items.GLASS, Items.TORCH, Items.STRING, Items.WHITE_WOOL)) {
			Cotacao q = PrecoMercador.cotar(List.of(new ItemStack(item)), 1.0);
			TrocasMartins.LOGGER.info("[preço] {} -> {}", item, Taxa.descrever(q));
		}
		for (LojaMercador.Oferta o : LojaMercador.ofertas()) {
			TrocasMartins.LOGGER.info("[loja] {} + {} -> {}", o.custoA(), o.custoB(), o.resultado());
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
		h.escolherVenda(new ItemStack(Items.DIRT));
		igual(ctx, 128, h.getSlot(0).getStack().getCount() + h.getSlot(1).getStack().getCount(), "espaços cheios ao escolher");
		h.onSlotClick(saida, 0, SlotActionType.QUICK_MOVE, p);
		igual(ctx, 2, p.getInventory().count(Items.EMERALD), "esmeraldas depois do shift-clique");
		igual(ctx, 50, p.getInventory().count(Items.DIRT) + h.getSlot(0).getStack().getCount() + h.getSlot(1).getStack().getCount(),
			"terras que sobraram depois do shift-clique");

		h.getSlot(0).setStack(new ItemStack(Items.STONE, 3));
		h.getSlot(1).setStack(new ItemStack(Items.DIRT, 3));
		ctx.assertTrue(h.cotacaoCliente().status() == Cotacao.Status.MISTURADO, "itens misturados");

		// comprar: escolher pão na loja coloca as esmeraldas no pagamento e o pão no resultado
		h.getSlot(0).setStack(ItemStack.EMPTY);
		h.getSlot(1).setStack(ItemStack.EMPTY);
		p.getInventory().clear();
		p.getInventory().insertStack(new ItemStack(Items.EMERALD, 10));
		int pao = -1;
		for (int i = 0; i < h.getLoja().size(); i++) if (h.getLoja().get(i).resultado().isOf(Items.BREAD)) pao = i;
		ctx.assertTrue(pao >= 0, "a loja deveria vender pão");
		LojaMercador.Oferta oferta = h.getLoja().get(pao);
		h.escolherCompra(pao);
		ctx.assertTrue(h.cotacaoCliente().status() == Cotacao.Status.COMPRA_OK, "compra de pão: " + h.cotacaoCliente());
		ctx.assertTrue(h.getSlot(saida).getStack().isOf(Items.BREAD), "resultado = pão");
		h.onSlotClick(saida, 0, SlotActionType.PICKUP, p);
		ctx.assertTrue(h.getCursorStack().isOf(Items.BREAD), "pegou o pão");
		igual(ctx, 10 - oferta.esmeraldas(), p.getInventory().count(Items.EMERALD) + h.getSlot(0).getStack().getCount(), "esmeraldas depois da compra");
		h.setCursorStack(ItemStack.EMPTY);
		// diamante custa blocos de esmeralda: sem eles, X e "faltam N esmeraldas"
		int diamante = -1;
		for (int i = 0; i < h.getLoja().size(); i++) if (h.getLoja().get(i).resultado().isOf(Items.DIAMOND)) diamante = i;
		h.escolherCompra(diamante);
		ctx.assertTrue(h.cotacaoCliente().status() == Cotacao.Status.COMPRA_FALTA && h.cotacaoCliente().faltam() > 90,
			"diamante sem dinheiro: " + h.cotacaoCliente());

		// limite igual aos aldeões: 16 trocas de gravetos e depois esgota
		h.setCursorStack(ItemStack.EMPTY);
		h.getSlot(0).setStack(ItemStack.EMPTY);
		h.getSlot(1).setStack(ItemStack.EMPTY);
		p.getInventory().clear();
		for (int i = 0; i < 9; i++) p.getInventory().insertStack(new ItemStack(Items.STICK, 64));
		h.escolherVenda(new ItemStack(Items.STICK));
		h.onSlotClick(saida, 0, SlotActionType.QUICK_MOVE, p);
		igual(ctx, 16, p.getInventory().count(Items.EMERALD), "só 16 trocas de gravetos até repor");
		ctx.assertTrue(h.cotacaoCliente().status() == Cotacao.Status.ESGOTADO, "gravetos esgotados: " + h.cotacaoCliente());
		v.restock();
		h.escolherVenda(new ItemStack(Items.STICK));
		ctx.assertTrue(h.cotacaoCliente().ok(), "depois de repor volta a comprar: " + h.cotacaoCliente());

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
		// níveis depois de Mestre: precisa completar a experiência de cada nível
		VillagerEntity mestre = ctx.spawnEntity(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		mestre.setVillagerData(mestre.getVillagerData().withProfession(VillagerProfession.LIBRARIAN).withLevel(5));
		int antes = mestre.getOffers().size();
		mestre.setExperience(com.martins.trocas.trocas.NiveisExtras.xpPara(1) - 1);
		mestre.trade(new TradeOffer(new TradedItem(Items.EMERALD, 1), new ItemStack(Items.BOOK), 1, 0, 0f));
		igual(ctx, 0, com.martins.trocas.trocas.NiveisExtras.nivelExtra(mestre), "ainda não completou Mestre");
		mestre.trade(new TradeOffer(new TradedItem(Items.EMERALD, 1), new ItemStack(Items.BOOK), 1,
			com.martins.trocas.trocas.NiveisExtras.xpPara(2) - mestre.getExperience(), 0f));
		igual(ctx, 2, com.martins.trocas.trocas.NiveisExtras.nivelExtra(mestre), "deveria estar em Mestre III");
		igual(ctx, antes + 2, mestre.getOffers().size(), "uma troca nova por nível extra");
		for (int n = 1; n <= 40; n++) ctx.assertTrue(com.martins.trocas.trocas.NiveisExtras.xpPara(n + 1) > com.martins.trocas.trocas.NiveisExtras.xpPara(n), "níveis cada vez mais difíceis");
		mestre.discard();

		// fase 2: depois de entregar todos os itens fortes, alterna otimizar (mais barato) e inovar (troca nova)
		var lista = com.martins.trocas.trocas.NiveisExtras.todas().get(VillagerProfession.LIBRARIAN);
		VillagerEntity sabio = ctx.spawnEntity(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		sabio.setVillagerData(sabio.getVillagerData().withProfession(VillagerProfession.LIBRARIAN).withLevel(5));
		int base = sabio.getOffers().size();
		sabio.setExperience(com.martins.trocas.trocas.NiveisExtras.xpPara(lista.size()));
		com.martins.trocas.trocas.NiveisExtras.verificar(sabio);
		igual(ctx, base + lista.size(), sabio.getOffers().size(), "fase 1 entrega todos os itens fortes");
		int precoAntes = sabio.getOffers().get(base).getOriginalFirstBuyItem().getCount();
		sabio.setExperience(com.martins.trocas.trocas.NiveisExtras.xpPara(lista.size() + 2));
		com.martins.trocas.trocas.NiveisExtras.verificar(sabio);
		igual(ctx, base + lista.size() + 1, sabio.getOffers().size(), "otimizar não cria troca, inovar cria uma");
		ctx.assertTrue(sabio.getOffers().get(base).getOriginalFirstBuyItem().getCount() < precoAntes, "a primeira troca forte deveria ficar mais barata");
		sabio.discard();

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
