package br.bolsavillager.mixin;

import br.bolsavillager.market.MarketEngine;
import br.bolsavillager.market.MarketRules;
import br.bolsavillager.market.StockState;
import br.bolsavillager.market.StockDef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin {
	/** Reposição de estoque no posto de trabalho = guilda produtiva. */
	@Inject(method = "restock", at = @At("TAIL"))
	private void bolsavillager$onRestock(CallbackInfo ci) {
		MarketEngine.onRestock((Villager) (Object) this);
	}

	/**
	 * Depois dos descontos de reputação/herói da vila, aplica a "inflação da guilda":
	 * ação em alta encarece as trocas, ação em baixa deixa tudo em promoção.
	 * O ajuste some sozinho quando a troca termina (o jogo zera os preços especiais).
	 */
	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void bolsavillager$applyMarketPrices(Player player, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (!(self.level() instanceof ServerLevel level)) {
			return;
		}
		StockDef def = StockDef.forEntity(self);
		if (def == null) {
			return;
		}
		StockState state = MarketEngine.data(level.getServer()).stock(def);
		double factor = MarketRules.current().tradePriceFactor(def, state.price, state.population);
		if (factor == 0) {
			return;
		}
		for (MerchantOffer offer : self.getOffers()) {
			int base = offer.getBaseCostA().getCount();
			// Quando o villager COMPRA do jogador (paga em esmeraldas), guilda rica pede menos itens.
			double direction = offer.getResult().is(Items.EMERALD) ? -1.0 : 1.0;
			int diff = (int) Math.round(base * factor * direction);
			if (diff != 0) {
				offer.addToSpecialPriceDiff(diff);
			}
		}
	}
}
