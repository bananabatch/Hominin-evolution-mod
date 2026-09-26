package dev.hominin.evolution.network;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Things to pick between, for a counter offer or a party: yours and your band's on the left, the other band's on
 * the right, with who holds each; how many can go in a party; and, for a message, what can be said.
 */
public record GoodsPayload(int mode, String band, int intent, String title, String detail, List<ItemStack> left,
        List<String> leftOwners, List<ItemStack> right, List<String> rightOwners, int maxParty, List<String> options)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GoodsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "goods"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GoodsPayload> STREAM_CODEC =
            StreamCodec.ofMember(GoodsPayload::write, GoodsPayload::read);

    private static void stacks(RegistryFriendlyByteBuf buf, List<ItemStack> stacks) {
        buf.writeVarInt(stacks.size());
        for (ItemStack stack : stacks) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
        }
    }

    private static List<ItemStack> stacks(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return list;
    }

    private static void strings(RegistryFriendlyByteBuf buf, List<String> strings) {
        buf.writeVarInt(strings.size());
        strings.forEach(buf::writeUtf);
    }

    private static List<String> strings(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeUtf(band);
        buf.writeVarInt(intent);
        buf.writeUtf(title);
        buf.writeUtf(detail);
        stacks(buf, left);
        strings(buf, leftOwners);
        stacks(buf, right);
        strings(buf, rightOwners);
        buf.writeVarInt(maxParty);
        strings(buf, options);
    }

    private static GoodsPayload read(RegistryFriendlyByteBuf buf) {
        return new GoodsPayload(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readUtf(),
                stacks(buf), strings(buf), stacks(buf), strings(buf), buf.readVarInt(), strings(buf));
    }

    public static void handle(GoodsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.GoodsScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
