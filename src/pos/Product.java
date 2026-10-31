package pos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class Product implements java.io.Serializable {
    private final String productId;
    private String productName, productCategory;
    private BigDecimal productBasePrice;
    private boolean productIsAvailable;
    private List<RecipeItem> recipeItems;
    private boolean orderSnapshot;
    public Product(String id, String name, String category, BigDecimal price, boolean active, Map<Ingredient, BigDecimal> recipe) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("รหัสสินค้าห้ามว่าง");
        productId = id; updateProduct(name, category, price, active, recipe);
    }
    public String id() { return productId; }
    public String name() { return productName; }
    public String category() { return productCategory; }
    public BigDecimal getSalePrice() { return productBasePrice; }
    public BigDecimal price() { return getSalePrice(); }
    public boolean active() { return productIsAvailable; }
    public List<RecipeItem> recipeItems() { return List.copyOf(recipeItems); }
    public Map<Ingredient, BigDecimal> recipe() {
        Map<Ingredient, BigDecimal> map = new LinkedHashMap<>();
        recipeItems.forEach(i -> map.put(i.ingredient(), i.quantityPerUnit()));
        return Collections.unmodifiableMap(map);
    }
    public BigDecimal calculateCostProduct(LocalDate date) {
        return recipeItems.stream().flatMap(r -> r.ingredient().plan(r.quantityPerUnit(), date).stream())
                .map(StockAllocation::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public void updateProductStatus(boolean available) {
        if (orderSnapshot) throw new IllegalArgumentException("ข้อมูลสินค้าในออร์เดอร์เป็นสำเนาอ่านอย่างเดียว");
        productIsAvailable = available;
    }
    Product snapshotForOrder() {
        Product copy = new Product(productId, productName, productCategory, productBasePrice, productIsAvailable, recipe());
        copy.orderSnapshot = true;
        return copy;
    }
    public void updateProduct(String name, String category, BigDecimal price, boolean available, Map<Ingredient, BigDecimal> recipe) {
        if (orderSnapshot) throw new IllegalArgumentException("ข้อมูลสินค้าในออร์เดอร์เป็นสำเนาอ่านอย่างเดียว");
        if (name == null || name.isBlank() || category == null || category.isBlank()) throw new IllegalArgumentException("ชื่อและหมวดสินค้าห้ามว่าง");
        Ingredient.positive(price);
        BigDecimal money = price.setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (recipe == null || recipe.isEmpty()) throw new IllegalArgumentException("ต้องมีสูตรวัตถุดิบ");
        List<RecipeItem> items = new ArrayList<>();
        recipe.forEach((i, q) -> items.add(new RecipeItem(i, q)));
        items.sort(Comparator.comparing(r -> r.ingredient().id()));
        productName = name.trim(); productCategory = category.trim(); productBasePrice = money;
        productIsAvailable = available; recipeItems = List.copyOf(items);
    }
    public void update(String name, BigDecimal price, boolean active, Map<Ingredient, BigDecimal> recipe) {
        updateProduct(name, productCategory, price, active, recipe);
    }
}
