package pos;

import java.math.BigDecimal;

public record RecipeItem(Ingredient ingredient, BigDecimal quantityPerUnit) implements java.io.Serializable {
    public RecipeItem {
        if (ingredient == null) throw new IllegalArgumentException("ต้องระบุวัตถุดิบ");
        Ingredient.positive(quantityPerUnit);
    }
}
