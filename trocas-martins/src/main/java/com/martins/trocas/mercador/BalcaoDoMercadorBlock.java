package com.martins.trocas.mercador;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;

/** Bloco de trabalho do Mercador: transforma um aldeão desempregado em Mercador. */
public class BalcaoDoMercadorBlock extends HorizontalFacingBlock {
	public static final MapCodec<BalcaoDoMercadorBlock> CODEC = createCodec(BalcaoDoMercadorBlock::new);

	public BalcaoDoMercadorBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}
}
