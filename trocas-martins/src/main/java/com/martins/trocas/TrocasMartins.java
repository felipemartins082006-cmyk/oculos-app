package com.martins.trocas;

import com.martins.trocas.mercador.ComandoMercador;
import com.martins.trocas.mercador.InteracaoMercador;
import com.martins.trocas.mercador.MercadorConfig;
import com.martins.trocas.mercador.PrecoMercador;
import com.martins.trocas.trocas.NovasTrocas;
import com.martins.trocas.mercador.MercadorScreenHandler;
import com.martins.trocas.rede.EscolherOfertaPayload;
import com.martins.trocas.rede.OfertasPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrocasMartins implements ModInitializer {
	public static final String MOD_ID = "trocasmartins";
	public static final Logger LOGGER = LoggerFactory.getLogger("Trocas Martins");

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		MercadorConfig.carregar();
		ModRegistro.registrar();
		NovasTrocas.registrar();

		PayloadTypeRegistry.playS2C().register(OfertasPayload.ID, OfertasPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(EscolherOfertaPayload.ID, EscolherOfertaPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EscolherOfertaPayload.ID, (payload, ctx) -> {
			if (ctx.player().currentScreenHandler instanceof MercadorScreenHandler h) {
				h.escolherOferta(payload.indice());
			}
		});

		UseEntityCallback.EVENT.register(InteracaoMercador::aoInteragir);
		CommandRegistrationCallback.EVENT.register(ComandoMercador::registrar);

		ServerLifecycleEvents.SERVER_STARTED.register(PrecoMercador::recalcular);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, ok) -> {
			MercadorConfig.carregar();
			PrecoMercador.recalcular(server);
		});
		LOGGER.info("Trocas Martins carregado!");
	}
}
