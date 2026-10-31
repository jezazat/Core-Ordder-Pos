package pos;

import java.math.BigDecimal;
import java.util.Map;

public record Employee(String employeeId, String employeeName, String employeeRole) implements java.io.Serializable {
    public Employee {
        if (employeeId == null || employeeId.isBlank() || employeeName == null || employeeName.isBlank()
                || employeeRole == null || employeeRole.isBlank()) throw new IllegalArgumentException("ข้อมูลพนักงานห้ามว่าง");
    }
    public String id() { return employeeId; }
    public String name() { return employeeName; }
    public String role() { return employeeRole; }
    public Order receiveEmployeeProduct(PosSystem system, Customer customer) { return customer.customerOrder(system); }
    public void recordEmployeeConfirmation(PosSystem system, Order order, Order.Payment method,
                                          BigDecimal amount, boolean qrConfirmed) {
        order.customer().customerPayment(system, order, method, amount, qrConfirmed);
    }
    public void prepareEmployeeProduct(PosSystem system, Order order) { order.updateOrderStatus(system, Order.Status.PREPARING); }
    public void finishEmployeeProduct(PosSystem system, Order order) { order.updateOrderStatus(system, Order.Status.READY); }
    public void deliverEmployeeProduct(PosSystem system, Order order) { order.updateOrderStatus(system, Order.Status.DELIVERED); }
    public Product saveEmployeeProduct(PosSystem system, String name, String category, BigDecimal price,
                                       boolean available, Map<Ingredient, BigDecimal> recipe) {
        return system.addProduct(name, category, price, available, recipe);
    }
}
