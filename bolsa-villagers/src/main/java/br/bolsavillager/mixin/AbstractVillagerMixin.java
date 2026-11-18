package br.bolsavillager.mixin;

import br.bolsavillager.market.MarketEngine;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avisa a bolsa sempre que uma troca é concluída com um villager ou mercador ambulante. */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixin {
	@Inject(method = "notifyTrade", at = @At("TAIL"))
	private void bolsavillager$onTrade(MerchantOffer offer, CallbackInfo ci) {
		MarketEngine.onTrade((AbstractVillager) (Object) this, offer);
	}
}
