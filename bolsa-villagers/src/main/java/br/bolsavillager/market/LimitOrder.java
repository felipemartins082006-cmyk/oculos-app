package br.bolsavillager.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/**
 * Ordem programada: fica guardada e é executada sozinha num pregão quando o preço chega no alvo,
 * mesmo com o jogador offline. Compras usam só o saldo da corretora.
 */
public record LimitOrder(Type type, String stockId, int quantity, double target) {
	public static final int MAX_PER_PLAYER = 6;

	public static final Codec<LimitOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
			Type.CODEC.fieldOf("type").forGetter(LimitOrder::type),
			Codec.STRING.fieldOf("stock").forGetter(LimitOrder::stockId),
			Codec.INT.fieldOf("quantity").forGetter(LimitOrder::quantity),
			Codec.DOUBLE.fieldOf("target").forGetter(LimitOrder::target)
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

		public boolean triggered(double price, double target) {
			return switch (this) {
				case BUY_BELOW, STOP_LOSS -> price <= target;
				case SELL_ABOVE -> price >= target;
			};
		}

		public Type next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	public StockDef stock() {
		return StockDef.byId(this.stockId);
	}
}
