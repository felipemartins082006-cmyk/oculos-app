package com.martins.trocas.client;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.mercador.MercadorScreenHandler;
import com.martins.trocas.rede.OfertasPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class TrocasMartinsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModRegistro.MERCADOR_SCREEN, MercadorScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(OfertasPayload.ID, (payload, ctx) -> {
			if (ctx.player() != null && ctx.player().currentScreenHandler instanceof MercadorScreenHandler h
				&& h.syncId == payload.syncId()) {
				h.setOfertas(payload.ofertas());
			}
		});
	}
}
