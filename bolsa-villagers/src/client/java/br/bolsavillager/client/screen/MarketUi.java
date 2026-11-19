package br.bolsavillager.client.screen;

import br.bolsavillager.client.ClientMarket;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.net.MarketSyncPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Peças de desenho compartilhadas entre a tela da bolsa e o painel do villager. */
public final class MarketUi {
	public static final int BG = 0xF0101820;
	public static final int BG_SOFT = 0xFF18242E;
	public static final int BORDER = 0xFF1FA35A;
	public static final int GRID = 0x30FFFFFF;
	public static final int TEXT = 0xFFE6EDF3;
	public static final int TEXT_DIM = 0xFF8B98A5;
	public static final int UP = 0xFF3DDC84;
	public static final int DOWN = 0xFFFF5C5C;
	public static final int FLAT = 0xFFB0B8C0;
	public static final int ACCENT = 0xFF4BE08A;
	public static final int GOLD = 0xFFFFD166;
	public static final String EMERALD = "◆";

	private MarketUi() {
	}

	/** Número no formato brasileiro (vírgula decimal). */
	public static String money(double value) {
		return String.format(Locale.ROOT, "%.2f", value).replace('.', ',');
	}

	public static String percent(double value) {
		return String.format(Locale.ROOT, "%+.2f%%", value).replace('.', ',');
	}

	public static String percent0(double value) {
		return String.format(Locale.ROOT, "%+.0f%%", value);
	}

	public static int changeColor(double change) {
		return change > 0.005 ? UP : change < -0.005 ? DOWN : FLAT;
	}

	public static String arrow(double change) {
		return change > 0.005 ? "▲" : change < -0.005 ? "▼" : "■";
	}

	public static Component name(StockDef def) {
		return Component.translatable(def.translationKey());
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, BG);
		g.outline(x, y, w, h, BORDER);
	}

	public static void textRight(GuiGraphicsExtractor g, Font font, String text, int right, int y, int color) {
		g.text(font, text, right - font.width(text), y, color, false);
	}

	/**
	 * Gráfico de linha com área preenchida, desenhado com retângulos de 1 px
	 * (a GUI do jogo não tem primitiva de linha).
	 */
	public static void chart(GuiGraphicsExtractor g, float[] values, int x, int y, int w, int h, boolean grid) {
		g.fill(x, y, x + w, y + h, 0x60000000);
		if (grid) {
			for (int i = 1; i < 4; i++) {
				g.horizontalLine(x, x + w - 1, y + h * i / 4, GRID);
			}
		}
		if (values.length < 2) {
			return;
		}
		float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
		for (float v : values) {
			min = Math.min(min, v);
			max = Math.max(max, v);
		}
		float range = max - min;
		if (range < 1e-4F) {
			range = Math.max(1e-4F, max * 0.02F);
			min -= range / 2;
		}
		// margem vertical
		float pad = range * 0.1F;
		min -= pad;
		range += pad * 2;

		boolean up = values[values.length - 1] >= values[0];
		int line = up ? UP : DOWN;
		int area = (up ? UP : DOWN) & 0x00FFFFFF | 0x30000000;

		int prevY = -1;
		for (int px = 0; px < w; px++) {
			float t = (float) px / (w - 1) * (values.length - 1);
			int i0 = (int) Math.floor(t);
			int i1 = Math.min(values.length - 1, i0 + 1);
			float v = values[i0] + (values[i1] - values[i0]) * (t - i0);
			int py = y + h - 1 - Math.round((v - min) / range * (h - 1));
			g.fill(x + px, py + 1, x + px + 1, y + h, area);
			if (prevY < 0) {
				prevY = py;
			}
			int top = Math.min(prevY, py);
			int bottom = Math.max(prevY, py);
			g.fill(x + px, top, x + px + 1, bottom + 1, line);
			prevY = py;
		}
	}

	/** Linha compacta de cotação: "FAZD3 12.34 ▲ +1.20%". Retorna a largura usada. */
	public static int quote(GuiGraphicsExtractor g, Font font, StockDef def, int x, int y) {
		MarketSyncPayload.StockView view = ClientMarket.get(def);
		if (view == null) {
			g.text(font, def.ticker + " …", x, y, TEXT_DIM, false);
			return font.width(def.ticker + " …");
		}
		double change = ClientMarket.changePercent(view);
		String price = " " + money(view.price()) + EMERALD + " ";
		String delta = arrow(change) + " " + percent(change);
		int cx = x;
		g.text(font, def.ticker, cx, y, ACCENT, false);
		cx += font.width(def.ticker);
		g.text(font, price, cx, y, TEXT, false);
		cx += font.width(price);
		g.text(font, delta, cx, y, changeColor(change), false);
		return cx + font.width(delta) - x;
	}
}
