package br.bolsavillager.market;

import br.bolsavillager.BolsaConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * As regras do mercado que o cliente também precisa conhecer para mostrar custos, efeito nas trocas e
 * dividendos previstos. O servidor monta a partir da config e manda junto com as cotações, então em
 * multiplayer vale sempre a config do servidor.
 */
public record MarketRules(
		double fee,
		double impactPerShare,
		double tradePriceEffect,
		double tradePriceEffectMax,
		boolean dividendsEnabled,
		double dividendBaseYield,
		double dividendActivityYield,
		int dividendTradesTarget
) {
	public static final MarketRules DEFAULT = fromConfig(new BolsaConfig());

	public static MarketRules current() {
		return fromConfig(BolsaConfig.get());
	}

	public static MarketRules fromConfig(BolsaConfig c) {
		return new MarketRules(c.brokerageFee, c.marketImpactPerShare, c.tradePriceEffect, c.tradePriceEffectMax,
				c.dividendsEnabled, c.dividendBaseYield, c.dividendActivityYield, c.dividendTradesTarget);
	}

	/** Valor "justo" da ação: cresce com a raiz da população da profissão. */
	public static double fairValue(StockDef def, int population) {
		return def.basePrice * (0.5 + 0.5 * Math.sqrt(1 + population));
	}

	/**
	 * Valor total de uma ordem (sem corretagem), pagando o preço médio ao longo do impacto que a própria
	 * ordem causa. Assim comprar e vender em seguida nunca dá lucro: a ida e a volta se anulam e a
	 * corretagem vira prejuízo. quantity > 0 compra, quantity < 0 vende.
	 */
	public double orderValue(double price, int quantity) {
		double x = this.impactPerShare * Math.abs(quantity);
		double average = quantity > 0 ? price * Math.expm1(x) / x : price * -Math.expm1(-x) / x;
		return average * Math.abs(quantity);
	}

	/** Custo total de uma compra, com corretagem. */
	public double buyCost(double price, int quantity) {
		return this.orderValue(price, quantity) * (1 + this.fee);
	}

	/** Quanto se recebe numa venda, já descontada a corretagem. */
	public double sellProceeds(double price, int quantity) {
		return this.orderValue(price, -quantity) * (1 - this.fee);
	}

	/**
	 * Ajuste de preço das trocas de um villager conforme a cotação da guilda dele:
	 * ação acima do valor justo = produtos mais caros; abaixo = promoção.
	 */
	public double tradePriceFactor(StockDef def, double price, int population) {
		double ratio = price / fairValue(def, population);
		return Mth.clamp((ratio - 1.0) * this.tradePriceEffect, -this.tradePriceEffectMax, this.tradePriceEffectMax);
	}

	/** Dividendo por ação que a empresa pagaria com o movimento de hoje. */
	public double dividendPerShare(StockDef def, double price, int population, int tradesDay) {
		if (!this.dividendsEnabled) {
			return 0;
		}
		// Guilda sem nenhum villager (e mercador sem nenhuma troca) não distribui nada.
		boolean active = def == StockDef.WANDERING_TRADER ? tradesDay > 0 : population > 0;
		if (!active) {
			return 0;
		}
		double activity = Math.min(1.0, (double) tradesDay / this.dividendTradesTarget);
		return price * (this.dividendBaseYield + this.dividendActivityYield * activity);
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeDouble(this.fee);
		buf.writeDouble(this.impactPerShare);
		buf.writeDouble(this.tradePriceEffect);
		buf.writeDouble(this.tradePriceEffectMax);
		buf.writeBoolean(this.dividendsEnabled);
		buf.writeDouble(this.dividendBaseYield);
		buf.writeDouble(this.dividendActivityYield);
		buf.writeVarInt(this.dividendTradesTarget);
	}

	public static MarketRules read(RegistryFriendlyByteBuf buf) {
		return new MarketRules(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
				buf.readBoolean(), buf.readDouble(), buf.readDouble(), Math.max(1, buf.readVarInt()));
	}
}
