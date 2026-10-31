package pos;

import java.math.BigDecimal;
import java.util.*;

public class OrderItem implements java.io.Serializable {
    private final Product product;
    private final Product orderProduct;
    private final String productName;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final Map<Ingredient, BigDecimal> recipeSnapshot;
    private List<StockAllocation> stockAllocations = List.of();
    OrderItem(Product product, int quantity) {
        this.product = product; this.orderProduct = product.snapshotForOrder();
        this.productName = orderProduct.name(); this.unitPrice = orderProduct.price(); this.quantity = quantity;
        recipeSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(orderProduct.recipe()));
    }
    public Product product() { return product; }
    public Product orderProduct() { return orderProduct; }
    public String productId() { return product.id(); }
    public String name() { return productName; }
    public BigDecimal price() { return unitPrice; }
    public int quantity() { return quantity; }
    public Map<Ingredient, BigDecimal> recipe() { return recipeSnapshot; }
    public BigDecimal total() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    public List<StockAllocation> allocations() { return List.copyOf(stockAllocations); }
    public BigDecimal totalCost() { return stockAllocations.stream().map(StockAllocation::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add); }
    void recordAllocations(List<StockAllocation> allocations) { stockAllocations = List.copyOf(allocations); }
}
