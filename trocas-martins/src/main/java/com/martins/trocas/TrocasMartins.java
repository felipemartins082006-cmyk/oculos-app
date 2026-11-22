package com.martins.trocas;

import com.martins.trocas.mercador.ComandoMercador;
import com.martins.trocas.mercador.InteracaoMercador;
import com.martins.trocas.mercador.MercadorConfig;
import com.martins.trocas.mercador.MercadorMenu;
import com.martins.trocas.mercador.PrecoMercador;
import com.martins.trocas.rede.CatalogoPayload;
import com.martins.trocas.rede.EscolherOfertaPayload;
import com.martins.trocas.rede.NivelPayload;
import com.martins.trocas.rede.OfertasPayload;
import com.martins.trocas.trocas.NiveisExtras;
import com.martins.trocas.trocas.NovasTrocas;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrocasMartins implements ModInitializer {
	public static final String MOD_ID = "trocasmartins";
	public static final Logger LOGGER = LoggerFactory.getLogger("Trocas Martins");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		MercadorConfig.carregar();
		ModRegistro.registrar();
		NovasTrocas.registrar();
		NiveisExtras.registrar();

		PayloadTypeRegistry.clientboundPlay().register(OfertasPayload.TYPE, OfertasPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CatalogoPayload.TYPE, CatalogoPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(NivelPayload.TYPE, NivelPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(EscolherOfertaPayload.TYPE, EscolherOfertaPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EscolherOfertaPayload.TYPE, (payload, ctx) -> {
			if (ctx.player().containerMenu instanceof MercadorMenu m) {
				if (payload.comprar()) m.escolherCompra(payload.indice());
				else m.escolherVenda(payload.item());
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
