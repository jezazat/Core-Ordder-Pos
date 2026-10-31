package pos;

import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public class ConsoleApp {
    private final PosSystem system;
    private final BufferedReader input;
    private final PrintWriter out;
    private static class EndOfInput extends RuntimeException {}
    public ConsoleApp(PosSystem system, InputStream input, OutputStream output) {
        this.system = system;
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
    }
    private static final java.nio.file.Path DATA_FILE = java.nio.file.Path.of("data", "pos-data.bin");
    public static void main(String[] args) {
        try {
            PosSystem system = PosSystem.loadOrDemo(DATA_FILE);
            new ConsoleApp(system, System.in, System.out).run();
            system.save(DATA_FILE);
        } catch (IOException e) { System.err.println("เปิด/บันทึกข้อมูลไม่ได้: " + e.getMessage()); }
    }
    private String text(String prompt) {
        out.print(prompt + ": "); out.flush();
        try {
            String line = input.readLine();
            if (line == null) throw new EndOfInput();
            return line.trim();
        } catch (IOException e) { throw new EndOfInput(); }
    }
    private int number(String prompt, int min, int max) {
        while (true) {
            try {
                int value = Integer.parseInt(text(prompt));
                if (value >= min && value <= max) return value;
            } catch (NumberFormatException ignored) {}
            out.println("กรุณากรอกจำนวนเต็มระหว่าง " + min + " ถึง " + max);
        }
    }
    private BigDecimal decimal(String prompt, boolean zeroAllowed, boolean money) {
        while (true) {
            try {
                String value = text(prompt);
                if (!value.matches("[0-9]{1,9}(\\.[0-9]{1,3})?")) throw new NumberFormatException();
                BigDecimal amount = new BigDecimal(value);
                if (money) amount = amount.setScale(2, RoundingMode.UNNECESSARY);
                if (zeroAllowed ? amount.signum() >= 0 : amount.signum() > 0) return amount;
            } catch (NumberFormatException | ArithmeticException ignored) {}
            out.println("กรุณากรอกจำนวน" + (zeroAllowed ? "ตั้งแต่ 0" : "มากกว่า 0") + (money ? " (ทศนิยมไม่เกิน 2 ตำแหน่ง)" : " (ทศนิยมไม่เกิน 3 ตำแหน่ง)"));
        }
    }
    private LocalDate date(String prompt) {
        while (true) {
            try { return LocalDate.parse(text(prompt + " [yyyy-MM-dd ค.ศ.]")); }
            catch (java.time.format.DateTimeParseException ignored) { out.println("วันที่ไม่ถูกต้อง เช่น 2026-10-07"); }
        }
    }
    private boolean yes(String prompt) {
        out.println(prompt);
        out.println("1. ใช่");
        out.println("0. ไม่ใช่");
        return number("เลือก", 0, 1) == 1;
    }
    private String money(BigDecimal amount) { return amount.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private void title(String title) {
        out.println(); out.println("============================================================");
        out.println(title); out.println("============================================================");
    }
    public void run() {
        title("Core Order POS");
        out.println("พนักงาน A | รหัส A001");
        out.println("ขายสินค้า: เลือก 1 → เลือกลูกค้า → เลือกไอศกรีม/ขนาด/รส → ชำระเงิน");
        alerts();
        try {
            while (true) {
                title("เมนูหลัก");
                out.println("งานขาย");
                out.println("  1. เริ่มขายสินค้า");
                out.println("  2. ดูและจัดการออร์เดอร์");
                out.println("ข้อมูลร้าน");
                out.println("  3. สมาชิก");
                out.println("  4. เมนูสินค้าและสูตร");
                out.println("  5. วัตถุดิบและสต็อก");
                out.println("  6. รายงานยอดขาย");
                out.println("  0. ปิดโปรแกรม (บันทึกข้อมูลแล้ว)");
                int menu = number("เลือกเมนู", 0, 6);
                if (menu == 0) { out.println("ปิดโปรแกรมแล้ว ข้อมูลบันทึกไว้ในเครื่อง"); return; }
                try {
                    switch (menu) {
                        case 1 -> sale(); case 2 -> orders(); case 3 -> members();
                        case 4 -> products(); case 5 -> ingredients(); case 6 -> sales();
                        default -> throw new AssertionError();
                    }
                } catch (IllegalArgumentException | ArithmeticException e) { out.println("ทำรายการไม่ได้: " + e.getMessage()); }
                try { system.save(DATA_FILE); } catch (IOException e) { out.println("บันทึกข้อมูลไม่ได้: " + e.getMessage()); }
            }
        } catch (EndOfInput e) { out.println(); out.println("สิ้นสุดข้อมูลนำเข้า จบการทำงาน"); }
    }
    private Member lookupMember() { return system.findMember(text("เบอร์โทรสมาชิก")); }
    private void sale() {
        title("ขายสินค้า");
        Member member = null;
        out.println("เลือกลูกค้า: สมาชิกใช้คะแนนสะสมได้; ลูกค้าทั่วไปเลือกไม่เป็นสมาชิก");
        if (yes("ลูกค้าเป็นสมาชิกหรือไม่")) { member = lookupMember(); showMember(member); }
        Order order = system.employee().receiveEmployeeProduct(system, member == null ? system.guest() : member);
        out.println("สร้างร่าง " + order.id() + " แล้ว");
        editDraft(order);
    }
    private void editDraft(Order order) {
        while (order.status() == Order.Status.WAITING_PAYMENT) {
            showOrder(order);
            out.println("1. เพิ่มสินค้า");
            out.println("2. ลบสินค้าออกจากรายการ");
            out.println("3. ใช้คะแนนสะสม");
            out.println("4. ชำระเงิน");
            out.println("5. ยกเลิกร่างออร์เดอร์");
            out.println("0. ทิ้งรายการและกลับเมนูหลัก (ไม่บันทึกร่าง)");
            int choice = number("เลือก", 0, 5);
            if (choice == 0) {
                system.discardDraft(order);
                out.println("ทิ้งออร์เดอร์ที่ยังไม่ชำระแล้ว กลับเมนูหลัก");
                return;
            }
            try {
                switch (choice) {
                    case 1 -> {
                        addCustomIceCream(order);
                    }
                    case 2 -> {
                        if (order.items().isEmpty()) throw new IllegalArgumentException("ไม่มีรายการให้ลบ");
                        order.remove(number("ลำดับรายการที่จะลบ", 1, order.items().size()) - 1);
                    }
                    case 3 -> order.applyOrderDiscount(number("คะแนนที่ต้องการใช้ (ชุดละ 10, 0=ไม่ใช้)", 0, Integer.MAX_VALUE));
                    case 4 -> payment(order);
                    case 5 -> { if (yes("ยืนยันยกเลิกร่าง")) order.cancelOrder(system, text("เหตุผลยกเลิก")); }
                    default -> throw new AssertionError();
                }
            } catch (IllegalArgumentException | ArithmeticException e) { out.println("ทำรายการไม่ได้: " + e.getMessage()); }
        }
    }
    private void payment(Order order) {
        showOrder(order);
        out.println("วิธีชำระเงิน");
        out.println("1. เงินสด");
        out.println("2. QR จำลอง");
        out.println("0. กลับ");
        int method = number("วิธีชำระ", 0, 2);
        if (method == 0) return;
        BigDecimal received;
        boolean confirmed = true;
        if (method == 1) received = decimal("เงินที่รับจากลูกค้า", true, true);
        else {
            out.println("QR จำลอง — โปรดตรวจผลชำระเงินยอด " + money(order.net()) + " บาท");
            out.println("ไม่มีการสร้าง QR หรือเชื่อมต่อธนาคาร");
            received = order.net(); confirmed = yes("พนักงานยืนยันว่าชำระ QR สำเร็จครบยอด");
        }
        if (!confirmed) { out.println("ยังไม่ยืนยันชำระ ออร์เดอร์ยังเป็นร่าง"); return; }
        if (!yes("ยืนยันบันทึกการชำระและตัดวัตถุดิบ")) return;
        system.employee().recordEmployeeConfirmation(system, order, method == 1 ? Order.Payment.CASH : Order.Payment.QR, received, confirmed);
        out.println("ชำระสำเร็จ | เงินทอน " + money(order.change()) + " บาท | สถานะ " + order.status());
        out.println("ตัดวัตถุดิบ:");
        for (StockAllocation a : order.allocations()) out.println("  " + a.ingredient().name() + " ล็อต " + a.lot().id() + " " + a.quantity().toPlainString() + " " + a.ingredient().unit() + " | ต้นทุน " + money(a.totalCost()));
        alerts();
    }
    private void showOrder(Order o) {
        title(o.id() + " | " + o.status() + " | " + o.customer().name() + " | รับโดย " + o.employee().name());
        int n = 1;
        out.println("สร้างเมื่อ " + o.orderDateTime());
        if (o.paidDateTime() != null) out.println("ชำระเมื่อ " + o.paidDateTime() + " | ต้นทุนล็อตที่ตัดจริง " + money(o.totalCost()));
        if (o.cancelledDateTime() != null) out.println("ยกเลิกเมื่อ " + o.cancelledDateTime() + " | เหตุผล " + o.cancelReason());
        for (OrderItem item : o.items()) out.println(n++ + ". " + item.name() + " x " + item.quantity() + " @ " + money(item.price()) + " = " + money(item.total()));
        out.println("ยอดสินค้า " + money(o.subtotal()) + " | ใช้ " + o.requestedPoints() + " คะแนน | ลด " + money(o.discount()));
        out.println("ยอดสุทธิ " + money(o.net()) + " บาท | จ่ายจริง " + money(o.paid()) + " | คืนเงิน " + money(o.refund()));
        if (o.customer() instanceof Member m) out.println("คะแนนสมาชิกปัจจุบัน " + m.points() + " | คะแนนจากออร์เดอร์นี้ " + o.earnedPoints());
        if (o.payment() != null) out.println("วิธีชำระ " + o.payment() + " | เงินรับ " + money(o.received()) + " | เงินทอน " + money(o.change()));
    }
    private void orders() {
        while (true) {
            title("จัดการออร์เดอร์");
            if (system.orders().isEmpty()) { out.println("ยังไม่มีออร์เดอร์"); return; }
            for (int i = 0; i < system.orders().size(); i++) {
                Order o = system.orders().get(i);
                out.println((i + 1) + ". " + o.id() + " | " + o.status() + " | " + o.customer().name() + " | " + money(o.net()) + " บาท");
            }
            int index = number("เลือกออร์เดอร์ (0=กลับ)", 0, system.orders().size());
            if (index == 0) return;
            Order order = system.orders().get(index - 1);
            showOrder(order);
            out.println("เลือกการทำงาน");
            out.println("1. เปิดออร์เดอร์/ชำระเงิน");
            out.println("2. เริ่มจัดเตรียม");
            out.println("3. ส่งมอบ");
            out.println("4. ยกเลิก");
            out.println("0. กลับ");
            int action = number("เลือก", 0, 4);
            try {
                switch (action) {
                    case 0 -> {}
                    case 1 -> { order.require(Order.Status.WAITING_PAYMENT); editDraft(order); }
                    case 2 -> system.employee().prepareEmployeeProduct(system, order);
                    case 3 -> { system.employee().deliverEmployeeProduct(system, order); out.println("ส่งมอบสำเร็จ ได้รับ " + order.earnedPoints() + " คะแนน"); }
                    case 4 -> {
                        if (yes("ยืนยันยกเลิกและคืนเงิน " + money(order.paid()) + " บาท (QR คืนเงินจำลอง)")) {
                            order.cancelOrder(system, text("เหตุผลยกเลิก"));
                            out.println("ยกเลิกสำเร็จ คืนเงิน " + money(order.refund()) + " บาท คืนคะแนนที่ใช้และวัตถุดิบแล้ว");
                            alerts();
                        }
                    }
                    default -> throw new AssertionError();
                }
            } catch (IllegalArgumentException | ArithmeticException e) { out.println("ทำรายการไม่ได้: " + e.getMessage()); }
        }
    }
    private void showMember(Member m) { out.println(m.memberId() + " / ลูกค้า " + m.id() + " | " + m.name() + " | " + m.phone() + " | " + m.points() + " คะแนน"); }
    private void members() {
        while (true) {
            title("สมาชิก — สมัครและแก้ไขผ่านพนักงานเคาน์เตอร์");
            out.println("1. สมัครสมาชิก");
            out.println("2. ค้นหา/ตรวจคะแนน");
            out.println("3. แก้ไขข้อมูล");
            out.println("4. รายชื่อสมาชิก");
            out.println("5. ประวัติคะแนน");
            out.println("0. กลับ");
            int action = number("เลือก", 0, 5);
            if (action == 0) return;
            try {
                switch (action) {
                    case 1 -> showMember(system.register(text("ชื่อสมาชิก"), text("เบอร์โทร 10 หลัก")));
                    case 2 -> showMember(lookupMember());
                    case 3 -> {
                        Member m = lookupMember(); showMember(m);
                        String name = text("ชื่อใหม่ (ว่าง=เดิม)"), phone = text("เบอร์ใหม่ (ว่าง=เดิม)");
                        system.editMember(m, name.isBlank() ? m.name() : name, phone.isBlank() ? m.phone() : phone);
                        showMember(m);
                    }
                    case 4 -> system.members().forEach(this::showMember);
                    case 5 -> {
                        Member m = lookupMember(); showMember(m);
                        out.println("ประวัติคะแนน:");
                        if (m.getPointHistoryMember().isEmpty()) out.println("ยังไม่มีประวัติ");
                        for (PointTransaction t : m.getPointHistoryMember()) {
                            String kind = switch (t.type()) {
                                case INITIAL -> "ตั้งต้นสาธิต"; case EARN -> "ได้รับ"; case REDEEM -> "ใช้";
                                case RETURN -> "คืน"; case ADJUSTMENT -> "ปรับยอดทดสอบ";
                            };
                            out.println(t.dateTime() + " | " + kind + " " + t.points() + " | คงเหลือ " + t.balanceAfter()
                                    + " | " + (t.orderId() == null ? "-" : t.orderId()) + " | " + t.reason());
                        }
                    }
                    default -> throw new AssertionError();
                }
            } catch (IllegalArgumentException e) { out.println("ทำรายการไม่ได้: " + e.getMessage()); }
        }
    }
    private Product chooseProduct() {
        if (system.products().isEmpty()) throw new IllegalArgumentException("ไม่มีสินค้า");
        return system.products().get(number("ลำดับสินค้า", 1, system.products().size()) - 1);
    }
    private record SizeOption(String type, String size, int price, int maxFlavors, String container) {}
    private void addCustomIceCream(Order order) {
        system.ensureIceCreamMenu();
        List<SizeOption> sizes = List.of(
                new SizeOption("ถ้วย", "S", 29, 2, "ถ้วยไอศกรีม"), new SizeOption("ถ้วย", "M", 39, 3, "ถ้วยไอศกรีม"),
                new SizeOption("ถ้วย", "M+", 49, 4, "ถ้วยไอศกรีม"), new SizeOption("ถ้วย", "L", 59, 5, "ถ้วยไอศกรีม"),
                new SizeOption("ถ้วย", "L+", 79, 5, "ถ้วยไอศกรีม"), new SizeOption("ถ้วย", "XL", 99, 6, "ถ้วยไอศกรีม"),
                new SizeOption("ถ้วย", "XL+", 119, 7, "ถ้วยไอศกรีม"), new SizeOption("โคนวาฟเฟิล", "S", 29, 2, "โคนวาฟเฟิล"),
                new SizeOption("โคนวาฟเฟิล", "M", 39, 3, "โคนวาฟเฟิล"), new SizeOption("โคนวาฟเฟิล", "L", 49, 3, "โคนวาฟเฟิล"),
                new SizeOption("โคนวาฟเฟิล", "XL", 59, 4, "โคนวาฟเฟิล"), new SizeOption("โคนวาฟเฟิล", "2XL", 79, 4, "โคนวาฟเฟิล"),
                new SizeOption("ถ้วยวาฟเฟิล", "M", 89, 5, "ถ้วยวาฟเฟิล"), new SizeOption("ถ้วยวาฟเฟิล", "L", 119, 6, "ถ้วยวาฟเฟิล"));
        for (int i = 0; i < sizes.size(); i++) {
            SizeOption s = sizes.get(i);
            out.println((i + 1) + ". " + s.type() + " " + s.size() + " | " + s.price() + " บาท | เลือกได้ 1-" + s.maxFlavors() + " รส");
        }
        SizeOption size = sizes.get(number("เลือกขนาด", 1, sizes.size()) - 1);
        String[] flavors = {"คาราเมลแครก", "คุกกี้แอนด์ครีม", "ชีส", "นมข้นน้ำผึ้ง", "โยเกิร์ตสตรอเบอร์รี่ชีสเค้ก",
                "มิ้นช็อคชิพ", "อูจิมัทฉะ", "นมเปรี้ยวปีโป้", "เชอร์รี่เบอร์รี่ชอร์เบย์", "มิกซ์แอปเปิ้ล",
                "นมเมล่อน", "มอคค่าอัลมอนด์", "ช็อกโกแลต", "รัมเรซิ่น", "บลูเบอร์รี่โยเกิร์ต"};
        for (int i = 0; i < flavors.length; i++) out.println((i + 1) + ". " + flavors[i]);
        int count = number("จำนวนรสชาติ", 1, size.maxFlavors());
        Map<Ingredient, BigDecimal> recipe = new LinkedHashMap<>();
        Set<Integer> chosen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int flavor = number("เลือกรสที่ " + (i + 1), 1, flavors.length);
            if (!chosen.add(flavor)) throw new IllegalArgumentException("เลือกรสซ้ำไม่ได้");
            recipe.put(system.ingredientNamed(flavors[flavor - 1]), BigDecimal.ONE);
        }
        recipe.put(system.ingredientNamed(size.container()), BigDecimal.ONE);
        String name = size.type() + " " + size.size() + " (" + String.join(", ", chosen.stream().sorted().map(i -> flavors[i - 1]).toList()) + ")";
        Product product = new Product("CUSTOM-" + UUID.randomUUID(), name, "ไอศกรีม", BigDecimal.valueOf(size.price()), true, recipe);
        order.addOrderItem(product, number("จำนวนสินค้า", 1, 10000));
    }
    private Ingredient chooseIngredient() {
        if (system.ingredients().isEmpty()) throw new IllegalArgumentException("ไม่มีวัตถุดิบ");
        return system.ingredients().get(number("ลำดับวัตถุดิบ", 1, system.ingredients().size()) - 1);
    }
    private void showProducts() {
        for (int i = 0; i < system.products().size(); i++) {
            Product p = system.products().get(i);
            out.println((i + 1) + ". " + p.id() + " " + p.name() + " | หมวด " + p.category() + " | " + money(p.getSalePrice()) + " บาท | " + (p.active() ? "ขาย" : "ปิดขาย"));
            try { out.println("   ต้นทุนประมาณการ FIFO ต่อหน่วย " + money(p.calculateCostProduct(system.today())) + " บาท"); }
            catch (IllegalArgumentException e) { out.println("   คำนวณต้นทุนครบสูตรไม่ได้: " + e.getMessage()); }
            p.recipe().forEach((ingredient, q) -> out.println("   " + ingredient.name() + " " + q.toPlainString() + " " + ingredient.unit()));
        }
    }
    private Map<Ingredient, BigDecimal> recipe() {
        out.println("ระบุสูตรต่อสินค้า 1 หน่วย (เลือกวัตถุดิบซ้ำจะบวกจำนวน)");
        showIngredients();
        Map<Ingredient, BigDecimal> recipe = new LinkedHashMap<>();
        while (true) {
            int index = number("ลำดับวัตถุดิบ (0=จบสูตร)", 0, system.ingredients().size());
            if (index == 0) {
                if (recipe.isEmpty()) { out.println("สูตรต้องมีวัตถุดิบอย่างน้อย 1 รายการ"); continue; }
                return recipe;
            }
            Ingredient ingredient = system.ingredients().get(index - 1);
            recipe.merge(ingredient, decimal("จำนวน " + ingredient.unit(), false, false), BigDecimal::add);
        }
    }
    private void products() {
        while (true) {
            title("สินค้าและสูตร"); showProducts();
            out.println("1. เพิ่มสินค้า");
            out.println("2. แก้ไขสินค้า/สูตร");
            out.println("0. กลับ");
            int action = number("เลือก", 0, 2);
            if (action == 0) return;
            try {
                Product p = action == 2 ? chooseProduct() : null;
                String name = text(p == null ? "ชื่อสินค้า" : "ชื่อสินค้า (ว่าง=เดิม)");
                if (p != null && name.isBlank()) name = p.name();
                String category = text(p == null ? "หมวดสินค้า" : "หมวดสินค้า (ว่าง=เดิม)");
                if (p != null && category.isBlank()) category = p.category();
                BigDecimal price = decimal("ราคาใหม่ บาท", false, true);
                boolean active = yes("เปิดขาย");
                Map<Ingredient, BigDecimal> formula = p != null && !yes("เปลี่ยนสูตร") ? p.recipe() : recipe();
                if (p == null) system.employee().saveEmployeeProduct(system, name, category, price, active, formula);
                else system.updateProduct(p, name, category, price, active, formula);
                out.println("บันทึกสินค้าแล้ว (ออร์เดอร์เดิมใช้ราคาและสูตรที่บันทึกไว้ตอนเลือกสินค้า)");
            } catch (IllegalArgumentException | ArithmeticException e) { out.println("ทำรายการไม่ได้: " + e.getMessage()); }
        }
    }
    private void showIngredients() {
        for (int i = 0; i < system.ingredients().size(); i++) {
            Ingredient ingredient = system.ingredients().get(i);
            out.println((i + 1) + ". " + ingredient.id() + " " + ingredient.name() + " | ใช้ขายได้ " + ingredient.getAvailableQuantityIngredient(system.today()).toPlainString() + " " + ingredient.unit() + " | สต็อกปกติ " + ingredient.normalStock() + " | เตือนเมื่อต่ำกว่า " + ingredient.ingredientReorderPoint());
        }
    }
    private void showLots(Ingredient i) {
        for (IngredientLot lot : i.lots()) out.println("  " + lot.id() + " | รับ " + lot.received() + " | หมดอายุ " + lot.expiry() + " | เหลือ " + lot.quantity() + " " + i.unit() + " | ต้นทุน/หน่วย " + money(lot.costPerUnit()) + " | เหตุผลรับเข้า " + lot.receiveReason());
    }
    private void alerts() {
        out.println("แจ้งเตือนวัตถุดิบ:");
        List<String> warnings = system.ingredients().stream().flatMap(i -> i.alerts(system.today()).stream()).toList();
        if (warnings.isEmpty()) out.println("  ไม่มีแจ้งเตือน"); else warnings.forEach(w -> out.println("  " + w));
    }
    private void ingredients() {
        while (true) {
            title("วัตถุดิบ"); showIngredients();
            out.println("1. รับเข้ารายล็อต");
            out.println("2. ลดจำนวนพร้อมเหตุผล");
            out.println("3. ดูล็อต/ประวัติลด");
            out.println("4. แจ้งเตือน");
            out.println("5. เพิ่มชนิดวัตถุดิบ");
            out.println("0. กลับ");
            int action = number("เลือก", 0, 5);
            if (action == 0) return;
            try {
                switch (action) {
                    case 1 -> {
                        Ingredient i = chooseIngredient(); showLots(i);
                        String lotId = text("รหัสล็อตใหม่"); LocalDate received = date("วันรับเข้า");
                        LocalDate expiry = date("วันหมดอายุ"); BigDecimal quantity = decimal("จำนวนรับเข้า", false, false);
                        BigDecimal cost = decimal("ต้นทุนต่อหน่วย บาท (ข้อมูลสาธิต)", true, true);
                        String reason = text("เหตุผลรับเข้า");
                        if (received.isAfter(system.today())) throw new IllegalArgumentException("วันรับเข้าต้องไม่เป็นอนาคต");
                        i.addIngredient(lotId, received, expiry, quantity, cost, reason); out.println("รับเข้าสต็อกแล้ว"); alerts();
                    }
                    case 2 -> {
                        Ingredient i = chooseIngredient(); showLots(i);
                        String lot = text("รหัสล็อตที่จะลด"); BigDecimal quantity = decimal("จำนวนลด", false, false);
                        i.reduceIngredient(lot, quantity, text("เหตุผล เช่น เสีย/หมดอายุ/ปรับยอด"), system.today());
                        out.println("ลดสต็อกและบันทึกเหตุผลแล้ว"); alerts();
                    }
                    case 3 -> {
                        Ingredient i = chooseIngredient(); showLots(i);
                        out.println("ประวัติการลดสต็อกด้วยมือ:");
                        i.adjustments().forEach(a -> out.println(a.date() + " | " + a.lotId() + " | ลด " + a.quantity() + " | " + a.reason()));
                    }
                    case 4 -> alerts();
                    case 5 -> {
                        system.addIngredient(text("ชื่อวัตถุดิบ"), text("หน่วยนับ"), decimal("จำนวนสต็อกปกติ", false, false));
                        out.println("เพิ่มชนิดวัตถุดิบแล้ว รับเข้าล็อตเพื่อให้มีสต็อก");
                    }
                    default -> throw new AssertionError();
                }
            } catch (IllegalArgumentException | ArithmeticException e) { out.println("ทำรายการไม่ได้: " + e.getMessage()); }
        }
    }
    private void sales() {
        title("รายงานยอดขาย / ต้นทุน / กำไรขั้นต้น");
        out.println("1. ทั้งรอบ");
        out.println("2. เลือกช่วงวันที่");
        out.println("3. รายวัน");
        out.println("4. รายสัปดาห์");
        out.println("5. รายเดือน");
        out.println("0. กลับ");
        int mode = number("เลือกรายงาน", 0, 5);
        if (mode == 0) return;
        LocalDate today = system.today(), start = today, end = today;
        if (mode == 2) { start = date("วันเริ่ม"); end = date("วันสิ้นสุด"); }
        if (mode == 4) { start = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); end = start.plusDays(6); }
        if (mode == 5) { start = today.withDayOfMonth(1); end = today.withDayOfMonth(today.lengthOfMonth()); }
        Sales report = mode == 1 ? system.sessionReport() : system.salesReport(start, end);
        out.println("รายงาน " + report.salesId() + " | " + report.salesStartDate() + " ถึง " + report.salesEndDate());
        out.println("นับตามวันชำระ/วันคืนเงิน รวมวันเริ่มและวันสิ้นสุด");
        Sales.Summary summary = report.summary();
        out.println("ออร์เดอร์ชำระแล้วทั้งหมด " + summary.paidOrders() + " | ยกเลิกหลังชำระ " + summary.cancelledPaidOrders() + " | ส่งมอบ " + summary.deliveredOrders());
        out.println("ยอดสินค้าก่อนลด " + money(summary.gross()) + " บาท");
        out.println("ส่วนลดรวม " + money(summary.discounts()) + " บาท (รวมออร์เดอร์ที่คืนเงิน)");
        out.println("ยอดรับชำระ " + money(summary.payments()) + " บาท (หักเงินทอนแล้ว)");
        out.println("เงินคืน " + money(summary.refunds()) + " บาท");
        out.println("ยอดขายสุทธิ " + money(summary.netSales()) + " บาท = ยอดรับชำระ - เงินคืน");
        out.println("ต้นทุนวัตถุดิบสุทธิ " + money(summary.totalCost()) + " บาท (หักต้นทุนล็อตที่คืนแล้ว)");
        out.println("กำไรขั้นต้น " + money(report.calculateSalesProfit()) + " บาท = ยอดขายสุทธิ - ต้นทุนวัตถุดิบสุทธิ");
        out.println("ต้นทุนสาธิต ไม่รวมค่าแรงและค่าใช้จ่ายร้าน");
        out.println("สินค้าขายดี 5 อันดับ (จำนวนสุทธิหลังคืน):");
        Map<Product, Long> ranking = report.getTopSellingProducts(5);
        if (ranking.isEmpty()) out.println("ไม่มีสินค้าที่มียอดจำนวนสุทธิมากกว่า 0");
        ranking.forEach((product, qty) -> out.println(product.id() + " " + product.name() + " | " + qty + " หน่วย"));
        out.println("ร่างที่ยังไม่ชำระไม่นับเป็นยอดขาย");
    }
}
