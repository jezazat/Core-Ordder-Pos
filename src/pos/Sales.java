package pos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class Sales {
    public record Summary(long paidOrders, long cancelledPaidOrders, long deliveredOrders,
                          BigDecimal gross, BigDecimal discounts, BigDecimal payments,
                          BigDecimal refunds, BigDecimal netSales, BigDecimal totalCost, BigDecimal profit) {}
    private final String salesId;
    private final LocalDate salesStartDate, salesEndDate;
    private List<Order> orders;
    private BigDecimal salesTotal, salesTotalCost, salesProfit;
    private int salesOrderCount;
    private Summary summary;
    public Sales(String id, LocalDate start, LocalDate end, List<Order> orders) {
        if (id == null || id.isBlank() || start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("รหัสรายงานหรือช่วงวันที่ไม่ถูกต้อง");
        salesId = id; salesStartDate = start; salesEndDate = end; calculateSales(orders);
    }
    public String salesId() { return salesId; }
    public LocalDate salesStartDate() { return salesStartDate; }
    public LocalDate salesEndDate() { return salesEndDate; }
    public List<Order> orders() { return List.copyOf(orders); }
    private boolean inRange(LocalDate date) { return !date.isBefore(salesStartDate) && !date.isAfter(salesEndDate); }
    public BigDecimal calculateSales(List<Order> selectedOrders) {
        if (selectedOrders == null || selectedOrders.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("รายการออร์เดอร์ไม่ถูกต้อง");
        orders = List.copyOf(selectedOrders);
        int count = 0, cancelled = 0, delivered = 0;
        BigDecimal gross = BigDecimal.ZERO, discounts = BigDecimal.ZERO, payments = BigDecimal.ZERO,
                refunds = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        for (Order order : orders) {
            if (order.payment() == null) continue;
            if (inRange(order.paidDateTime().toLocalDate())) {
                count++; if (order.status() == Order.Status.DELIVERED) delivered++;
                gross = gross.add(order.subtotal()); discounts = discounts.add(order.discount());
                payments = payments.add(order.paid()); cost = cost.add(order.totalCost());
            }
            if (order.status() == Order.Status.CANCELLED && inRange(order.cancelledDateTime().toLocalDate())) {
                cancelled++; refunds = refunds.add(order.refund()); cost = cost.subtract(order.totalCost());
            }
        }
        salesTotal = payments.subtract(refunds); salesTotalCost = cost; salesProfit = salesTotal.subtract(cost);
        salesOrderCount = count;
        summary = new Summary(count, cancelled, delivered, gross, discounts, payments, refunds, salesTotal, cost, salesProfit);
        return salesTotal;
    }
    public BigDecimal calculateSalesProfit() { calculateSales(orders); return salesProfit; }
    public BigDecimal salesTotal() { calculateSales(orders); return salesTotal; }
    public BigDecimal salesTotalCost() { calculateSales(orders); return salesTotalCost; }
    public int salesOrderCount() { calculateSales(orders); return salesOrderCount; }
    public Summary summary() { calculateSales(orders); return summary; }
    public Map<Product, Long> getTopSellingProducts(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("จำนวนอันดับต้องมากกว่า 0");
        Map<Product, Long> quantities = new HashMap<>();
        for (Order order : orders) {
            if (order.payment() == null) continue;
            boolean sale = inRange(order.paidDateTime().toLocalDate());
            boolean refund = order.status() == Order.Status.CANCELLED && inRange(order.cancelledDateTime().toLocalDate());
            int sign = (sale ? 1 : 0) - (refund ? 1 : 0);
            if (sign != 0) for (OrderItem item : order.items())
                quantities.merge(item.product(), (long) sign * item.quantity(), Long::sum);
        }
        LinkedHashMap<Product, Long> result = new LinkedHashMap<>();
        quantities.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Comparator.<Map.Entry<Product, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(e -> e.getKey().id()))
                .limit(limit).forEach(e -> result.put(e.getKey(), e.getValue()));
        return Collections.unmodifiableMap(result);
    }
}
