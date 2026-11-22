package com.martins.trocas.client;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.mercador.MercadorScreenHandler;
import com.martins.trocas.rede.CatalogoPayload;
import com.martins.trocas.rede.OfertasPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class TrocasMartinsClient implements ClientModInitializer {
	/** Nível extra (depois de Mestre) do aldeão da tela de trocas aberta. */
	public static int nivelExtra;

	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModRegistro.MERCADOR_SCREEN, MercadorScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(com.martins.trocas.rede.NivelPayload.ID, (payload, ctx) -> nivelExtra = payload.extra());
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen)) return;
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.remove(screen).register(s -> nivelExtra = 0);
		});
		ClientPlayNetworking.registerGlobalReceiver(OfertasPayload.ID, (payload, ctx) -> {
			if (ctx.player() != null && ctx.player().currentScreenHandler instanceof MercadorScreenHandler h
				&& h.syncId == payload.syncId()) {
				h.setOfertas(payload.ofertas(), payload.usos());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(CatalogoPayload.ID, (payload, ctx) -> {
			if (ctx.player() != null && ctx.player().currentScreenHandler instanceof MercadorScreenHandler h
				&& h.syncId == payload.syncId()) {
				h.setCatalogo(payload.compra(), payload.venda());
			}
		});
	}
}
