package dev.hominin.evolution.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * One of us, or near enough: a hominin, chimpanzee or bonobo, lying where it died. Butchered
 * like any carcass, it gives hominin meat, ribs, the brain, and sometimes the skull.
 */
public class HomininCarcassBlock extends CarcassBlock {
    public static final MapCodec<HomininCarcassBlock> CODEC = simpleCodec(HomininCarcassBlock::new);

    public HomininCarcassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
