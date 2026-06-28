package com.echestmod.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;
import java.util.List;

@Mod(modid = EChestMod.MODID, name = EChestMod.NAME, version = EChestMod.VERSION, clientSideOnly = true)
public class EChestMod {

    public static final String MODID   = "echestmod";
    public static final String NAME    = "EChest Auto Deposit";
    public static final String VERSION = "1.0.0";

    private static final int INTERVAL     = 2400;
    private static final int MAX_PER_SLOT = 32;

    private static final List<Item> VALUABLES = Arrays.asList(
        Item.getItemFromBlock(Blocks.DIAMOND_BLOCK),
        Item.getItemFromBlock(Blocks.IRON_BLOCK),
        Item.getItemFromBlock(Blocks.EMERALD_BLOCK),
        Item.getItemFromBlock(Blocks.GOLD_BLOCK)
    );

    private int tickCount = 0;
    private int step = 0;
    private int waitTick = 0;
    private boolean enabled = false;

    private static KeyBinding startKey;
    private static KeyBinding stopKey;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        startKey = new KeyBinding("Start EChest", KeyConflictContext.IN_GAME, Keyboard.KEY_G, "EChest Mod");
        stopKey  = new KeyBinding("Stop EChest",  KeyConflictContext.IN_GAME, Keyboard.KEY_H, "EChest Mod");
        ClientRegistry.registerKeyBinding(startKey);
        ClientRegistry.registerKeyBinding(stopKey);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        if (startKey.isPressed()) {
            enabled = true;
            step = 1;
            tickCount = 0;
            waitTick = 0;
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("§aEChest Mod ishga tushirildi!"));
        }
        if (stopKey.isPressed()) {
            enabled = false;
            step = 0;
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("§cEChest Mod to'xtatildi!"));
        }

        if (!enabled) return;

        switch (step) {
            case 0:
                tickCount++;
                if (tickCount >= INTERVAL) {
                    tickCount = 0;
                    if (hasValuables(mc)) step = 1;
                }
                break;

            case 1:
                mc.player.sendChatMessage("/team echest");
                mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("§e[1] /team echest yuborildi..."));
                waitTick = 0;
                step = 2;
                break;

            case 2:
                waitTick++;
                if (mc.currentScreen instanceof GuiChest) {
                    GuiChest gui = (GuiChest) mc.currentScreen;
                    ContainerChest container = (ContainerChest) gui.inventorySlots;
                    if (container.getLowerChestInventory().getSizeInventory() == 27) {
                        mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("§e[2] EChest ochildi!"));
                        executeSafeDeposit(mc, container);
                        waitTick = 0;
                        step = 0;
                    }
                }
                if (waitTick > 100) {
                    mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("§c[Xato] EChest ochilmadi!"));
                    waitTick = 0;
                    step = 0;
                }
                break;
        }
    }

    private void executeSafeDeposit(Minecraft mc, ContainerChest container) {
        if (mc.playerController == null || mc.player == null) return;

        for (int invSlot = 0; invSlot < 36; invSlot++) {
            ItemStack invStack = mc.player.inventory.getStackInSlot(invSlot);
            if (invStack.isEmpty() || !VALUABLES.contains(invStack.getItem())) continue;

            // 0-8 hotbar, 9-35 inventory
            int handlerSlot = (invSlot < 9) ? (54 + invSlot) : (27 + (invSlot - 9));
            Item currentItem = invStack.getItem();

            // Kursorga ol
            mc.playerController.windowClick(container.windowId, handlerSlot, 0, ClickType.PICKUP, mc.player);

            for (int es = 0; es < 27; es++) {
                ItemStack esStack = container.getLowerChestInventory().getStackInSlot(es);

                if (esStack.isEmpty()) {
                    ItemStack cursor = container.getInventory().get(container.inventorySlots.size() - 1);
                    if (cursor == null || cursor.isEmpty()) break;
                    int toPut = Math.min(cursor.getCount(), MAX_PER_SLOT);
                    if (toPut == cursor.getCount()) {
                        mc.playerController.windowClick(container.windowId, es, 0, ClickType.PICKUP, mc.player);
                    } else {
                        for (int k = 0; k < toPut; k++) {
                            mc.playerController.windowClick(container.windowId, es, 1, ClickType.PICKUP, mc.player);
                        }
                    }
                } else if (esStack.getItem() == currentItem && esStack.getCount() < MAX_PER_SLOT) {
                    int spaceLeft = MAX_PER_SLOT - esStack.getCount();
                    for (int k = 0; k < spaceLeft; k++) {
                        mc.playerController.windowClick(container.windowId, es, 1, ClickType.PICKUP, mc.player);
                    }
                }
            }

            // Qolganini qaytар
            mc.playerController.windowClick(container.windowId, handlerSlot, 0, ClickType.PICKUP, mc.player);
        }

        // Yop
        if (mc.currentScreen != null) {
            mc.currentScreen.onGuiClosed();
            mc.displayGuiScreen(null);
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("§a§l[Muvaffaqiyatli] Bloklar solindi va oyna yopildi!"));
        }
    }

    private boolean hasValuables(Minecraft mc) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.inventory.getStackInSlot(i);
            if (!s.isEmpty() && VALUABLES.contains(s.getItem())) return true;
        }
        return false;
    }
}
