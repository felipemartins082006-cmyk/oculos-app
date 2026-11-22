package com.martins.trocas.mercador;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * /mercador preco                 -> quanto o Mercador paga pelo item na sua mão
 * /mercador preco <item> [qtd]    -> quanto o Mercador paga por um item qualquer
 * /mercador recarregar            -> relê config/trocasmartins.json (operadores)
 */
public final class ComandoMercador {
	private ComandoMercador() {
	}

	public static void registrar(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess acesso, CommandManager.RegistrationEnvironment env) {
		d.register(CommandManager.literal("mercador")
			.then(CommandManager.literal("preco")
				.executes(ctx -> {
					ItemStack naMao = ctx.getSource().getPlayerOrThrow().getMainHandStack();
					return mostrar(ctx, naMao);
				})
				.then(CommandManager.argument("item", ItemStackArgumentType.itemStack(acesso))
					.executes(ctx -> mostrar(ctx, ItemStackArgumentType.getItemStackArgument(ctx, "item").createStack(1, false)))
					.then(CommandManager.argument("quantidade", IntegerArgumentType.integer(1, 576))
						.executes(ctx -> mostrar(ctx, ItemStackArgumentType.getItemStackArgument(ctx, "item")
							.createStack(IntegerArgumentType.getInteger(ctx, "quantidade"), true))))))
			.then(CommandManager.literal("recarregar")
				.requires(s -> s.hasPermissionLevel(2))
				.executes(ctx -> {
					MercadorConfig.carregar();
					PrecoMercador.recalcular(ctx.getSource().getServer());
					ctx.getSource().sendFeedback(() -> Text.literal("Preços do Mercador recarregados.").formatted(Formatting.GREEN), true);
					return 1;
				})));
	}

	private static int mostrar(CommandContext<ServerCommandSource> ctx, ItemStack pilha) throws CommandSyntaxException {
		if (pilha.isEmpty()) {
			ctx.getSource().sendError(Text.literal("Segure um item na mão ou informe um item: /mercador preco <item> [quantidade]"));
			return 0;
		}
		Cotacao c = PrecoMercador.cotar(List.of(pilha), MercadorConfig.atual.multiplicador_global);
		Text nome = pilha.getName();
		Text msg = switch (c.status()) {
			case NAO_COMPRA -> Text.literal("O Mercador não compra ").append(nome).append(".").formatted(Formatting.RED);
			case INSUFICIENTE -> Text.literal(c.total() + "x ").append(nome)
				.append(" não vale 1 esmeralda. Faltam " + c.faltam() + " itens.").formatted(Formatting.RED);
			case OK -> Text.literal(c.usados() + "x ").append(nome)
				.append(" = " + c.esmeraldas() + " esmeralda(s)").formatted(Formatting.GREEN);
			default -> Text.literal("Nada para avaliar.");
		};
		ctx.getSource().sendFeedback(() -> msg, false);
		ctx.getSource().sendFeedback(() -> Text.literal(Taxa.descrever(c)).formatted(Formatting.GRAY), false);
		ctx.getSource().sendFeedback(() -> Text.literal("(mesmo preço que os aldeões pagam, quando algum aldeão compra esse item)").formatted(Formatting.DARK_GRAY), false);
		return c.esmeraldas();
	}
}
