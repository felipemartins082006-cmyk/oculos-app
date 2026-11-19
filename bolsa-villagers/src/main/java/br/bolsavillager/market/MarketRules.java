package br.bolsavillager.market;

import br.bolsavillager.BolsaConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * As regras do mercado que o cliente também precisa conhecer para mostrar custos, efeito nas trocas,
 * ofertas do balcão e dividendos previstos. O servidor monta a partir da config e manda junto com as
 * cotações, então em multiplayer vale sempre a config do servidor.
 *
 * <p>Nos métodos com {@code heroLevel}: -1 = sem o efeito Herói da Vila; 0 = Herói I; 1 = Herói II...
 */
public record MarketRules(
		double fee,
		double impactPerShare,
		double tradePriceEffect,
		double tradePriceEffectMax,
		boolean dividendsEnabled,
		double dividendBaseYield,
		double dividendActivityYield,
		int dividendTradesTarget,
		double saleDemandPerVillager,
		double heroSaleBonus,
		double heroFeeDiscount
) {
	public static final MarketRules DEFAULT = fromConfig(new BolsaConfig());

	public static MarketRules current() {
		return fromConfig(BolsaConfig.get());
	}

	public static MarketRules fromConfig(BolsaConfig c) {
		return new MarketRules(c.brokerageFee, c.marketImpactPerShare, c.tradePriceEffect, c.tradePriceEffectMax,
				c.dividendsEnabled, c.dividendBaseYield, c.dividendActivityYield, c.dividendTradesTarget,
				c.saleDemandPerVillager, c.heroSaleBonus, c.heroFeeDiscount);
	}

	/** Valor "justo" da ação: cresce com a raiz da população da profissão. */
	public static double fairValue(StockDef def, int population) {
		return def.basePrice * (0.5 + 0.5 * Math.sqrt(1 + population));
	}

	// ---- Ações ----

	/** Corretagem efetiva: o Herói da Vila paga menos. */
	public double feeFor(int heroLevel) {
		return heroLevel >= 0 ? this.fee * (1 - this.heroFeeDiscount) : this.fee;
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

	public double buyCost(double price, int quantity, int heroLevel) {
		return this.orderValue(price, quantity) * (1 + this.feeFor(heroLevel));
	}

	public double sellProceeds(double price, int quantity, int heroLevel) {
		return this.orderValue(price, -quantity) * (1 - this.feeFor(heroLevel));
	}

	/** Quantas ações dá para comprar com {@code funds} esmeraldas. */
	public int maxAffordable(double price, double funds, int heroLevel, int cap) {
		int lo = 0, hi = cap;
		while (lo < hi) {
			int mid = (lo + hi + 1) / 2;
			if (this.buyCost(price, mid, heroLevel) <= funds + 1e-9) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return lo;
	}

	// ---- Efeito nas trocas com villagers ----

	/**
	 * Ajuste de preço das trocas de um villager conforme a cotação da guilda dele:
	 * ação acima do valor justo = produtos mais caros; abaixo = promoção.
	 */
	public double tradePriceFactor(StockDef def, double price, int population) {
		double ratio = price / fairValue(def, population);
		return Mth.clamp((ratio - 1.0) * this.tradePriceEffect, -this.tradePriceEffectMax, this.tradePriceEffectMax);
	}

	// ---- Balcão de vendas (jogador vende itens, a guilda decide quanto paga) ----

	/** Prosperidade da guilda: guilda valorizada paga melhor, guilda em crise paga menos. */
	public static double prosperity(StockDef def, double price, int population) {
		return Mth.clamp(Math.sqrt(price / fairValue(def, population)), 0.6, 1.5);
	}

	public double heroSaleMultiplier(int heroLevel) {
		return heroLevel >= 0 ? 1 + this.heroSaleBonus + 0.05 * heroLevel : 1;
	}

	/** Quantas unidades a guilda absorve por dia antes de o preço cair pela metade. */
	public double saleCapacity(ItemDemand.Buyer buyer, int population) {
		return buyer.unitsPerEmerald() * this.saleDemandPerVillager * Math.sqrt(Math.max(1, population));
	}

	/** Preço da primeira unidade, antes da saturação. 0 se a guilda não tem ninguém para comprar. */
	public double saleBasePrice(ItemDemand.Buyer buyer, double stockPrice, int population, int heroLevel) {
		if (population <= 0) {
			return 0;
		}
		return prosperity(buyer.guild(), stockPrice, population) * this.heroSaleMultiplier(heroLevel) / buyer.unitsPerEmerald();
	}

	/**
	 * Quanto a guilda paga por {@code quantity} unidades, sabendo que já comprou {@code soldToday} hoje.
	 * Cada unidade a mais vale um pouco menos: preço(s) = base × D / (D + s).
	 */
	public double saleTotal(ItemDemand.Buyer buyer, double stockPrice, int population, int soldToday, int quantity, int heroLevel) {
		double base = this.saleBasePrice(buyer, stockPrice, population, heroLevel);
		if (base <= 0 || quantity <= 0) {
			return 0;
		}
		double d = this.saleCapacity(buyer, population);
		return base * d * Math.log((d + soldToday + quantity) / (d + soldToday));
	}

	/** Interesse atual da guilda nesse item (1 = total, cai conforme ela compra no dia). */
	public double saleInterest(ItemDemand.Buyer buyer, int population, int soldToday) {
		double d = this.saleCapacity(buyer, population);
		return d / (d + soldToday);
	}

	// ---- Dividendos ----

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
		buf.writeDouble(this.saleDemandPerVillager);
		buf.writeDouble(this.heroSaleBonus);
		buf.writeDouble(this.heroFeeDiscount);
	}

	public static MarketRules read(RegistryFriendlyByteBuf buf) {
		return new MarketRules(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
				buf.readBoolean(), buf.readDouble(), buf.readDouble(), Math.max(1, buf.readVarInt()),
				buf.readDouble(), buf.readDouble(), buf.readDouble());
	}
}
