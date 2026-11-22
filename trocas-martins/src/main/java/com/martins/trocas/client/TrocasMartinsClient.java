package com.martins.trocas.client;

import com.martins.trocas.ModRegistro;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class TrocasMartinsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModRegistro.MERCADOR_SCREEN, MercadorScreen::new);
	}
}
