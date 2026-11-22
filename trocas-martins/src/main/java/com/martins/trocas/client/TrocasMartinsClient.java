package com.martins.trocas.client;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.mercador.MercadorMenu;
import com.martins.trocas.rede.CatalogoPayload;
import com.martins.trocas.rede.NivelPayload;
import com.martins.trocas.rede.OfertasPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;

public class TrocasMartinsClient implements ClientModInitializer {
	/** Nível extra (depois de Mestre) do aldeão da tela de trocas aberta. */
	public static int nivelExtra;

	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModRegistro.MERCADOR_MENU, MercadorScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(NivelPayload.TYPE, (payload, ctx) -> nivelExtra = payload.extra());
		ClientPlayNetworking.registerGlobalReceiver(OfertasPayload.TYPE, (payload, ctx) -> {
			if (ctx.player() != null && ctx.player().containerMenu instanceof MercadorMenu m && m.containerId == payload.syncId()) {
				m.setOfertas(payload.ofertas(), payload.usos());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(CatalogoPayload.TYPE, (payload, ctx) -> {
			if (ctx.player() != null && ctx.player().containerMenu instanceof MercadorMenu m && m.containerId == payload.syncId()) {
				m.setCatalogo(payload.compra(), payload.venda());
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof MerchantScreen) ScreenEvents.remove(screen).register(s -> nivelExtra = 0);
		});
	}
}
