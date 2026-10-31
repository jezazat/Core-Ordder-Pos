package pos;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ModelTests {
    private static int passed;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
    private static BigDecimal b(String v) { return new BigDecimal(v); }
    private static void check(boolean v, String message) { if (!v) throw new AssertionError(message); }
    private static void eq(BigDecimal actual, String expected) { check(actual.compareTo(b(expected)) == 0, actual + " != " + expected); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    private static void test(String name, Runnable action) {
        try { action.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable e) { throw new AssertionError("FAIL " + name, e); }
    }
    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> time;
        private final ZoneId zone;
        MutableClock() { this(new AtomicReference<>(Instant.parse("2026-10-07T05:00:00Z")), ZoneOffset.UTC); }
        MutableClock(AtomicReference<Instant> time, ZoneId zone) { this.time = time; this.zone = zone; }
        public ZoneId getZone() { return zone; }
        public Clock withZone(ZoneId zone) { return new MutableClock(time, zone); }
        public Instant instant() { return time.get(); }
        void nextDay() { time.updateAndGet(t -> t.plus(Duration.ofDays(1))); }
    }
    private static class Fixture {
        final MutableClock clock = new MutableClock();
        final PosSystem s = new PosSystem(clock);
        final Member m = s.register("สมาชิก", "0812345678");
        final Ingredient i = s.addIngredient("ไอศกรีม", "สกู๊ป", b("100"));
        final Product p;
        Fixture() {
            i.addIngredient("old", DAY.minusDays(2), DAY, b("2"), b("3"), "ทดสอบ");
            i.addIngredient("new", DAY.minusDays(1), DAY.plusDays(10), b("98"), b("7"), "ทดสอบ");
            p = s.addProduct("สินค้า", "ไอศกรีม", b("35"), true, Map.of(i, BigDecimal.ONE));
            m.initializeDemoPoints(100, s.now());
        }
        Order order(int qty) { Order o = m.customerOrder(s); o.addOrderItem(p, qty); return o; }
        void pay(Order o) { m.customerPayment(s, o, Order.Payment.CASH, o.net(), true); }
        void deliver(Order o) { pay(o); s.employee().prepareEmployeeProduct(s, o); s.employee().finishEmployeeProduct(s, o); s.employee().deliverEmployeeProduct(s, o); }
    }
    private static String console(PosSystem s, String script) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ConsoleApp(s, new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)), output).run();
        return output.toString(StandardCharsets.UTF_8);
    }
    public static int run() {
        passed = 0;
        test("point history reconciles initial/spend/earn and timestamps", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); o.applyOrderDiscount(10); f.deliver(o);
            List<PointTransaction> history = f.m.getPointHistoryMember();
            check(history.size() == 3 && history.get(0).type() == PointTransaction.Type.INITIAL
                    && history.get(1).type() == PointTransaction.Type.REDEEM && history.get(2).type() == PointTransaction.Type.EARN, "types");
            check(history.stream().mapToInt(PointTransaction::points).sum() == f.m.points() && f.m.points() == 92, "reconciliation");
            check(history.get(1).balanceAfter() == 90 && history.get(2).balanceAfter() == 92
                    && history.get(2).orderId().equals(o.id()) && history.get(2).dateTime().equals(f.s.now()), "audit details");
            rejects(() -> f.s.deliver(o)); check(f.m.getPointHistoryMember().size() == 3, "duplicate history");
        });
        test("cancel audit returns exact points once", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); o.applyOrderDiscount(20); f.pay(o);
            o.cancelOrder(f.s, "เปลี่ยนรายการ"); List<PointTransaction> h = f.m.getPointHistoryMember();
            check(h.size() == 3 && h.get(2).type() == PointTransaction.Type.RETURN && h.get(2).points() == 20
                    && h.get(2).reason().equals("เปลี่ยนรายการ") && f.m.points() == 100, "refund audit");
            rejects(() -> o.cancelOrder(f.s, "ซ้ำ")); check(f.m.getPointHistoryMember().size() == 3, "repeat audit");
        });
        test("failed stock/cash/QR creates no points audit", () -> {
            Fixture f = new Fixture(); Order o = f.order(101); o.applyOrderDiscount(10);
            rejects(() -> f.pay(o)); rejects(() -> f.s.pay(o, Order.Payment.QR, o.net(), false));
            rejects(() -> f.s.pay(o, Order.Payment.CASH, b("1"), true));
            check(f.m.getPointHistoryMember().size() == 1 && f.m.points() == 100 && o.allocations().isEmpty(), "failed audit mutation");
        });
        test("read-only history and zero points are not fake events", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); f.deliver(o);
            check(f.m.getPointHistoryMember().size() == 2, "zero spend event");
            try { f.m.getPointHistoryMember().clear(); throw new AssertionError("mutable history"); }
            catch (UnsupportedOperationException expected) {}
            rejects(() -> f.m.initializeDemoPoints(100, f.s.now()));
            rejects(() -> f.m.returnPointsMember(10, "unknown", "invalid", f.s.now()));
        });
        test("FIFO unit costs allocated across shared order items", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); o.addOrderItem(f.p, 2); f.pay(o);
            eq(o.items().get(0).totalCost(), "3"); eq(o.items().get(1).totalCost(), "10"); eq(o.totalCost(), "13");
            eq(f.i.usable(DAY), "97"); check(o.allocations().size() == 3, "allocation composition");
            eq(f.s.summary().profit(), "92");
        });
        test("cost preview does not reserve or consume lots", () -> {
            Fixture f = new Fixture(); eq(f.p.calculateCostProduct(DAY), "3"); eq(f.i.usable(DAY), "100");
            eq(f.p.calculateCostProduct(DAY.plusDays(1)), "7");
            f.i.reduceIngredient("new", b("98"), "ทดสอบ", DAY);
            rejects(() -> f.p.calculateCostProduct(DAY.plusDays(1))); eq(f.i.lots().get(0).quantity(), "2");
        });
        test("historical cost unaffected by later product and receipt changes", () -> {
            Fixture f = new Fixture(); Order o = f.order(3); f.pay(o);
            f.s.updateProduct(f.p, "ใหม่", "หมวดใหม่", b("100"), true, Map.of(f.i, b("2")));
            f.i.addIngredient("future", DAY, DAY.plusDays(20), b("50"), b("99"), "ล็อตใหม่");
            eq(o.totalCost(), "13"); eq(o.paid(), "105"); check(o.items().get(0).name().equals("สินค้า"), "name snapshot");
            o.cancelOrder(f.s, "คืน"); eq(f.s.summary().totalCost(), "0"); eq(f.s.summary().profit(), "0");
            eq(f.i.lots().get(0).quantity(), "2"); eq(f.i.lots().get(1).quantity(), "98");
        });
        test("cross-day refunds are recorded in refund period", () -> {
            Fixture f = new Fixture(); Order o = f.order(3); f.pay(o); f.clock.nextDay(); o.cancelOrder(f.s, "คืนวันถัดไป");
            Sales saleDay = f.s.salesReport(DAY, DAY), returnDay = f.s.salesReport(DAY.plusDays(1), DAY.plusDays(1));
            eq(saleDay.summary().netSales(), "105"); eq(saleDay.summary().totalCost(), "13"); eq(saleDay.calculateSalesProfit(), "92");
            eq(returnDay.summary().payments(), "0"); eq(returnDay.summary().refunds(), "105");
            eq(returnDay.summary().netSales(), "-105"); eq(returnDay.summary().totalCost(), "-13"); eq(returnDay.calculateSalesProfit(), "-92");
            eq(f.s.sessionReport().summary().netSales(), "0");
            check(returnDay.summary().paidOrders() == 0 && returnDay.summary().cancelledPaidOrders() == 1, "period counts");
        });
        test("refund to expired original lot remains unusable", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); f.pay(o); f.clock.nextDay(); o.cancelOrder(f.s, "คืน");
            eq(f.i.lots().get(0).quantity(), "2"); eq(f.i.usable(DAY.plusDays(1)), "98");
            check(String.join("\n", f.i.alerts(DAY.plusDays(1))).contains("หมดอายุ"), "expired return");
        });
        test("report boundaries empty period and invalid range", () -> {
            Fixture f = new Fixture(); Order a = f.order(1); f.pay(a); f.clock.nextDay(); Order c = f.order(1); f.pay(c);
            eq(f.s.salesReport(DAY, DAY.plusDays(1)).summary().payments(), "70");
            eq(f.s.salesReport(DAY.plusDays(1), DAY.plusDays(1)).summary().payments(), "35");
            Sales empty = f.s.salesReport(DAY.minusDays(2), DAY.minusDays(1));
            eq(empty.summary().profit(), "0"); check(empty.getTopSellingProducts(5).isEmpty(), "empty ranking");
            rejects(() -> f.s.salesReport(DAY.plusDays(1), DAY));
        });
        test("top selling net returns tie order and limit", () -> {
            Fixture f = new Fixture(); Product other = f.s.addProduct("อันดับสอง", "อื่น", b("20"), true, Map.of(f.i, BigDecimal.ONE));
            Order a = f.order(2); f.pay(a); Order cancelled = f.order(1); f.pay(cancelled); f.s.cancel(cancelled, "คืน");
            Order c = f.s.draft(f.m); c.addOrderItem(other, 2); f.pay(c);
            Map<Product, Long> ranked = f.s.sessionReport().getTopSellingProducts(5);
            check(new ArrayList<>(ranked.keySet()).equals(List.of(f.p, other)) && ranked.get(f.p) == 2L, "rank/tie/refund");
            check(f.s.sessionReport().getTopSellingProducts(1).size() == 1, "limit"); rejects(() -> f.s.sessionReport().getTopSellingProducts(0));
            f.clock.nextDay(); f.s.cancel(a, "คืนใหม่");
            check(f.s.salesReport(DAY.plusDays(1), DAY.plusDays(1)).getTopSellingProducts(5).isEmpty(), "negative ranking shown");
        });
        test("aggregation reports share orders and remain independently usable", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); f.pay(o);
            Sales a = f.s.sessionReport(), other = f.s.sessionReport();
            check(a.orders().get(0) == other.orders().get(0) && !a.salesId().equals(other.salesId()), "aggregation");
            o.cancelOrder(f.s, "คืน"); eq(a.summary().netSales(), "0"); check(f.s.orders().size() == 1, "report lifecycle");
        });
        test("actors register/order/pay and prevent wrong customer payment", () -> {
            Fixture f = new Fixture(); Customer guest = f.s.guest(); Member newMember = guest.customerRegister(f.s, "ใหม่", "0822222222");
            check(newMember.id().equals(guest.id()) && !newMember.memberId().equals(newMember.id()), "distinct IDs");
            Order o = f.s.employee().receiveEmployeeProduct(f.s, newMember); o.addOrderItem(f.p, 1);
            rejects(() -> f.m.customerPayment(f.s, o, Order.Payment.CASH, b("35"), true));
            f.s.employee().recordEmployeeConfirmation(f.s, o, Order.Payment.CASH, b("40"), true); eq(o.change(), "5");
            rejects(() -> guest.customerRegister(f.s, "ซ้ำ", "0833333333"));
        });
        test("reason required and status changes cannot bypass workflow", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); f.pay(o);
            rejects(() -> o.cancelOrder(f.s, "")); rejects(() -> o.updateOrderStatus(f.s, Order.Status.DELIVERED));
            rejects(() -> o.updateOrderStatus(f.s, Order.Status.WAITING_PREPARATION));
            check(o.cancelledDateTime() == null && o.orderDateTime().equals(f.s.now()) && o.paidDateTime().equals(f.s.now()), "timestamp mutation");
            o.cancelOrder(f.s, "เหตุผล"); check(o.cancelReason().equals("เหตุผล"), "reason missing");
        });
        test("category/status and lot cost validation preserve state", () -> {
            Fixture f = new Fixture();
            rejects(() -> f.s.updateProduct(f.p, "ใหม่", "", b("50"), true, Map.of(f.i, BigDecimal.ONE)));
            check(f.p.category().equals("ไอศกรีม") && f.p.name().equals("สินค้า"), "category partial edit");
            f.p.updateProductStatus(false); rejects(() -> f.order(1));
            rejects(() -> f.i.addIngredient("bad", DAY, DAY, b("1"), b("-1"), "ทดสอบ"));
            rejects(() -> f.i.addIngredient("bad", DAY, DAY, b("1"), b("1"), ""));
            check(f.i.lots().size() == 2 && f.i.isNearExpiryIngredient(DAY, 0), "lot validation");
        });
        test("console point history and cancellation reason", () -> {
            Fixture f = new Fixture();
            String output = console(f.s, "1\n1\n0812345678\n1\n2\n1\n1\n1\n3\n10\n4\n1\n30\n1\n2\n1\n4\n1\nเปลี่ยนใจ\n0\n3\n5\n0812345678\n0\n0\n");
            check(output.contains("ประวัติคะแนน") && output.contains("คืน 10") && output.contains("เปลี่ยนใจ") && output.contains("พนักงานเคาน์เตอร์"), "audit console");
            check(f.s.orders().get(0).cancelReason().equals("เปลี่ยนใจ") && f.m.points() == 100, "console refund");
        });
        test("console date range cost profit and ranking", () -> {
            Fixture f = new Fixture(); Order o = f.order(3); f.pay(o);
            String output = console(f.s, "6\n2\n2026-10-07\n2026-10-07\n0\n");
            check(output.contains("ต้นทุนวัตถุดิบสุทธิ 13.00") && output.contains("กำไรขั้นต้น 92.00") && output.contains("3 หน่วย"), "report console");
        });
        test("negative demo initialization cannot corrupt history", () -> {
            Fixture f = new Fixture(); Member m = f.s.register("ใหม่", "0899999999");
            rejects(() -> m.initializeDemoPoints(-10, f.s.now()));
            check(m.points() == 0 && m.getPointHistoryMember().isEmpty(), "invalid init");
        });
        test("seven-card Order-Product composition owns independent immutable snapshots", () -> {
            Fixture f = new Fixture(); Order a = f.order(1), other = f.order(1);
            Product aProduct = a.orderProducts().get(0), otherProduct = other.orderProducts().get(0);
            check(aProduct != otherProduct && aProduct != f.p && aProduct.id().equals(f.p.id()), "snapshot ownership");
            f.s.updateProduct(f.p, "ชื่อใหม่", "หมวดใหม่", b("99"), true, Map.of(f.i, b("2")));
            check(aProduct.name().equals("สินค้า") && aProduct.category().equals("ไอศกรีม"), "snapshot changed");
            eq(aProduct.getSalePrice(), "35");
            rejects(() -> aProduct.updateProductStatus(false));
            rejects(() -> aProduct.updateProduct("แก้สำเนา", "อื่น", b("1"), true, Map.of(f.i, BigDecimal.ONE)));
            f.pay(a); a.cancelOrder(f.s, "คืน");
            check(f.s.products().contains(f.p) && f.p.active() && other.orderProducts().get(0) == otherProduct, "cancel removed master product");
        });
        return passed;
    }
}
