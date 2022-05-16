package me.logwet.sar.firstenchant;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;

public class EnchantmentSimulator {
    private static final int BASE_ENCHANTMENT_SEED = 0;

    private EnchantmentSimulator() {}

    private static List<SimpleEnchantmentInstance> getEnchantmentList(
            ItemStack itemStack, int enchantSlot, int level) {
        Random random = new Random();
        random.setSeed(BASE_ENCHANTMENT_SEED + enchantSlot);

        List<SimpleEnchantmentInstance> list =
                EnchantmentHelper.selectEnchantment(random, itemStack, level, false).stream()
                        .map(
                                enchantmentInstance ->
                                        new SimpleEnchantmentInstance(
                                                enchantmentInstance.enchantment,
                                                enchantmentInstance.level))
                        .collect(Collectors.toList());
        if (itemStack.getItem() == Items.BOOK && list.size() > 1) {
            list.remove(random.nextInt(list.size()));
        }

        return list;
    }

    private static List<EnchantmentOutcome> simulateForItemStack(ItemStack itemStack) {
        Random random = new Random();
        int[] costs;
        List<EnchantmentOutcome> enchantmentOutcomes = new ArrayList<>();

        for (int bookshelves = 0; bookshelves <= 15; ++bookshelves) {
            random.setSeed(BASE_ENCHANTMENT_SEED);

            costs = new int[3];

            for (int id = 0; id < 3; ++id) {
                costs[id] =
                        EnchantmentHelper.getEnchantmentCost(random, id, bookshelves, itemStack);
                if (costs[id] < id + 1) {
                    costs[id] = 0;
                }
            }

            for (int id = 0; id < 3; ++id) {
                if (costs[id] > 0) {
                    List<SimpleEnchantmentInstance> enchantments =
                            getEnchantmentList(itemStack, id, costs[id]);
                    if (Objects.nonNull(enchantments) && !enchantments.isEmpty()) {
                        SimpleEnchantmentInstance displayEnchantment =
                                enchantments.get(random.nextInt(enchantments.size()));
                        enchantmentOutcomes.add(
                                new EnchantmentOutcome(
                                        itemStack,
                                        bookshelves,
                                        id,
                                        displayEnchantment,
                                        enchantments,
                                        costs[id]));
                    }
                }
            }
        }

        return enchantmentOutcomes;
    }

    private static Map<String, Map<SimpleEnchantmentInstance, List<EnchantmentOutcome>>>
            simulateOnAllItems() {
        Map<String, Map<SimpleEnchantmentInstance, List<EnchantmentOutcome>>> data =
                new HashMap<>();

        Registry.ITEM.stream()
                .map(Item::getDefaultInstance)
                .filter(ItemStack::isEnchantable)
                .map(EnchantmentSimulator::simulateForItemStack)
                .forEachOrdered(
                        enchantmentOutcomes -> {
                            if (!enchantmentOutcomes.isEmpty()) {
                                ItemStack itemStack = enchantmentOutcomes.get(0).itemStack;
                                String itemName =
                                        itemStack.getItem().getName(itemStack).getString();

                                Map<SimpleEnchantmentInstance, List<EnchantmentOutcome>>
                                        enchantmentInstanceMap = new HashMap<>();

                                for (EnchantmentOutcome enchantmentOutcome : enchantmentOutcomes) {
                                    for (SimpleEnchantmentInstance simpleEnchantmentInstance :
                                            enchantmentOutcome.enchantments) {
                                        List<EnchantmentOutcome> enchantmentOutcomeList =
                                                enchantmentInstanceMap.get(
                                                        simpleEnchantmentInstance);

                                        if (Objects.isNull(enchantmentOutcomeList)) {
                                            enchantmentOutcomeList = new ArrayList<>();
                                        }

                                        enchantmentOutcomeList.add(enchantmentOutcome);

                                        enchantmentInstanceMap.put(
                                                simpleEnchantmentInstance, enchantmentOutcomeList);
                                    }
                                }

                                data.put(itemName, enchantmentInstanceMap);
                            }
                        });

        return data;
    }

