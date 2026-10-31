package pos;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public class PosTests {
    private static int passed;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T05:00:00Z"), ZoneOffset.UTC);
    private static BigDecimal b(String value) { return new BigDecimal(value); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void eq(BigDecimal actual, String expected) { check(actual.compareTo(b(expected)) == 0, actual + " != " + expected); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    private static void test(String name, Runnable action) {
        try { action.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable e) { throw new AssertionError("FAIL " + name, e); }
    }
    private static class Fixture {
        final PosSystem s = new PosSystem(CLOCK);
        final Member m = s.register("ทดสอบ", "0812345678");
        final Ingredient ice = s.addIngredient("ไอศกรีม", "สกู๊ป", b("100"));
        final Ingredient cone = s.addIngredient("โคน", "ชิ้น", b("100"));
        final Product p;
        Fixture() {
            ice.receive("old", DAY.minusDays(5), DAY.plusDays(2), b("2"));
            ice.receive("new", DAY.minusDays(1), DAY.plusDays(10), b("98"));
            cone.receive("cone", DAY.minusDays(3), DAY.plusDays(30), b("100"));
            p = s.addProduct("วานิลลาโคน", b("35"), true, Map.of(ice, BigDecimal.ONE, cone, BigDecimal.ONE));
            m.initializeDemoPoints(100, s.now());
        }
        Order order(int quantity) { Order o = s.draft(m); o.addOrderItem(p, quantity); return o; }
        void cash(Order o) { s.pay(o, Order.Payment.CASH, o.net(), true); }
        void delivered(Order o) { cash(o); s.start(o); s.ready(o); s.deliver(o); }
    }
    private static String console(PosSystem s, String script) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ConsoleApp(s, new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)), bytes).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }
    public static void main(String[] args) {
        test("cash/change and payment-only stock", () -> {
            Fixture f = new Fixture(); Order o = f.order(1);
            eq(f.ice.usable(DAY), "100");
            f.s.pay(o, Order.Payment.CASH, b("100"), true);
            eq(o.change(), "65"); eq(o.paid(), "35"); eq(f.ice.usable(DAY), "99");
            check(o.status() == Order.Status.WAITING_PREPARATION, "status");
            rejects(() -> f.cash(o)); eq(f.ice.usable(DAY), "99");
        });
        test("incomplete cash does not change state", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); o.applyOrderDiscount(10);
            rejects(() -> f.s.pay(o, Order.Payment.CASH, b("24.99"), true));
            check(f.m.points() == 100, "points changed"); eq(f.ice.usable(DAY), "100");
            check(o.payment() == null && o.status() == Order.Status.WAITING_PAYMENT, "payment changed");
        });
        test("QR requires confirmation and exact amount", () -> {
            Fixture f = new Fixture(); Order o = f.order(1);
            rejects(() -> f.s.pay(o, Order.Payment.QR, b("35"), false));
            rejects(() -> f.s.pay(o, Order.Payment.QR, b("36"), true));
            rejects(() -> f.s.pay(o, Order.Payment.QR, b("34"), true));
            eq(f.ice.usable(DAY), "100");
            f.s.pay(o, Order.Payment.QR, b("35"), true); eq(o.change(), "0");
            check(o.payment() == Order.Payment.QR, "method");
        });
        test("draft/invalid transitions and empty checkout", () -> {
            Fixture f = new Fixture(); Order o = f.s.draft(f.m);
            rejects(() -> f.cash(o)); rejects(() -> f.s.start(o)); rejects(() -> f.s.ready(o)); rejects(() -> f.s.deliver(o));
            f.s.cancel(o); eq(o.refund(), "0"); check(f.s.summary().paidOrders() == 0, "draft counted");
            rejects(() -> o.addOrderItem(f.p, 1)); rejects(() -> f.s.cancel(o));
        });
        test("discount blocks/funds/order limits", () -> {
            Fixture f = new Fixture(); Order o = f.order(2);
            rejects(() -> o.applyOrderDiscount(9)); rejects(() -> o.applyOrderDiscount(-10));
            rejects(() -> o.applyOrderDiscount(110)); rejects(() -> o.applyOrderDiscount(80));
            o.applyOrderDiscount(30); eq(o.net(), "40"); f.cash(o);
            check(f.m.points() == 70, "spend"); rejects(() -> o.applyOrderDiscount(0));
        });
        test("guest cannot spend or receive member points", () -> {
            Fixture f = new Fixture(); Order o = f.s.draft(null); o.addOrderItem(f.p, 1);
            rejects(() -> o.applyOrderDiscount(10)); f.delivered(o); check(o.earnedPoints() == 0, "guest points");
        });
        test("points on delivery/floor/single credit", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); o.applyOrderDiscount(10); f.cash(o);
            check(f.m.points() == 90, "points credited early");
            f.s.start(o); f.s.ready(o); check(f.m.points() == 90, "points credited early");
            f.s.deliver(o); check(f.m.points() == 92 && o.earnedPoints() == 2, "floor/net basis");
            rejects(() -> f.s.deliver(o)); check(f.m.points() == 92, "duplicate credit");
        });
        test("amount below 10 earns zero", () -> {
            Fixture f = new Fixture(); Product p = f.s.addProduct("ตัวอย่างราคาเล็ก", b("9.99"), true, Map.of(f.ice, b("0.1")));
            Order o = f.s.draft(f.m); o.addOrderItem(p, 1); f.delivered(o);
            check(f.m.points() == 100 && o.earnedPoints() == 0, "below minimum");
        });
        test("full points payment with zero cash", () -> {
            Fixture f = new Fixture(); Product p = f.s.addProduct("ตัวอย่าง", b("30"), true, Map.of(f.ice, BigDecimal.ONE));
            Order o = f.s.draft(f.m); o.addOrderItem(p, 1); o.applyOrderDiscount(30); f.delivered(o);
            eq(o.paid(), "0"); check(f.m.points() == 70 && o.earnedPoints() == 0, "zero net");
            check(f.s.summary().paidOrders() == 1, "zero net order not counted");
        });
        test("points rechecked against another paid draft", () -> {
            Fixture f = new Fixture(); Order a = f.order(3), other = f.order(3); a.applyOrderDiscount(100); other.applyOrderDiscount(100);
            f.cash(other); BigDecimal before = f.ice.usable(DAY);
            rejects(() -> f.cash(a)); eq(f.ice.usable(DAY), before.toPlainString());
            check(a.payment() == null, "stale balance accepted");
        });
        test("cancel refunds net and restores original FIFO lots once", () -> {
            Fixture f = new Fixture(); Order o = f.order(3); o.applyOrderDiscount(30); f.s.pay(o, Order.Payment.CASH, b("100"), true);
            eq(f.ice.lots().get(0).quantity(), "0"); eq(f.ice.lots().get(1).quantity(), "97");
            f.s.cancel(o); eq(o.refund(), "75"); check(f.m.points() == 100, "points not restored");
            eq(f.ice.lots().get(0).quantity(), "2"); eq(f.ice.lots().get(1).quantity(), "98"); eq(f.cone.usable(DAY), "100");
            rejects(() -> f.s.cancel(o)); eq(f.ice.usable(DAY), "100"); check(f.m.points() == 100, "double refund");
        });
        test("QR cancel retains refund ledger", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); f.s.pay(o, Order.Payment.QR, b("35"), true);
            f.s.cancel(o); eq(f.s.summary().refunds(), "35"); eq(f.s.summary().netSales(), "0");
        });
        test("cancel forbidden after preparation begins", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); f.cash(o); f.s.start(o);
            rejects(() -> f.s.cancel(o)); f.s.ready(o); rejects(() -> f.s.cancel(o));
            f.s.deliver(o); rejects(() -> f.s.cancel(o)); eq(f.ice.usable(DAY), "99"); eq(o.refund(), "0");
        });
        test("FIFO follows receipt not expiry", () -> {
            Fixture f = new Fixture(); Ingredient i = f.s.addIngredient("FIFO", "ชิ้น", b("10"));
            i.receive("later", DAY.minusDays(1), DAY.plusDays(1), b("10"));
            i.receive("first", DAY.minusDays(2), DAY.plusDays(5), b("10"));
            Product p = f.s.addProduct("FIFO", b("10"), true, Map.of(i, b("12")));
            Order o = f.s.draft(null); o.addOrderItem(p, 1); f.cash(o);
            eq(i.lots().get(0).quantity(), "0"); eq(i.lots().get(1).quantity(), "8");
            check(o.allocations().get(0).lot().id().equals("first"), "not FIFO");
        });
        test("expired stock skipped, expiry day usable", () -> {
            Fixture f = new Fixture(); Ingredient i = f.s.addIngredient("expiry", "ชิ้น", b("10"));
            i.receive("expired", DAY.minusDays(5), DAY.minusDays(1), b("10"));
            i.receive("today", DAY.minusDays(4), DAY, b("1"));
            Product p = f.s.addProduct("expiry", b("10"), true, Map.of(i, BigDecimal.ONE));
            Order o = f.s.draft(null); o.addOrderItem(p, 1); f.cash(o);
            eq(i.lots().get(0).quantity(), "10"); eq(i.lots().get(1).quantity(), "0");
            eq(i.usable(DAY.plusDays(1)), "0");
            Order next = f.s.draft(null); next.addOrderItem(p, 1); rejects(() -> f.cash(next));
        });
        test("all ingredient validation is atomic", () -> {
            Fixture f = new Fixture(); f.cone.reduce("cone", b("100"), "ทดสอบหมด", DAY);
            Order o = f.order(1); o.applyOrderDiscount(10); rejects(() -> f.cash(o));
            eq(f.ice.usable(DAY), "100"); check(f.m.points() == 100, "partial points mutation");
            check(o.payment() == null && o.allocations().isEmpty(), "partial payment");
        });
        test("shared ingredient quantities aggregated across items", () -> {
            Fixture f = new Fixture(); Order o = f.order(60); o.addOrderItem(f.p, 41);
            rejects(() -> f.cash(o)); eq(f.ice.usable(DAY), "100"); eq(f.cone.usable(DAY), "100");
        });
        test("low-stock boundary excludes expired quantity", () -> {
            Fixture f = new Fixture(); Ingredient i = f.s.addIngredient("boundary", "ชิ้น", b("100"));
            i.receive("valid", DAY, DAY.plusDays(10), b("50"));
            i.receive("expired", DAY.minusDays(2), DAY.minusDays(1), b("100"));
            check(!i.low(DAY), "50% warned"); i.reduce("valid", b("0.001"), "boundary", DAY);
            check(i.low(DAY), "below 50% missing");
        });
        test("expiry alerts at 3/0/-1 but not 4 days", () -> {
            Fixture f = new Fixture(); Ingredient i = f.s.addIngredient("alert", "ชิ้น", b("1"));
            i.receive("three", DAY, DAY.plusDays(3), b("1"));
            i.receive("four", DAY, DAY.plusDays(4), b("1"));
            i.receive("today", DAY, DAY, b("1"));
            i.receive("expired", DAY.minusDays(2), DAY.minusDays(1), b("1"));
            String alerts = String.join("\n", i.alerts(DAY));
            check(alerts.contains("three") && alerts.contains("today") && alerts.contains("หมดอายุ: alert ล็อต expired") && !alerts.contains("four"), "alert boundary");
            i.reduce("three", b("1"), "remove", DAY);
            check(!String.join("\n", i.alerts(DAY)).contains("three"), "empty lot warned");
        });
        test("receipt/adjustment validation and reason history", () -> {
            Fixture f = new Fixture();
            rejects(() -> f.ice.receive("old", DAY, DAY.plusDays(5), b("1")));
            rejects(() -> f.ice.receive("x", DAY, DAY.minusDays(1), b("1")));
            rejects(() -> f.ice.receive("x", DAY, DAY, b("0")));
            rejects(() -> f.ice.reduce("old", b("1"), "", DAY));
            rejects(() -> f.ice.reduce("old", b("3"), "too much", DAY));
            rejects(() -> f.ice.reduce("missing", b("1"), "reason", DAY));
            rejects(() -> f.ice.reduce("old", b("-1"), "reason", DAY));
            f.ice.reduce("old", b("1"), "ของเสีย", DAY);
            check(f.ice.adjustments().size() == 1 && f.ice.adjustments().get(0).reason().equals("ของเสีย"), "reason missing");
        });
        test("member duplicate and atomic edit", () -> {
            Fixture f = new Fixture(); Member other = f.s.register("other", "0899999999");
            rejects(() -> f.s.register("duplicate", f.m.phone()));
            rejects(() -> f.s.register("invalid", "abc"));
            rejects(() -> f.s.editMember(f.m, "new", other.phone()));
            rejects(() -> f.s.editMember(f.m, "new", "12"));
            check(f.m.name().equals("ทดสอบ") && f.m.phone().equals("0812345678"), "partial edit");
            f.s.editMember(f.m, "ใหม่", "0822222222"); check(f.s.findMember("0822222222") == f.m && f.m.points() == 100, "edit");
        });
        test("product snapshots and recipe validation", () -> {
            Fixture f = new Fixture(); Order o = f.order(1);
            f.s.updateProduct(f.p, "new", b("50"), false, Map.of(f.ice, b("2")));
            eq(o.subtotal(), "35"); f.cash(o); eq(f.ice.usable(DAY), "99"); eq(f.cone.usable(DAY), "99");
            rejects(() -> f.order(1));
            rejects(() -> f.s.updateProduct(f.p, "bad", b("0"), true, Map.of(f.ice, b("1"))));
            rejects(() -> f.s.updateProduct(f.p, "bad", b("10"), true, Map.of()));
            rejects(() -> f.s.updateProduct(f.p, "bad", b("10"), true, Map.of(f.ice, b("-1"))));
            check(f.p.name().equals("new") && !f.p.active(), "partial product edit");
        });
        test("remove item preserves valid discount", () -> {
            Fixture f = new Fixture(); Order o = f.order(1); o.addOrderItem(f.p, 1); o.applyOrderDiscount(40);
            rejects(() -> o.remove(0)); check(o.items().size() == 2, "partial remove");
            o.applyOrderDiscount(0); o.remove(0); eq(o.subtotal(), "35");
            rejects(() -> o.addOrderItem(f.p, 0)); rejects(() -> o.addOrderItem(f.p, -1)); rejects(() -> o.remove(5));
        });
        test("sales ledger includes paid orders and reversals only", () -> {
            Fixture f = new Fixture(); Order sold = f.order(2); sold.usePoints(20); f.delivered(sold);
            Order cancelled = f.order(1); f.cash(cancelled); f.s.cancel(cancelled);
            f.order(4); Order draft = f.order(1); f.s.cancel(draft);
            Sales.Summary summary = f.s.summary();
            check(summary.paidOrders() == 2 && summary.cancelledPaidOrders() == 1 && summary.deliveredOrders() == 1, "counts");
            eq(summary.gross(), "105"); eq(summary.discounts(), "20"); eq(summary.payments(), "85"); eq(summary.refunds(), "35"); eq(summary.netSales(), "50");
        });
        test("Thailand date independent of host timezone", () -> {
            PosSystem s = new PosSystem(Clock.fixed(Instant.parse("2026-10-06T18:00:00Z"), ZoneOffset.UTC));
            check(s.today().equals(DAY), "timezone incorrect");
        });
        test("console malformed menu/quantity/money recovery", () -> {
            Fixture f = new Fixture();
            String output = console(f.s, "abc\n-1\n9\n1\n0\n1\n1\n-2\nx\n1\n4\n1\nno\n-10\n35.001\n35\n1\n0\n");
            check(output.contains("กรุณากรอกจำนวนเต็ม") && output.contains("กรุณากรอกจำนวนตั้งแต่ 0") && output.contains("ชำระสำเร็จ"), "input recovery");
            check(f.s.summary().paidOrders() == 1, "console checkout missing");
        });
        test("console sale/delivery/Thai receipt and summary", () -> {
            Fixture f = new Fixture();
            String output = console(f.s, "1\n1\n0812345678\n1\n1\n1\n3\n10\n4\n1\n100\n1\n2\n1\n2\n1\n3\n1\n4\n0\n6\n1\n0\n");
            check(output.contains("เงินทอน 75.00") && output.contains("ส่งมอบสำเร็จ ได้รับ 2 คะแนน") && output.contains("ยอดขายสุทธิ 25.00"), "end-to-end output");
            check(f.m.points() == 92 && f.s.orders().get(0).status() == Order.Status.DELIVERED, "end-to-end state");
        });
        test("console unconfirmed QR preserves draft", () -> {
            Fixture f = new Fixture();
            String output = console(f.s, "1\n0\n1\n1\n1\n4\n2\n0\n0\n0\n");
            check(output.contains("ยังไม่ยืนยันชำระ"), "QR simulation label missing");
            eq(f.ice.usable(DAY), "100"); check(f.s.orders().get(0).payment() == null, "QR mutated");
        });
        test("console member registration/edit/search", () -> {
            Fixture f = new Fixture();
            String output = console(f.s, "3\n1\nสมาชิกใหม่\n0833333333\n3\n0833333333\nชื่อแก้ไข\n0844444444\n2\n0844444444\n4\n0\n0\n");
            check(f.s.findMember("0844444444").name().equals("ชื่อแก้ไข") && output.contains("ชื่อแก้ไข"), "member menu");
        });
        test("console product creation/edit and ingredient menus", () -> {
            Fixture f = new Fixture();
            String output = console(f.s, "4\n1\nสินค้าใหม่\nไอศกรีม\n20\n1\n1\n1\n0\n2\n2\nแก้สินค้า\nไอศกรีมใหม่\n25\n0\n0\n0\n5\n1\n1\nextra\n2026-10-07\n2026-10-20\n5\n3\nรับทดสอบ\n2\n1\nextra\n1\nเสีย\n3\n1\n4\n5\nนม\nมล.\n1000\n0\n0\n");
            check(f.s.products().size() == 2 && f.s.products().get(1).name().equals("แก้สินค้า") && !f.s.products().get(1).active(), "product menu");
            check(f.s.ingredients().size() == 3 && f.ice.adjustments().size() == 1 && output.contains("ใกล้หมดอายุ"), "ingredient menu");
            eq(f.ice.usable(DAY), "104");
        });
        test("console EOF exits cleanly", () -> {
            Fixture f = new Fixture(); String output = console(f.s, "1\n");
            check(output.contains("สิ้นสุดข้อมูลนำเข้า"), "EOF handling");
        });
        test("fresh session resets demo data", () -> {
            PosSystem first = PosSystem.demo(), second = PosSystem.demo();
            first.register("new", "0898765432");
            check(second.members().size() == 1 && second.orders().isEmpty(), "shared state");
        });
        test("POS state persists with linked member, order and stock objects", () -> {
            try {
                Fixture f = new Fixture(); Order o = f.order(1); f.delivered(o);
                java.nio.file.Path file = java.nio.file.Files.createTempFile("pos-persist", ".bin");
                try {
                    f.s.save(file); PosSystem loaded = PosSystem.loadOrDemo(file);
                    check(loaded.orders().size() == 1 && loaded.members().get(0).points() == 103, "loaded order/member state");
                    eq(loaded.ingredients().get(0).usable(DAY), "99");
                    check(loaded.orders().get(0).items().get(0).product() == loaded.products().get(0), "linked product reference");
                } finally { java.nio.file.Files.deleteIfExists(file); }
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        });
        passed += ModelTests.run();
        System.out.println("ALL " + passed + " TESTS PASSED");
    }
}
