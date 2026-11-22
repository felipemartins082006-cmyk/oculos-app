package com.martins.trocas.teste;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.TrocasMartins;
import com.martins.trocas.mercador.Cotacao;
import com.martins.trocas.mercador.LojaMercador;
import com.martins.trocas.mercador.MercadorMenu;
import com.martins.trocas.mercador.PrecoMercador;
import com.martins.trocas.mercador.Taxa;
import com.martins.trocas.mercador.TrocasAldeoes;
import com.martins.trocas.trocas.NiveisExtras;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class TestesMercador {

	private static void piso(GameTestHelper ctx) {
		for (int x = 0; x < 8; x++)
			for (int z = 0; z < 8; z++)
				ctx.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
	}

	private static void igual(GameTestHelper ctx, long esperado, long real, String oQue) {
		ctx.assertTrue(esperado == real, oQue + ": esperado " + esperado + ", veio " + real);
	}

	@GameTest
	public void precos(GameTestHelper ctx) {
		ctx.assertTrue(ctx.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, TrocasMartins.id("balcao_do_mercador"))).isPresent(),
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
		var mascate = TrocasAldeoes.VENDEM.get(Items.OAK_LOG);
		double tetoTronco = mascate == null ? 250 : Math.min(250, mascate.porItem() * 1000 * 0.9);
		ctx.assertTrue(tronco > 0 && tronco <= tetoTronco + 0.01, "tronco: até 0,25 esmeralda e sem lucro com o mascate, veio " + tronco);
		double tabua = PrecoMercador.valorUnitario(new ItemStack(Items.OAK_PLANKS));
		ctx.assertTrue(tabua * 4 <= 250 && tronco <= tabua * 4, "tábuas e tronco coerentes: " + tabua + " / " + tronco);

		// nenhum aldeão vende algo mais barato do que o Mercador paga (não dá lucro)
		for (var e : TrocasAldeoes.VENDEM.entrySet()) {
			if (TrocasAldeoes.COMPRAM.containsKey(e.getKey())) continue; // segue o preço do próprio aldeão
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
			ctx.assertTrue(!(o.resultado().is(Items.ELYTRA) || o.resultado().is(Items.TOTEM_OF_UNDYING)), "item apelão na loja");
		}
		// itens raros com preço absurdo
		for (LojaMercador.Oferta o : LojaMercador.ofertas()) {
			if (o.resultado().is(Items.DIAMOND)) ctx.assertTrue(o.esmeraldas() >= 100, "diamante barato demais");
			if (o.resultado().is(Items.NETHERITE_INGOT)) ctx.assertTrue(o.esmeraldas() >= 500, "netherite barato demais");
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

		MerchantOffer oferta = new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.DIAMOND_CHESTPLATE), 1, 1, 0f);
		ctx.assertTrue(PrecoMercador.valorUnitario(oferta.assemble()) <= 500, "peitoral comprado de aldeão por 1 esmeralda");
		ItemStack picaretaGasta = new ItemStack(Items.IRON_PICKAXE);
		picaretaGasta.setDamageValue(picaretaGasta.getMaxDamage() / 2);
		ctx.assertTrue(PrecoMercador.valorUnitario(picaretaGasta) < PrecoMercador.valorUnitario(new ItemStack(Items.IRON_PICKAXE)),
			"picareta gasta deveria valer menos");

		for (var item : List.of(Items.STICK, Items.OAK_LOG, Items.OAK_PLANKS, Items.DIRT, Items.COBBLESTONE, Items.WHEAT, Items.BREAD,
			Items.IRON_INGOT, Items.IRON_BLOCK, Items.RAW_IRON, Items.DIAMOND, Items.DIAMOND_PICKAXE, Items.ROTTEN_FLESH, Items.PAPER,
			Items.BOOK, Items.BOOKSHELF, Items.ARROW, Items.GLASS, Items.TORCH, Items.STRING, Items.WOOL.white())) {
			Cotacao q = PrecoMercador.cotar(List.of(new ItemStack(item)), 1.0);
			TrocasMartins.LOGGER.info("[preço] {} -> {}", item, Taxa.descrever(q));
		}
		for (LojaMercador.Oferta o : LojaMercador.ofertas()) {
			TrocasMartins.LOGGER.info("[loja] {} + {} -> {}", o.custoA(), o.custoB(), o.resultado());
		}
		ctx.succeed();
	}

	@GameTest
	public void vendaNoBalcao(GameTestHelper ctx) {
		piso(ctx);
		Villager v = ctx.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 2));
		v.setVillagerData(v.getVillagerData().withProfession(ctx.getLevel().registryAccess(), ModRegistro.MERCADOR_KEY).withLevel(1));
		FakePlayer p = FakePlayer.get(ctx.getLevel());
		p.getInventory().clearContent();
		p.snapTo(v.getX() + 1, v.getY(), v.getZ(), 0, 0);
		v.setTradingPlayer(p);
		MercadorMenu h = new MercadorMenu(1, p.getInventory(), v);
		int saida = MercadorMenu.SAIDA;

		// 100 terras nos 2 espaços de pagamento: faltam 25 para 1 esmeralda
		h.getSlot(0).set(new ItemStack(Items.DIRT, 64));
		h.getSlot(1).set(new ItemStack(Items.DIRT, 36));
		Cotacao c = h.cotacaoCliente();
		ctx.assertTrue(c.status() == Cotacao.Status.INSUFICIENTE, "100 terras: insuficiente, veio " + c);
		igual(ctx, 25, c.faltam(), "faltam com 100 terras");
		ctx.assertTrue(h.getSlot(saida).getItem().isEmpty(), "sem proposta não pode haver esmeralda na saída");
		ctx.assertTrue(!h.getSlot(saida).mayPickup(p), "não pode pegar a saída sem proposta");

		h.getSlot(1).set(new ItemStack(Items.DIRT, 64));
		c = h.cotacaoCliente();
		ctx.assertTrue(c.ok() && c.esmeraldas() == 1 && c.usados() == 125, "128 terras: 1 esmeralda usando 125, veio " + c);

		int xpAntes = v.getVillagerXp();
		h.clicked(saida, 0, ContainerInput.PICKUP, p);
		ctx.assertTrue(h.getCarried().is(Items.EMERALD) && h.getCarried().getCount() == 1, "cursor com 1 esmeralda");
		igual(ctx, 3, h.getSlot(0).getItem().getCount() + h.getSlot(1).getItem().getCount(), "terras que sobraram");
		ctx.assertTrue(v.getVillagerXp() > xpAntes, "o Mercador deveria ganhar experiência");
		h.setCarried(ItemStack.EMPTY);
		h.getSlot(0).set(ItemStack.EMPTY);
		h.getSlot(1).set(ItemStack.EMPTY);

		// lista de trocas montada a partir do inventário
		p.getInventory().add(new ItemStack(Items.DIRT, 64));
		p.getInventory().add(new ItemStack(Items.DIRT, 64));
		p.getInventory().add(new ItemStack(Items.DIRT, 64));
		p.getInventory().add(new ItemStack(Items.DIRT, 64));
		p.getInventory().add(new ItemStack(Items.DIRT, 44));
		p.getInventory().add(new ItemStack(Items.BEDROCK, 1));
		h.broadcastChanges();
		var ofertas = h.getOfertas();
		igual(ctx, 1, ofertas.size(), "só a terra deveria aparecer na lista (bedrock não)");
		var o = ofertas.get(0);
		ctx.assertTrue(o.item().is(Items.DIRT) && o.quantidade() == 125 && o.esmeraldas() == 1 && o.temJogador() == 300,
			"oferta da terra: " + o);

		// clicar na troca enche os espaços; shift-clique vende tudo o que dá
		h.escolherVenda(new ItemStack(Items.DIRT));
		igual(ctx, 128, h.getSlot(0).getItem().getCount() + h.getSlot(1).getItem().getCount(), "espaços cheios ao escolher");
		h.clicked(saida, 0, ContainerInput.QUICK_MOVE, p);
		igual(ctx, 2, p.getInventory().countItem(Items.EMERALD), "esmeraldas depois do shift-clique");
		igual(ctx, 50, p.getInventory().countItem(Items.DIRT) + h.getSlot(0).getItem().getCount() + h.getSlot(1).getItem().getCount(),
			"terras que sobraram depois do shift-clique");

		h.getSlot(0).set(new ItemStack(Items.STONE, 3));
		h.getSlot(1).set(new ItemStack(Items.DIRT, 3));
		ctx.assertTrue(h.cotacaoCliente().status() == Cotacao.Status.MISTURADO, "itens misturados");

		// comprar: escolher pão na loja coloca as esmeraldas no pagamento e o pão no resultado
		h.getSlot(0).set(ItemStack.EMPTY);
		h.getSlot(1).set(ItemStack.EMPTY);
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(Items.EMERALD, 10));
		int pao = -1;
		for (int i = 0; i < h.getLoja().size(); i++) if (h.getLoja().get(i).resultado().is(Items.BREAD)) pao = i;
		ctx.assertTrue(pao >= 0, "a loja deveria vender pão");
		LojaMercador.Oferta oferta = h.getLoja().get(pao);
		h.escolherCompra(pao);
		ctx.assertTrue(h.cotacaoCliente().status() == Cotacao.Status.COMPRA_OK, "compra de pão: " + h.cotacaoCliente());
		ctx.assertTrue(h.getSlot(saida).getItem().is(Items.BREAD), "resultado = pão");
		h.clicked(saida, 0, ContainerInput.PICKUP, p);
		ctx.assertTrue(h.getCarried().is(Items.BREAD), "pegou o pão");
		igual(ctx, 10 - oferta.esmeraldas(), p.getInventory().countItem(Items.EMERALD) + h.getSlot(0).getItem().getCount(), "esmeraldas depois da compra");
		h.setCarried(ItemStack.EMPTY);
		// diamante custa blocos de esmeralda: sem eles, X e "faltam N esmeraldas"
		int diamante = -1;
		for (int i = 0; i < h.getLoja().size(); i++) if (h.getLoja().get(i).resultado().is(Items.DIAMOND)) diamante = i;
		h.escolherCompra(diamante);
		ctx.assertTrue(h.cotacaoCliente().status() == Cotacao.Status.COMPRA_FALTA && h.cotacaoCliente().faltam() > 90,
			"diamante sem dinheiro: " + h.cotacaoCliente());

		// limite igual aos aldeões: 16 trocas de gravetos e depois esgota
		h.setCarried(ItemStack.EMPTY);
		h.getSlot(0).set(ItemStack.EMPTY);
		h.getSlot(1).set(ItemStack.EMPTY);
		p.getInventory().clearContent();
		for (int i = 0; i < 9; i++) p.getInventory().add(new ItemStack(Items.STICK, 64));
		h.escolherVenda(new ItemStack(Items.STICK));
		h.clicked(saida, 0, ContainerInput.QUICK_MOVE, p);
		igual(ctx, 16, p.getInventory().countItem(Items.EMERALD), "só 16 trocas de gravetos até repor");
		ctx.assertTrue(h.cotacaoCliente().status() == Cotacao.Status.ESGOTADO, "gravetos esgotados: " + h.cotacaoCliente());
		v.restock();
		h.escolherVenda(new ItemStack(Items.STICK));
		ctx.assertTrue(h.cotacaoCliente().ok(), "depois de repor volta a comprar: " + h.cotacaoCliente());

		h.removed(p);
		ctx.assertTrue(v.getTradingPlayer() == null, "o Mercador deveria ficar livre ao fechar");
		ctx.succeed();
	}

	@GameTest
	public void trocasNovas(GameTestHelper ctx) {
		piso(ctx);
		for (ResourceKey<VillagerProfession> prof : List.of(VillagerProfession.LIBRARIAN, VillagerProfession.ARMORER, VillagerProfession.FARMER,
			VillagerProfession.WEAPONSMITH, VillagerProfession.CLERIC, VillagerProfession.FISHERMAN)) {
			for (int nivel = 1; nivel <= 5; nivel++) {
				Villager v = ctx.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 3));
				v.setVillagerData(v.getVillagerData().withProfession(ctx.getLevel().registryAccess(), prof).withLevel(nivel));
				MerchantOffers ofertas = v.getOffers();
				ctx.assertTrue(ofertas.size() >= 2, prof + " nível " + nivel + " deveria ter troca nova, tem " + ofertas.size());
				TrocasMartins.LOGGER.info("[troca] {} nv{}: {}", prof, nivel, ofertas.get(ofertas.size() - 1).getResult());
				v.discard();
			}
		}
		// níveis depois de Mestre: precisa completar a experiência de cada nível
		Villager mestre = ctx.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 3));
		mestre.setVillagerData(mestre.getVillagerData().withProfession(ctx.getLevel().registryAccess(), VillagerProfession.LIBRARIAN).withLevel(5));
		int antes = mestre.getOffers().size();
		mestre.setVillagerXp(NiveisExtras.xpPara(1) - 1);
		mestre.notifyTrade(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BOOK), 1, 0, 0f));
		igual(ctx, 0, NiveisExtras.nivelExtra(mestre), "ainda não completou Mestre");
		mestre.notifyTrade(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BOOK), 1,
			NiveisExtras.xpPara(2) - mestre.getVillagerXp(), 0f));
		igual(ctx, 2, NiveisExtras.nivelExtra(mestre), "deveria estar em Mestre III");
		igual(ctx, antes + 2, mestre.getOffers().size(), "uma troca nova por nível extra");
		for (int n = 1; n <= 40; n++) ctx.assertTrue(NiveisExtras.xpPara(n + 1) > NiveisExtras.xpPara(n), "níveis cada vez mais difíceis");
		mestre.discard();

		// fase 2: depois de entregar todos os itens fortes, alterna otimizar (mais barato) e inovar (troca nova)
		var lista = NiveisExtras.todas().get(VillagerProfession.LIBRARIAN);
		Villager sabio = ctx.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 3));
		sabio.setVillagerData(sabio.getVillagerData().withProfession(ctx.getLevel().registryAccess(), VillagerProfession.LIBRARIAN).withLevel(5));
		int base = sabio.getOffers().size();
		sabio.setVillagerXp(NiveisExtras.xpPara(lista.size()));
		NiveisExtras.verificar(sabio);
		igual(ctx, base + lista.size(), sabio.getOffers().size(), "fase 1 entrega todos os itens fortes");
		int precoAntes = sabio.getOffers().get(base).getBaseCostA().getCount();
		sabio.setVillagerXp(NiveisExtras.xpPara(lista.size() + 2));
		NiveisExtras.verificar(sabio);
		igual(ctx, base + lista.size() + 1, sabio.getOffers().size(), "otimizar não cria troca, inovar cria uma");
		ctx.assertTrue(sabio.getOffers().get(base).getBaseCostA().getCount() < precoAntes, "a primeira troca forte deveria ficar mais barata");
		sabio.discard();

		Villager m = ctx.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 3));
		m.setVillagerData(m.getVillagerData().withProfession(ctx.getLevel().registryAccess(), ModRegistro.MERCADOR_KEY));
		ctx.assertTrue(m.getOffers().isEmpty(), "o Mercador não usa a tela de trocas normal");
		ctx.succeed();
	}

	@GameTest(maxTicks = 2400)
	public void aldeaoViraMercador(GameTestHelper ctx) {
		piso(ctx);
		ctx.setBlock(new BlockPos(5, 1, 5), ModRegistro.BALCAO_DO_MERCADOR);
		Villager v = ctx.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 2));
		ctx.succeedWhen(() -> ctx.assertTrue(v.getVillagerData().profession().is(ModRegistro.MERCADOR_KEY),
			"aldeão ainda é " + v.getVillagerData().profession()));
	}
}