    private static void writeCell(WritableSheet sheet, int x, int y, String content) {
        Label label = new Label(x, y, content);
        try {
            sheet.addCell(label);
        } catch (WriteException e) {
            e.printStackTrace();
        }
    }

    private static void writeCell(WritableSheet sheet, int x, int y, int content) {
        Number number = new Number(x, y, content);
        try {
            sheet.addCell(number);
        } catch (WriteException e) {
            e.printStackTrace();
        }
    }

    private static void writeSheet(
            WritableSheet sheet,
            String name,
            Map<SimpleEnchantmentInstance, List<EnchantmentOutcome>> data) {
        sheet.setColumnView(0, 20);

        int y = 0;

        List<SimpleEnchantmentInstance> keySet = Lists.newArrayList(data.keySet());
        Collections.sort(keySet);

        for (SimpleEnchantmentInstance targetEnchantment : keySet) {
            List<EnchantmentOutcome> enchantmentOutcomes = data.get(targetEnchantment);
            writeCell(
                    sheet,
                    0,
                    y,
                    targetEnchantment.enchantment.getFullname(targetEnchantment.level).getString());

            int x = 1;
            for (EnchantmentOutcome enchantmentOutcome : enchantmentOutcomes) {
                writeCell(
                        sheet,
                        x,
                        y,
                        enchantmentOutcome.bookshelves + ", " + enchantmentOutcome.cost + ", " + (enchantmentOutcome.id + 1));
                x++;
            }

            y++;
        }
    }

    public static void genData() {
        Map<String, Map<SimpleEnchantmentInstance, List<EnchantmentOutcome>>> data =
                simulateOnAllItems();

        File file = new File(Paths.get("").toAbsolutePath().toFile(), "data.xls").getAbsoluteFile();
        if (file.exists()) {
            file.delete();
        }

        List<String> keySet = Lists.newArrayList(data.keySet());
        Collections.sort(keySet);

        try {
            WritableWorkbook workbook = Workbook.createWorkbook(file);

            int i = 0;
            for (String itemName : keySet) {
                writeSheet(workbook.createSheet(itemName, i++), itemName, data.get(itemName));
            }

            workbook.write();
            workbook.close();
        } catch (IOException | WriteException e) {
            e.printStackTrace();
        }
    }

    static class EnchantmentOutcome {
        final ItemStack itemStack;
        final int bookshelves;
        final int id;
        final SimpleEnchantmentInstance displayEnchantment;
        final List<SimpleEnchantmentInstance> enchantments;
        final int cost;

        EnchantmentOutcome(
                ItemStack itemStack,
                int bookshelves,
                int id,
                SimpleEnchantmentInstance displayEnchantment,
                List<SimpleEnchantmentInstance> enchantments,
                int cost) {
            this.itemStack = itemStack;
            this.bookshelves = bookshelves;
            this.id = id;
            this.displayEnchantment = displayEnchantment;
            this.enchantments = enchantments;
            this.cost = cost;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            EnchantmentOutcome that = (EnchantmentOutcome) o;
            return bookshelves == that.bookshelves
                    && id == that.id
                    && com.google.common.base.Objects.equal(
                            itemStack.getItem(), that.itemStack.getItem());
        }

        @Override
        public int hashCode() {
            return com.google.common.base.Objects.hashCode(itemStack.getItem(), bookshelves, id);
        }
    }

    static class SimpleEnchantmentInstance implements Comparable<SimpleEnchantmentInstance> {
        final Enchantment enchantment;
        final int level;

        SimpleEnchantmentInstance(Enchantment enchantment, int level) {
            this.enchantment = enchantment;
            this.level = level;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SimpleEnchantmentInstance that = (SimpleEnchantmentInstance) o;
            return level == that.level
                    && com.google.common.base.Objects.equal(enchantment, that.enchantment);
        }

        @Override
        public int hashCode() {
            return com.google.common.base.Objects.hashCode(enchantment, level);
        }

        @Override
        public int compareTo(@NotNull EnchantmentSimulator.SimpleEnchantmentInstance o) {
            return this.enchantment
                    .getFullname(this.level)
                    .toString()
                    .compareTo(o.enchantment.getFullname(o.level).toString());
        }
    }
}
