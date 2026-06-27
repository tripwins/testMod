package dev.prisonsutils.client.pv;

import dev.prisonsutils.client.pv.PvManager.Vault;
import dev.prisonsutils.client.pv.PvManager.VaultItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

/** Rebuilds {@link ItemStack}s from cached NBT (faithful name/lore/enchants) and snapshots live ones. */
public final class PvItems {
    private PvItems() {}

    /** Reconstructs an {@link ItemStack} from a {@code {i:<stack>}} SNBT string; EMPTY on any failure. */
    public static ItemStack fromNbt(RegistryOps<NbtElement> ops, String nbt) {
        if (nbt == null || nbt.isEmpty()) return ItemStack.EMPTY;
        try {
            NbtCompound c = StringNbtReader.readCompound(nbt);
            return c.get("i", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /** Serializes a stack to the {@code {i:<stack>}} SNBT string {@link #fromNbt} reads back. */
    public static String encode(RegistryOps<NbtElement> ops, ItemStack stack) {
        NbtCompound c = new NbtCompound();
        c.put("i", ItemStack.CODEC, ops, stack);
        return c.toString();
    }

    public static ItemStack toStack(RegistryOps<NbtElement> ops, VaultItem it) {
        return fromNbt(ops, it.nbt);
    }

    public static Map<Integer, ItemStack> stacksOf(Vault v) {
        Map<Integer, ItemStack> map = new HashMap<>();
        RegistryOps<NbtElement> ops = ops();
        if (ops == null) return map;
        for (VaultItem it : v.items) {
            ItemStack st = toStack(ops, it);
            if (!st.isEmpty()) map.put(it.slot, st);
        }
        return map;
    }

    /** Snapshots the non-empty container slots (the vault grid, not the player inventory) to SNBT. */
    public static List<VaultItem> snapshot(GenericContainerScreenHandler gc) {
        List<VaultItem> items = new ArrayList<>();
        RegistryOps<NbtElement> ops = ops();
        if (ops == null) return items;
        int container = gc.getRows() * 9;
        for (int i = 0; i < container && i < gc.slots.size(); i++) {
            Slot slot = gc.getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) continue;
            try {
                items.add(new VaultItem(i, encode(ops, stack)));
            } catch (Exception ignored) {}
        }
        return items;
    }

    public static RegistryOps<NbtElement> ops() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        return RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());
    }
}
