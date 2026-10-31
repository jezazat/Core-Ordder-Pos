package pos;

import java.math.BigDecimal;
import java.time.LocalDate;

public class IngredientLot implements java.io.Serializable {
    private final String lotId;
    private final LocalDate receivedDate, ingredientExpireDate;
    private final BigDecimal ingredientCostPerUnit;
    private final String receiveReason;
    private BigDecimal quantity;
    IngredientLot(String id, LocalDate received, LocalDate expiry, BigDecimal quantity, BigDecimal cost, String reason) {
        this.lotId = id; this.receivedDate = received; this.ingredientExpireDate = expiry;
        this.quantity = quantity; this.ingredientCostPerUnit = cost;
        this.receiveReason = reason.trim();
    }
    public String id() { return lotId; }
    public LocalDate received() { return receivedDate; }
    public LocalDate expiry() { return ingredientExpireDate; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal costPerUnit() { return ingredientCostPerUnit; }
    public String receiveReason() { return receiveReason; }
    void change(BigDecimal delta) { quantity = quantity.add(delta); }
}
