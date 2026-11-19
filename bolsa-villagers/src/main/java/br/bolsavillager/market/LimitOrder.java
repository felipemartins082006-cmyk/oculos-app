package br.bolsavillager.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/**
 * Ordem programada: fica guardada e é executada sozinha num pregão quando o preço chega no alvo,
 * mesmo com o jogador offline. O alvo é um número inteiro de esmeraldas.
 *
 * <p>Ordens de compra guardam as esmeraldas na hora em que são criadas ({@link #reserved}): assim a
 * compra pode acontecer com o jogador offline. Se cancelar, as esmeraldas voltam; se a compra sair mais
 * barata, o troco volta.
 */
public record LimitOrder(Type type, String stockId, int quantity, int target, int reserved) {
	public static final int MAX_PER_PLAYER = 6;

	public static final Codec<LimitOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
			Type.CODEC.fieldOf("type").forGetter(LimitOrder::type),
			Codec.STRING.fieldOf("stock").forGetter(LimitOrder::stockId),
			Codec.INT.fieldOf("quantity").forGetter(LimitOrder::quantity),
			// versões antigas guardavam o alvo com decimais
			Codec.DOUBLE.fieldOf("target").xmap(d -> Math.max(1, (int) Math.round(d)), Integer::doubleValue)
					.forGetter(LimitOrder::target),
			Codec.INT.optionalFieldOf("reserved", 0).forGetter(LimitOrder::reserved)
	).apply(i, LimitOrder::new));

	public enum Type implements StringRepresentable {
		/** Compra quando o preço cair até o alvo (ou abaixo). */
		BUY_BELOW("buy_below"),
		/** Vende quando o preço subir até o alvo (ou acima): realizar lucro. */
		SELL_ABOVE("sell_above"),
		/** Vende quando o preço cair até o alvo: limitar o prejuízo. */
		STOP_LOSS("stop_loss");

		public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);
		private final String name;

		Type(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public String translationKey() {
			return "bolsavillager.order." + this.name;
		}

		public boolean isBuy() {
			return this == BUY_BELOW;
		}

		/** Compara com a cotação mostrada (inteira), que é o que o jogador vê. */
		public boolean triggered(double price, int target) {
			int shown = MarketRules.shownPrice(price);
			return switch (this) {
				case BUY_BELOW, STOP_LOSS -> shown <= target;
				case SELL_ABOVE -> shown >= target;
			};
		}

		public Type next() {
			return values()[(this.ordinal() + 1) % values().length];
		}

		/** Alvo inicial sugerido a partir da cotação atual. */
		public int defaultTarget(int shownPrice) {
			return switch (this) {
				case BUY_BELOW -> Math.max(1, shownPrice - 1);
				case SELL_ABOVE -> shownPrice + 1;
				case STOP_LOSS -> Math.max(1, shownPrice - 2);
			};
		}
	}

	public StockDef stock() {
		return StockDef.byId(this.stockId);
	}
}
