package me.logwet.sar.firstenchant;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.FileOutputStream;
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
import net.minecraft.SharedConstants;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.Level;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.IndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;

public class EnchantmentSimulator {
    private static final int BASE_ENCHANTMENT_SEED = 0;

    private static final Workbook WORKBOOK;
    private static final IndexedColorMap INDEXED_COLOR_MAP;
    private static final Font DEFAULT_FONT;
    private static final Font BOLD_FONT;
    private static final CellStyle DEFAULT_STYLE;
    private static final CellStyle BOLD_STYLE;

    private static final Map<Integer, CellStyle> COLOUR_STYLE_MAP;

    static {
        WORKBOOK = new XSSFWorkbook();

        INDEXED_COLOR_MAP = new DefaultIndexedColorMap();

        DEFAULT_FONT = WORKBOOK.createFont();
        DEFAULT_FONT.setFontName("Arial");
        DEFAULT_FONT.setFontHeightInPoints((short) 10);

        BOLD_FONT = WORKBOOK.createFont();
        BOLD_FONT.setFontName("Arial");
        BOLD_FONT.setFontHeightInPoints((short) 10);
        BOLD_FONT.setBold(true);

        DEFAULT_STYLE = WORKBOOK.createCellStyle();
        DEFAULT_STYLE.setFont(DEFAULT_FONT);

        BOLD_STYLE = WORKBOOK.createCellStyle();
        BOLD_STYLE.setFont(BOLD_FONT);

        COLOUR_STYLE_MAP = new HashMap<>();
    }

    private EnchantmentSimulator() {}

    private static List<SimpleEnchantmentInstance> getEnchantmentList(
            ItemStack itemStack, int enchantSlot, int level) {
        Random random = new Random();
        random.setSeed(BASE_ENCHANTMENT_SEED + enchantSlot);

        List<SimpleEnchantmentInstance> list =
                EnchantmentHelper.generateEnchantments(random, itemStack, level, false).stream()
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
                        EnchantmentHelper.calculateRequiredExperienceLevel(random, id, bookshelves, itemStack);
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
                .map(Item::getStackForRender)
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

    private static boolean getContrastColour(int r, int g, int b) {
        return MathHelper.sqrt(r * r * 0.241 + g * g * 0.691 + b * b * 0.068) > 130;
    }

    private static CellStyle getStyleByColour(Integer hex) {
        XSSFCellStyle cellStyle = (XSSFCellStyle) COLOUR_STYLE_MAP.get(hex);

        if (Objects.isNull(cellStyle)) {
            int r = ((hex & 0xFF0000) >> 16);
            int g = ((hex & 0xFF00) >> 8);
            int b = (hex & 0xFF);

            XSSFFont font = (XSSFFont) WORKBOOK.createFont();
            font.setFontName("Arial");
            font.setFontHeightInPoints((short) 10);
            font.setColor(
                    getContrastColour(r, g, b)
                            ? IndexedColors.BLACK.getIndex()
                            : IndexedColors.WHITE.getIndex());

            cellStyle = (XSSFCellStyle) WORKBOOK.createCellStyle();
            cellStyle.setFont(font);
            cellStyle.setFillForegroundColor(
                    new XSSFColor(new byte[] {(byte) r, (byte) g, (byte) b}, INDEXED_COLOR_MAP));
            cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            COLOUR_STYLE_MAP.put(hex, cellStyle);
        }

        return cellStyle;
    }

    private static void writeCell(Sheet sheet, int x, int y, String content) {
        writeCell(sheet, x, y, content, DEFAULT_STYLE);
    }

    private static void writeCell(Sheet sheet, int x, int y, String content, int hex) {
        CellStyle cellStyle = getStyleByColour(hex);
        writeCell(sheet, x, y, content, cellStyle);
    }

    private static void writeCell(Sheet sheet, int x, int y, String content, CellStyle cellStyle) {
        Row row = sheet.getRow(y);
        if (Objects.isNull(row)) {
            row = sheet.createRow(y);
        }

        Cell cell = row.getCell(x);
        if (Objects.isNull(cell)) {
            cell = row.createCell(x);
        }

        cell.setCellValue(content);
        cell.setCellStyle(cellStyle);
    }

    private static void writeSheet(
            Sheet sheet,
            String name,
            Map<SimpleEnchantmentInstance, List<EnchantmentOutcome>> data) {
        sheet.setColumnWidth(0, 20 * 256);

        writeCell(sheet, 0, 0, "Enchantment:", BOLD_STYLE);
        writeCell(sheet, 1, 0, "Shelves, Levels, Index", BOLD_STYLE);

        int y = 1;

        List<SimpleEnchantmentInstance> keySet = Lists.newArrayList(data.keySet());
        Collections.sort(keySet);

        for (SimpleEnchantmentInstance targetEnchantment : keySet) {
            List<EnchantmentOutcome> enchantmentOutcomes = data.get(targetEnchantment);
            writeCell(
                    sheet,
                    0,
                    y,
                    targetEnchantment.enchantment.getName(targetEnchantment.level).getString());

            int x = 1;
            for (EnchantmentOutcome enchantmentOutcome : enchantmentOutcomes) {
                int r = (int) Math.round(255 * (enchantmentOutcome.cost - 2) / 28D) << 16;
                int g = (int) Math.round(255 * enchantmentOutcome.bookshelves / 15D) << 8;
                int b = (int) Math.round(255 * enchantmentOutcome.id / 2D);
                int hex = r + g + b;

                writeCell(
                        sheet,
                        x,
                        y,
                        enchantmentOutcome.bookshelves
                                + ", "
                                + enchantmentOutcome.cost
                                + ", "
                                + (enchantmentOutcome.id + 1),
                        getStyleByColour(hex));
                x++;
            }

            y++;
        }
    }

    public static void genData() {
        Map<String, Map<SimpleEnchantmentInstance, List<EnchantmentOutcome>>> data =
                simulateOnAllItems();

        File directory = new File(Paths.get("").toAbsolutePath().toFile(), "enchantments");
        directory.mkdirs();
        File file =
                new File(directory, SharedConstants.getGameVersion().getName() + ".xlsx")
                        .getAbsoluteFile();
        if (file.exists()) {
            file.delete();
        }

        List<String> keySet = Lists.newArrayList(data.keySet());
        Collections.sort(keySet);

        try {

            for (String itemName : keySet) {
                writeSheet(WORKBOOK.createSheet(itemName), itemName, data.get(itemName));
            }

            FileOutputStream fileOutputStream = new FileOutputStream(file);
            WORKBOOK.write(fileOutputStream);
            WORKBOOK.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        FirstEnchant.log(Level.INFO, "Dumped enchant info");
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
                    .getName(this.level)
                    .toString()
                    .compareTo(o.enchantment.getName(o.level).toString());
        }
    }
}
