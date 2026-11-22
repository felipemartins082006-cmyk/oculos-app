package com.martins.trocas.mixin;

import com.martins.trocas.trocas.NovasTrocas;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {
	@Inject(method = "fillRecipes", at = @At("TAIL"))
	private void trocasmartins$novasTrocas(CallbackInfo ci) {
		VillagerEntity self = (VillagerEntity) (Object) this;
		NovasTrocas.adicionarTrocas(self, self.getOffers());
	}

	@Inject(method = "afterUsing", at = @At("TAIL"))
	private void trocasmartins$niveisExtras(net.minecraft.village.TradeOffer offer, CallbackInfo ci) {
		VillagerEntity self = (VillagerEntity) (Object) this;
		if (self.getVillagerData().getProfession() != com.martins.trocas.ModRegistro.MERCADOR) {
			com.martins.trocas.trocas.NiveisExtras.verificar(self);
		}
	}

	@Inject(method = "beginTradeWith", at = @At("TAIL"))
	private void trocasmartins$enviarNivel(net.minecraft.entity.player.PlayerEntity player, CallbackInfo ci) {
		if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
			com.martins.trocas.trocas.NiveisExtras.enviar(sp, (VillagerEntity) (Object) this);
		}
	}
}
