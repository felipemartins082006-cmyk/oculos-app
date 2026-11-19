package br.bolsavillager.market;

import br.bolsavillager.BolsaConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * As regras do mercado que o cliente também precisa conhecer para mostrar custos, efeito nas trocas,
 * ofertas do balcão e dividendos previstos. O servidor monta a partir da config e manda junto com as
 * cotações, então em multiplayer vale sempre a config do servidor.
 *
 * <p><b>Todo pagamento é em esmeraldas inteiras.</b> Por dentro a cotação tem casas decimais (para o
 * gráfico andar suave), mas tudo o que o jogador paga ou recebe é arredondado: compras para cima,
 * vendas para baixo.
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
		double heroFeeDiscount,
		boolean unlimitedTrades,
		double scarcitySurcharge
) {
	public static final MarketRules DEFAULT = fromConfig(new BolsaConfig());

	public static MarketRules current() {
		return fromConfig(BolsaConfig.get());
	}

	public static MarketRules fromConfig(BolsaConfig c) {
		return new MarketRules(c.brokerageFee, c.marketImpactPerShare, c.tradePriceEffect, c.tradePriceEffectMax,
				c.dividendsEnabled, c.dividendBaseYield, c.dividendActivityYield, c.dividendTradesTarget,
				c.saleDemandPerVillager, c.heroSaleBonus, c.heroFeeDiscount,
				c.unlimitedTrades, c.scarcitySurchargePerExtraTrade);
	}

	/** Valor "justo" da ação: cresce com a raiz da população da profissão. */
	public static double fairValue(StockDef def, int population) {
		return def.basePrice * (0.5 + 0.5 * Math.sqrt(1 + population));
	}

	/** Cotação mostrada: a interna arredondada, nunca menos de 1 esmeralda. */
	public static int shownPrice(double price) {
		return Math.max(1, (int) Math.round(price));
	}

	// ---- Ações ----

	/** Corretagem efetiva: o Herói da Vila paga menos. */
	public double feeFor(int heroLevel) {
		return heroLevel >= 0 ? this.fee * (1 - this.heroFeeDiscount) : this.fee;
	}

	/**
	 * Valor exato de uma ordem (sem corretagem), pagando o preço médio ao longo do impacto que a própria
	 * ordem causa. Assim comprar e vender em seguida nunca dá lucro. quantity > 0 compra, quantity < 0 vende.
	 */
	public double orderValue(double price, int quantity) {
		double x = this.impactPerShare * Math.abs(quantity);
		double average = quantity > 0 ? price * Math.expm1(x) / x : price * -Math.expm1(-x) / x;
		return average * Math.abs(quantity);
	}

	/** Quantas esmeraldas custa comprar {@code quantity} ações (arredondado para cima). */
	public int buyCost(double price, int quantity, int heroLevel) {
		double exact = this.orderValue(price, quantity) * (1 + this.feeFor(heroLevel));
		return Math.max(quantity, (int) Math.ceil(exact - 1e-9));
	}

	/** Quantas esmeraldas se recebe vendendo {@code quantity} ações (arredondado para baixo). */
	public int sellProceeds(double price, int quantity, int heroLevel) {
		double exact = this.orderValue(price, -quantity) * (1 - this.feeFor(heroLevel));
		return Math.max(0, (int) Math.floor(exact + 1e-9));
	}

	/** Quantas ações dá para comprar com {@code emeralds} esmeraldas. */
	public int maxAffordable(double price, int emeralds, int heroLevel, int cap) {
		int lo = 0, hi = cap;
		while (lo < hi) {
			int mid = (lo + hi + 1) / 2;
			if (this.buyCost(price, mid, heroLevel) <= emeralds) {
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

	/** Valor da primeira unidade, antes da saturação. 0 se a guilda não tem ninguém para comprar. */
	public double saleBasePrice(ItemDemand.Buyer buyer, double stockPrice, int population, int heroLevel) {
		if (population <= 0) {
			return 0;
		}
		return prosperity(buyer.guild(), stockPrice, population) * this.heroSaleMultiplier(heroLevel) / buyer.unitsPerEmerald();
	}

	/** Valor exato que a guilda daria por {@code quantity} unidades: preço(s) = base × D / (D + s). */
	public double saleValue(ItemDemand.Buyer buyer, double stockPrice, int population, int soldToday, int quantity, int heroLevel) {
		double base = this.saleBasePrice(buyer, stockPrice, population, heroLevel);
		if (base <= 0 || quantity <= 0) {
			return 0;
		}
		double d = this.saleCapacity(buyer, population);
		return base * d * Math.log((d + soldToday + quantity) / (d + soldToday));
	}

	/** Esmeraldas inteiras que a guilda paga por {@code quantity} unidades (arredondado para baixo). */
	public int salePayout(ItemDemand.Buyer buyer, double stockPrice, int population, int soldToday, int quantity, int heroLevel) {
		return (int) Math.floor(this.saleValue(buyer, stockPrice, population, soldToday, quantity, heroLevel) + 1e-9);
	}

	/**
	 * Menor quantidade de itens que ainda rende o mesmo pagamento que {@code quantity}. Assim o jogador
	 * nunca entrega item "de graça" por causa do arredondamento: o resto fica na mochila.
	 */
	public int saleQuantityFor(ItemDemand.Buyer buyer, double stockPrice, int population, int soldToday, int quantity, int heroLevel) {
		int payout = this.salePayout(buyer, stockPrice, population, soldToday, quantity, heroLevel);
		if (payout <= 0) {
			return 0;
		}
		int lo = 1, hi = quantity;
		while (lo < hi) {
			int mid = (lo + hi) / 2;
			if (this.salePayout(buyer, stockPrice, population, soldToday, mid, heroLevel) >= payout) {
				hi = mid;
			} else {
				lo = mid + 1;
			}
		}
		return lo;
	}

	/** Quantas unidades valem 1 esmeralda agora (para mostrar sem casas decimais). */
	public int unitsPerEmeraldNow(ItemDemand.Buyer buyer, double stockPrice, int population, int soldToday, int heroLevel) {
		double base = this.saleBasePrice(buyer, stockPrice, population, heroLevel);
		if (base <= 0) {
			return 0;
		}
		double d = this.saleCapacity(buyer, population);
		double unitNow = base * d / (d + soldToday);
		return Math.max(1, (int) Math.ceil(1.0 / unitNow - 1e-9));
	}

	/** Interesse atual da guilda nesse item (1 = total, cai conforme ela compra no dia). */
	public double saleInterest(ItemDemand.Buyer buyer, int population, int soldToday) {
		double d = this.saleCapacity(buyer, population);
		return d / (d + soldToday);
	}

	// ---- Dividendos ----

	/** Dividendo exato por ação que a empresa pagaria com o movimento de hoje (por dentro tem decimais). */
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

	/** Quantas ações rendem 1 esmeralda de dividendo hoje (0 = não paga). */
	public static int sharesPerDividendEmerald(double dividendPerShare) {
		return dividendPerShare <= 0 ? 0 : Math.max(1, (int) Math.ceil(1.0 / dividendPerShare - 1e-9));
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
		buf.writeBoolean(this.unlimitedTrades);
		buf.writeDouble(this.scarcitySurcharge);
	}

	public static MarketRules read(RegistryFriendlyByteBuf buf) {
		return new MarketRules(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
				buf.readBoolean(), buf.readDouble(), buf.readDouble(), Math.max(1, buf.readVarInt()),
				buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readDouble());
	}
}
