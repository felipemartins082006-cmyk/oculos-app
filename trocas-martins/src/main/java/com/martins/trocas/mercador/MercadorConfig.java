package com.martins.trocas.mercador;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.martins.trocas.TrocasMartins;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuração em config/trocasmartins.json.
 * Os preços personalizados ficam em "precos": { "minecraft:dirt": 0.01, "#minecraft:logs": 0.05 }
 * (valor em esmeraldas por UM item; 0 = o Mercador não compra).
 */
public final class MercadorConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static Dados atual = new Dados();

	public static final class Dados {
		public String _ajuda = "precos: valor em esmeraldas de UM item (ex.: 0.01 = 100 itens por esmeralda; 0 = não compra). "
			+ "Use '#namespace:tag' para grupos de itens. multiplicador_global ajusta todos os preços do Mercador.";
		public double multiplicador_global = 1.0;
		public double bonus_por_nivel = 0.04;
		public boolean bonus_por_reputacao = true;
		public double bonus_maximo = 0.20;
		public int max_esmeraldas_por_item = 64;
		public int trocas_novas_por_nivel = 1;
		public Map<String, Double> precos = new LinkedHashMap<>();
	}

	private MercadorConfig() {
	}

	public static void carregar() {
		Path arquivo = FabricLoader.getInstance().getConfigDir().resolve("trocasmartins.json");
		try {
			if (Files.exists(arquivo)) {
				try (Reader r = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
					Dados d = GSON.fromJson(r, Dados.class);
					if (d != null) {
						if (d.precos == null) d.precos = new LinkedHashMap<>();
						atual = d;
					}
				}
			} else {
				Dados d = new Dados();
				d.precos.put("minecraft:rotten_flesh", 0.03);
				atual = d;
			}
			Files.createDirectories(arquivo.getParent());
			try (Writer w = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
				GSON.toJson(atual, w);
			}
		} catch (Exception e) {
			TrocasMartins.LOGGER.error("Não foi possível ler {}: usando valores padrão", arquivo, e);
			atual = new Dados();
		}
	}
}
