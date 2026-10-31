package pos;

import java.math.BigDecimal;

public record StockAllocation(Ingredient ingredient, IngredientLot lot, BigDecimal quantity,
                              BigDecimal costPerUnit) implements java.io.Serializable {
    public BigDecimal totalCost() { return quantity.multiply(costPerUnit); }
}
