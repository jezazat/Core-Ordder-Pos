package pos;

import java.time.LocalDateTime;

public record PointTransaction(Type type, int points, int balanceAfter,
                               LocalDateTime dateTime, String orderId, String reason) implements java.io.Serializable {
    public enum Type { INITIAL, EARN, REDEEM, RETURN, ADJUSTMENT }
}
