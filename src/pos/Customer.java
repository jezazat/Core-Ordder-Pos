package pos;

import java.math.BigDecimal;

public class Customer implements java.io.Serializable {
    private final String customerId;
    public Customer(String customerId) {
        if (customerId == null || customerId.isBlank()) throw new IllegalArgumentException("รหัสลูกค้าห้ามว่าง");
        this.customerId = customerId;
    }
    public String id() { return customerId; }
    public String name() { return "ลูกค้าทั่วไป"; }
    public Member customerRegister(PosSystem system, String name, String phone) {
        return system.registerCustomer(this, name, phone);
    }
    public Order customerOrder(PosSystem system) { return system.createOrder(this); }
    public void customerPayment(PosSystem system, Order order, Order.Payment method,
                                BigDecimal amount, boolean qrConfirmed) {
        if (order.customer() != this) throw new IllegalArgumentException("ออร์เดอร์ไม่ใช่ของลูกค้ารายนี้");
        system.pay(order, method, amount, qrConfirmed);
    }
}
