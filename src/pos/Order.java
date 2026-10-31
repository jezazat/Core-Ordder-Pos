package pos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public class Order implements java.io.Serializable {
    public enum Status {
        WAITING_PAYMENT("รอชำระ"), WAITING_PREPARATION("รอจัดเตรียม"), PREPARING("กำลังจัดเตรียม"),
        READY("เตรียมเสร็จ"), DELIVERED("ส่งมอบ"), CANCELLED("ยกเลิก");
        private final String label;
        Status(String label) { this.label = label; }
        public String toString() { return label; }
    }
    public enum Payment { CASH, QR }
    private final String orderId;
    private final LocalDateTime orderDateTime;
    private LocalDateTime paidDateTime, cancelledDateTime;
    private String cancelReason;
    private final Customer customer;
    private final Employee employee;
    private final List<OrderItem> orderItems = new ArrayList<>();
    private Status orderStatus = Status.WAITING_PAYMENT;
    private int requestedPoints, earnedPoints;
    private BigDecimal paidAmount = BigDecimal.ZERO, refundAmount = BigDecimal.ZERO,
            receivedAmount = BigDecimal.ZERO, changeAmount = BigDecimal.ZERO;
    private Payment payment;
    Order(String id, Customer customer, Employee employee, LocalDateTime created) {
        orderId = id; this.customer = customer; this.employee = employee; orderDateTime = created;
    }
    public String id() { return orderId; }
    public LocalDateTime orderDateTime() { return orderDateTime; }
    public LocalDateTime paidDateTime() { return paidDateTime; }
    public LocalDateTime cancelledDateTime() { return cancelledDateTime; }
    public String cancelReason() { return cancelReason; }
    public Customer customer() { return customer; }
    public Employee employee() { return employee; }
    public Status status() { return orderStatus; }
    public List<OrderItem> items() { return List.copyOf(orderItems); }
    public List<Product> orderProducts() { return orderItems.stream().map(OrderItem::orderProduct).toList(); }
    public int requestedPoints() { return requestedPoints; }
    public int earnedPoints() { return earnedPoints; }
    public BigDecimal paid() { return paidAmount; }
    public BigDecimal refund() { return refundAmount; }
    public BigDecimal received() { return receivedAmount; }
    public BigDecimal change() { return changeAmount; }
    public Payment payment() { return payment; }
    public List<StockAllocation> allocations() { return orderItems.stream().flatMap(i -> i.allocations().stream()).toList(); }
    public BigDecimal orderTotalAmount() { return orderItems.stream().map(OrderItem::total).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public BigDecimal orderDiscountAmount() { return BigDecimal.valueOf(requestedPoints); }
    public BigDecimal orderNetAmount() { return orderTotalAmount().subtract(orderDiscountAmount()); }
    public BigDecimal calculateOrderTotal() { return orderNetAmount(); }
    public BigDecimal totalCost() { return orderItems.stream().map(OrderItem::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public BigDecimal subtotal() { return orderTotalAmount(); }
    public BigDecimal discount() { return orderDiscountAmount(); }
    public BigDecimal net() { return orderNetAmount(); }
    void require(Status expected) {
        if (orderStatus != expected) throw new IllegalArgumentException("สถานะไม่อนุญาต: " + orderStatus);
    }
    public void addOrderItem(Product product, int qty) {
        require(Status.WAITING_PAYMENT);
        if (qty <= 0 || qty > 10000) throw new IllegalArgumentException("จำนวนสินค้าต้องอยู่ระหว่าง 1–10000");
        if (product == null || !product.active()) throw new IllegalArgumentException("สินค้าปิดการขายหรือไม่พบสินค้า");
        orderItems.add(new OrderItem(product, qty));
    }
    public void add(Product product, int quantity) { addOrderItem(product, quantity); }
    public void remove(int index) {
        require(Status.WAITING_PAYMENT);
        if (index < 0 || index >= orderItems.size()) throw new IllegalArgumentException("ไม่พบรายการ");
        if (discount().compareTo(subtotal().subtract(orderItems.get(index).total())) > 0)
            throw new IllegalArgumentException("กรุณาปรับคะแนนก่อนลบรายการ");
        orderItems.remove(index);
    }
    public void applyOrderDiscount(int points) {
        require(Status.WAITING_PAYMENT);
        if (points < 0 || points % 10 != 0) throw new IllegalArgumentException("ใช้คะแนนเป็นชุดละ 10 คะแนน");
        if (points > 0 && !(customer instanceof Member)) throw new IllegalArgumentException("ต้องเป็นสมาชิก");
        if (customer instanceof Member m && points > m.points()) throw new IllegalArgumentException("คะแนนไม่เพียงพอ");
        if (BigDecimal.valueOf(points).compareTo(subtotal()) > 0) throw new IllegalArgumentException("ส่วนลดเกินยอดสินค้า");
        requestedPoints = points;
    }
    public void usePoints(int points) { applyOrderDiscount(points); }
    public void cancelOrder(PosSystem system, String reason) { system.cancel(this, reason); }
    public void updateOrderStatus(PosSystem system, Status newStatus) {
        if (newStatus == Status.PREPARING) system.start(this);
        else if (newStatus == Status.READY) system.ready(this);
        else if (newStatus == Status.DELIVERED) system.deliver(this);
        else throw new IllegalArgumentException("สถานะนี้ต้องผ่านการชำระหรือยกเลิกพร้อมเหตุผล");
    }
    void completePayment(Payment method, BigDecimal tendered, Map<OrderItem, List<StockAllocation>> used, LocalDateTime at) {
        used.forEach(OrderItem::recordAllocations);
        payment = method; paidAmount = net(); receivedAmount = tendered; changeAmount = tendered.subtract(paidAmount);
        paidDateTime = at; orderStatus = Status.WAITING_PREPARATION;
    }
    void transition(Status next) { orderStatus = next; }
    void delivered(int points) { earnedPoints = points; orderStatus = Status.DELIVERED; }
    void cancelled(String reason, LocalDateTime at) {
        refundAmount = paidAmount; cancelReason = reason; cancelledDateTime = at; orderStatus = Status.CANCELLED;
    }
}
