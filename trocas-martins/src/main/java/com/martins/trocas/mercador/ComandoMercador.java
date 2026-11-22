package com.martins.trocas.mercador;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * /mercador preco                 -> quanto o Mercador paga pelo item na sua mão
 * /mercador preco <item> [qtd]    -> quanto o Mercador paga por um item qualquer
 * /mercador recarregar            -> relê config/trocasmartins.json (operadores)
 */
public final class ComandoMercador {
	private ComandoMercador() {
	}

	public static void registrar(CommandDispatcher<CommandSourceStack> d, CommandBuildContext acesso, Commands.CommandSelection env) {
		d.register(Commands.literal("mercador")
			.then(Commands.literal("preco")
				.executes(ctx -> mostrar(ctx, ctx.getSource().getPlayerOrException().getMainHandItem()))
				.then(Commands.argument("item", ItemArgument.item(acesso))
					.executes(ctx -> mostrar(ctx, ItemArgument.getItem(ctx, "item").createItemStack(1)))
					.then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 128))
						.executes(ctx -> mostrar(ctx, ItemArgument.getItem(ctx, "item")
							.createItemStack(IntegerArgumentType.getInteger(ctx, "quantidade")))))))
			.then(Commands.literal("recarregar")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					MercadorConfig.carregar();
					PrecoMercador.recalcular(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal("Preços do Mercador recarregados.").withStyle(ChatFormatting.GREEN), true);
					return 1;
				})));
	}

	private static int mostrar(CommandContext<CommandSourceStack> ctx, ItemStack pilha) throws CommandSyntaxException {
		if (pilha.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Segure um item na mão ou informe um item: /mercador preco <item> [quantidade]"));
			return 0;
		}
		Cotacao c = PrecoMercador.cotar(List.of(pilha), MercadorConfig.atual.multiplicador_global);
		Component nome = pilha.getHoverName();
		Component msg = switch (c.status()) {
			case NAO_COMPRA -> Component.literal("O Mercador não compra ").append(nome).append(".").withStyle(ChatFormatting.RED);
			case INSUFICIENTE -> Component.literal(c.total() + "x ").append(nome)
				.append(" não vale 1 esmeralda. Faltam " + c.faltam() + " itens.").withStyle(ChatFormatting.RED);
			case OK -> Component.literal(c.usados() + "x ").append(nome)
				.append(" = " + c.esmeraldas() + " esmeralda(s)").withStyle(ChatFormatting.GREEN);
			default -> Component.literal("Nada para avaliar.");
		};
		ctx.getSource().sendSuccess(() -> msg, false);
		ctx.getSource().sendSuccess(() -> Component.literal(Taxa.descrever(c)).withStyle(ChatFormatting.GRAY), false);
		return c.esmeraldas();
	}
}
