package br.bolsavillager.market;

/**
 * Substitui o "esgotado" (X vermelho) das trocas: a troca nunca trava. Depois que acaba o estoque
 * normal, cada troca extra cobra um ágio de escassez sobre o preço base, até o villager repor o estoque.
 *
 * <p>Valores estáticos porque são lidos dentro do próprio MerchantOffer (servidor e cliente).
 * O servidor define pela config; o cliente recebe do servidor junto com as cotações.
 */
public final class TradeLimits {
	/** true = trocas nunca ficam bloqueadas. */
	public static volatile boolean unlimited = true;
	/** Ágio por troca além do estoque normal, em fração do preço base (0.25 = +25% a cada troca extra). */
	public static volatile double surchargePerExtraTrade = 0.25;

	private TradeLimits() {
	}

	/** Quantas trocas já passaram do estoque normal (0 = ainda dentro do estoque). */
	public static int extraTrades(int uses, int maxUses) {
		return Math.max(0, uses - maxUses + 1);
	}

	/** Itens a mais cobrados pela escassez. */
	public static int surcharge(int baseCount, int uses, int maxUses) {
		int extra = extraTrades(uses, maxUses);
		if (!unlimited || extra <= 0 || surchargePerExtraTrade <= 0) {
			return 0;
		}
		return Math.max(1, (int) Math.ceil(baseCount * surchargePerExtraTrade * extra));
	}
}
