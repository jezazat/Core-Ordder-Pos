package pos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class Ingredient implements java.io.Serializable {
    public record Adjustment(LocalDate date, String lotId, BigDecimal quantity, String reason) implements java.io.Serializable {}
    private final String ingredientId, ingredientName, ingredientUnit;
    private final BigDecimal ingredientNormalStock;
    private final List<IngredientLot> lots = new ArrayList<>();
    private final List<Adjustment> adjustments = new ArrayList<>();
    public Ingredient(String id, String name, String unit, BigDecimal normalStock) {
        if (id == null || id.isBlank() || name == null || name.isBlank() || unit == null || unit.isBlank())
            throw new IllegalArgumentException("ข้อมูลวัตถุดิบห้ามว่าง");
        positive(normalStock);
        ingredientId = id; ingredientName = name; ingredientUnit = unit; ingredientNormalStock = normalStock;
    }
    static void positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("จำนวนต้องมากกว่า 0");
    }
    public String id() { return ingredientId; }
    public String name() { return ingredientName; }
    public String unit() { return ingredientUnit; }
    public BigDecimal normalStock() { return ingredientNormalStock; }
    public BigDecimal ingredientReorderPoint() { return ingredientNormalStock.multiply(new BigDecimal("0.5")); }
    public List<IngredientLot> lots() { return List.copyOf(lots); }
    public List<Adjustment> adjustments() { return List.copyOf(adjustments); }
    public void addIngredient(String lotId, LocalDate received, LocalDate expiry,
                              BigDecimal quantity, BigDecimal costPerUnit, String reason) {
        positive(quantity);
        if (costPerUnit == null || costPerUnit.signum() < 0) throw new IllegalArgumentException("ต้นทุนต้องไม่ติดลบ");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("ต้องระบุเหตุผลรับเข้า");
        if (lotId == null || lotId.isBlank() || lots.stream().anyMatch(l -> l.id().equals(lotId)))
            throw new IllegalArgumentException("รหัสล็อตว่างหรือซ้ำ");
        if (received == null || expiry == null || expiry.isBefore(received))
            throw new IllegalArgumentException("วันหมดอายุต้องไม่ก่อนวันรับเข้า");
        lots.add(new IngredientLot(lotId, received, expiry, quantity, costPerUnit, reason));
        lots.sort(Comparator.comparing(IngredientLot::received));
    }
    public void receive(String id, LocalDate received, LocalDate expiry, BigDecimal quantity) {
        addIngredient(id, received, expiry, quantity, BigDecimal.ZERO, "ข้อมูลทดสอบต้นทุน 0");
    }
    public BigDecimal getAvailableQuantityIngredient(LocalDate date) {
        return lots.stream().filter(l -> !l.expiry().isBefore(date) && !l.received().isAfter(date))
                .map(IngredientLot::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal usable(LocalDate date) { return getAvailableQuantityIngredient(date); }
    public boolean isLowIngredient(LocalDate date) { return usable(date).compareTo(ingredientReorderPoint()) < 0; }
    public boolean low(LocalDate date) { return isLowIngredient(date); }
    public boolean isNearExpiryIngredient(LocalDate date, int daysAhead) {
        if (daysAhead < 0) throw new IllegalArgumentException("จำนวนวันต้องไม่ติดลบ");
        return lots.stream().anyMatch(l -> l.quantity().signum() > 0 && !l.received().isAfter(date)
                && !l.expiry().isBefore(date) && !l.expiry().isAfter(date.plusDays(daysAhead)));
    }
    public List<String> alerts(LocalDate date) {
        List<String> result = new ArrayList<>();
        if (isLowIngredient(date)) result.add("เหลือน้อย: " + name() + " " + usable(date).toPlainString() + " " + unit());
        for (IngredientLot lot : lots) if (lot.quantity().signum() > 0 && !lot.received().isAfter(date)) {
            long days = ChronoUnit.DAYS.between(date, lot.expiry());
            if (days < 0) result.add("หมดอายุ: " + name() + " ล็อต " + lot.id() + " (" + lot.expiry() + ")");
            else if (days <= 3) result.add("ใกล้หมดอายุ: " + name() + " ล็อต " + lot.id() + " เหลือ " + days + " วัน");
        }
        return result;
    }
    List<StockAllocation> plan(BigDecimal quantity, LocalDate date) { return plan(quantity, date, new HashMap<>()); }
    List<StockAllocation> plan(BigDecimal quantity, LocalDate date, Map<IngredientLot, BigDecimal> reserved) {
        positive(quantity);
        BigDecimal remaining = quantity;
        List<StockAllocation> result = new ArrayList<>();
        for (IngredientLot lot : lots) {
            if (lot.expiry().isBefore(date) || lot.received().isAfter(date)) continue;
            BigDecimal available = lot.quantity().subtract(reserved.getOrDefault(lot, BigDecimal.ZERO));
            if (available.signum() <= 0) continue;
            BigDecimal take = remaining.min(available);
            result.add(new StockAllocation(this, lot, take, lot.costPerUnit()));
            reserved.merge(lot, take, BigDecimal::add);
            remaining = remaining.subtract(take);
            if (remaining.signum() == 0) return result;
        }
        throw new IllegalArgumentException("วัตถุดิบไม่พอ: " + name());
    }
    public void reduceIngredient(String lotId, BigDecimal quantity, String reason, LocalDate date) {
        positive(quantity);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("ต้องระบุเหตุผลการลดสต็อก");
        IngredientLot lot = lots.stream().filter(l -> l.id().equals(lotId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบล็อต"));
        if (lot.quantity().compareTo(quantity) < 0) throw new IllegalArgumentException("จำนวนในล็อตไม่พอ");
        lot.change(quantity.negate());
        adjustments.add(new Adjustment(date, lotId, quantity, reason.trim()));
    }
    public void reduce(String id, BigDecimal quantity, String reason, LocalDate date) { reduceIngredient(id, quantity, reason, date); }
}
