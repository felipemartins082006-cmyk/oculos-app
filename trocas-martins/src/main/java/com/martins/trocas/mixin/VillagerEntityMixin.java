package com.martins.trocas.mixin;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.trocas.NiveisExtras;
import com.martins.trocas.trocas.NovasTrocas;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerEntityMixin {
	/** Depois das trocas normais de cada nível, adiciona as trocas novas do mod. */
	@Inject(method = "updateTrades", at = @At("TAIL"))
	private void trocasmartins$novasTrocas(ServerLevel level, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		NovasTrocas.adicionarTrocas(self, self.getOffers());
	}

	/** Níveis depois de Mestre. */
	@Inject(method = "rewardTradeXp", at = @At("TAIL"))
	private void trocasmartins$niveisExtras(MerchantOffer offer, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (!ModRegistro.ehMercador(self)) NiveisExtras.verificar(self);
	}

	@Inject(method = "startTrading", at = @At("TAIL"))
	private void trocasmartins$enviarNivel(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp) NiveisExtras.enviar(sp, (Villager) (Object) this);
	}
}
