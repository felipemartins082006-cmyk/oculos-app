package br.bolsavillager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuração do mod, em {@code config/bolsavillager.json}. O arquivo é criado com os valores padrão na
 * primeira vez que o jogo abre; edite e use {@code /bolsa admin recarregar} (ou reinicie) para aplicar.
 * Valores fora da faixa permitida são corrigidos automaticamente.
 */
public final class BolsaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static BolsaConfig instance = new BolsaConfig();

	public String _leia_me = "Explicação de cada opção no README do mod (seção Configuração). "
			+ "Depois de editar, use /bolsa admin recarregar.";

	// ---- Pregão ----
	/** Ticks entre pregões (20 ticks = 1 segundo). Padrão 600 = 30 s. */
	public int sessionIntervalTicks = 600;
	/** Desvio padrão do ruído aleatório por pregão (0.006 = 0,6%). 0 = sem sorte/azar, só eventos. */
	public double volatility = 0.006;
	/** Força com que o preço volta ao valor justo por pregão (0.08 = 8% do caminho). */
	public double meanReversion = 0.08;
	/** Variação máxima de um pregão (0.15 = ±15%). */
	public double maxMovePerSession = 0.15;
	/** Multiplica a pressão de todos os eventos dos villagers (trocas, mortes, censo...). 1 = normal. */
	public double eventStrength = 1.0;
	/** Villagers comemoram/reclamam com partículas quando a ação da guilda mexe forte. */
	public boolean villagerParticles = true;

	// ---- Efeito da bolsa nas trocas ----
	/** Quanto o desvio da ação em relação ao valor justo vira ajuste de preço nas trocas. 0 = desliga. */
	public double tradePriceEffect = 0.3;
	/** Limite do ajuste de preço nas trocas (0.3 = ±30%). */
	public double tradePriceEffectMax = 0.3;

	// ---- Corretora ----
	/** Corretagem por ordem (0.01 = 1%). */
	public double brokerageFee = 0.01;
	/** Quanto cada ação comprada/vendida move o preço (0.004 = 0,4% por ação). */
	public double marketImpactPerShare = 0.004;
	/** Permite /bolsa comprar e /bolsa vender longe de um Terminal da Bolsa. */
	public boolean chatTradingAnywhere = false;
	/** Distância máxima (em blocos) de um terminal para negociar pelo chat. */
	public int terminalRange = 8;

	// ---- Dividendos ----
	public boolean dividendsEnabled = true;
	/** Dividendo mínimo por dia, em fração do preço da ação (0.002 = 0,2% ao dia). */
	public double dividendBaseYield = 0.002;
	/** Dividendo extra se a guilda teve movimento, em fração do preço (0.008 = até +0,8% ao dia). */
	public double dividendActivityYield = 0.008;
	/** Trocas no dia para a guilda pagar o dividendo extra completo. */
	public int dividendTradesTarget = 12;

	public static BolsaConfig get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(BolsaMod.MOD_ID + ".json");
	}

	/** Lê o arquivo (criando com os padrões se não existir). Retorna mensagem de erro ou null se deu certo. */
	public static String load() {
		Path path = path();
		String error = null;
		BolsaConfig loaded = new BolsaConfig();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				BolsaConfig parsed = GSON.fromJson(reader, BolsaConfig.class);
				if (parsed != null) {
					loaded = parsed;
				}
			} catch (IOException | JsonParseException e) {
				error = e.getMessage();
				BolsaMod.LOGGER.error("Não consegui ler {}, usando os valores atuais: {}", path, e.getMessage());
				return error;
			}
		}
		loaded.clamp();
		instance = loaded;
		save();
		return error;
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			BolsaMod.LOGGER.error("Não consegui salvar {}: {}", path, e.getMessage());
		}
	}

	private void clamp() {
		this.sessionIntervalTicks = Mth.clamp(this.sessionIntervalTicks, 20, 72000);
		this.volatility = Mth.clamp(this.volatility, 0.0, 0.1);
		this.meanReversion = Mth.clamp(this.meanReversion, 0.0, 1.0);
		this.maxMovePerSession = Mth.clamp(this.maxMovePerSession, 0.001, 0.5);
		this.eventStrength = Mth.clamp(this.eventStrength, 0.0, 10.0);
		this.tradePriceEffect = Mth.clamp(this.tradePriceEffect, 0.0, 2.0);
		this.tradePriceEffectMax = Mth.clamp(this.tradePriceEffectMax, 0.0, 0.9);
		this.brokerageFee = Mth.clamp(this.brokerageFee, 0.0, 0.5);
		this.marketImpactPerShare = Mth.clamp(this.marketImpactPerShare, 0.0001, 0.05);
		this.terminalRange = Mth.clamp(this.terminalRange, 1, 64);
		this.dividendBaseYield = Mth.clamp(this.dividendBaseYield, 0.0, 0.1);
		this.dividendActivityYield = Mth.clamp(this.dividendActivityYield, 0.0, 0.1);
		this.dividendTradesTarget = Mth.clamp(this.dividendTradesTarget, 1, 10000);
	}
}
