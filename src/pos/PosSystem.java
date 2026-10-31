package pos;

import java.math.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public class PosSystem implements Serializable {
    private transient Clock clock;
    private final List<Customer> customers = new ArrayList<>();
    private final List<Member> members = new ArrayList<>();
    private final List<Product> products = new ArrayList<>();
    private final List<Ingredient> ingredients = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final Employee employee = new Employee("A001", "พนักงาน A", "พนักงานเคาน์เตอร์");
    private int reportSequence;
    private final LocalDate sessionStartDate;
    public PosSystem(Clock clock) {
        this.clock = clock.withZone(ZoneId.of("Asia/Bangkok")); sessionStartDate = today();
    }
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject(); clock = Clock.system(ZoneId.of("Asia/Bangkok"));
    }
    public static PosSystem loadOrDemo(Path file) throws IOException {
        if (!Files.exists(file)) return demo();
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            Object value = in.readObject();
            if (!(value instanceof PosSystem system)) throw new IOException("ไฟล์ข้อมูล POS ไม่ถูกต้อง");
            system.ensureIceCreamMenu();
            return system;
        } catch (ClassNotFoundException e) { throw new IOException("อ่านข้อมูล POS ไม่ได้", e); }
    }
    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) { out.writeObject(this); }
    }
    public LocalDate today() { return LocalDate.now(clock); }
    public LocalDateTime now() { return LocalDateTime.now(clock); }
    public Employee employee() { return employee; }
    public List<Member> members() { return List.copyOf(members); }
    public List<Product> products() { return List.copyOf(products); }
    public List<Ingredient> ingredients() { return List.copyOf(ingredients); }
    public List<Order> orders() { return List.copyOf(orders); }
    public Customer guest() { Customer c = new Customer("C" + (customers.size() + 1)); customers.add(c); return c; }
    public Member findMember(String phone) {
        return members.stream().filter(m -> m.phone().equals(phone)).findFirst().orElseThrow(() -> new IllegalArgumentException("ไม่พบสมาชิก"));
    }
    public Member register(String name, String phone) {
        validateMemberData(null, name, phone);
        return guest().customerRegister(this, name, phone);
    }
    Member registerCustomer(Customer customer, String name, String phone) {
        if (customer instanceof Member || !customers.contains(customer)) throw new IllegalArgumentException("ลูกค้าลงทะเบียนแล้วหรือไม่พบลูกค้า");
        validateMemberData(null, name, phone);
        Member member = new Member(customer.id(), "M" + (members.size() + 1), name, phone);
        members.add(member); customers.set(customers.indexOf(customer), member); return member;
    }
    private void validateMemberData(Member current, String name, String phone) {
        if (name == null || name.isBlank() || phone == null || !phone.matches("[0-9]{10}"))
            throw new IllegalArgumentException("ชื่อหรือเบอร์โทรไม่ถูกต้อง");
        if (members.stream().anyMatch(m -> m != current && m.phone().equals(phone)))
            throw new IllegalArgumentException("เบอร์โทรซ้ำ");
    }
    public void editMember(Member member, String name, String phone) {
        if (!members.contains(member)) throw new IllegalArgumentException("ไม่พบสมาชิก");
        validateMemberData(member, name, phone); member.adjustMember(name, phone);
    }
    public Ingredient addIngredient(String name, String unit, BigDecimal normal) {
        Ingredient i = new Ingredient("I" + (ingredients.size() + 1), name, unit, normal); ingredients.add(i); return i;
    }
    public Ingredient ingredientNamed(String name) {
        return ingredients.stream().filter(i -> i.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบวัตถุดิบ " + name));
    }
    private void ensureIceCreamMenu() {
        String[] flavors = {"คาราเมลแครก", "คุกกี้แอนด์ครีม", "ชีส", "นมข้นน้ำผึ้ง", "โยเกิร์ตสตรอเบอร์รี่ชีสเค้ก",
                "มิ้นช็อคชิพ", "อูจิมัทฉะ", "นมเปรี้ยวปีโป้", "เชอร์รี่เบอร์รี่ชอร์เบย์", "มิกซ์แอปเปิ้ล",
                "นมเมล่อน", "มอคค่าอัลมอนด์", "ช็อกโกแลต", "รัมเรซิ่น", "บลูเบอร์รี่โยเกิร์ต"};
        LocalDate d = today();
        for (String flavor : flavors) if (ingredients.stream().noneMatch(i -> i.name().equals(flavor))) {
            Ingredient i = addIngredient(flavor, "สกู๊ป", new BigDecimal("100"));
            i.addIngredient("DEMO-" + i.id(), d.minusDays(1), d.plusYears(1), new BigDecimal("100"), new BigDecimal("5"), "สต็อกเริ่มต้น");
        }
        String[][] containers = {{"ถ้วยไอศกรีม", "ถ้วย"}, {"โคนวาฟเฟิล", "ชิ้น"}, {"ถ้วยวาฟเฟิล", "ถ้วย"}};
        String[] ids = {"DEMO-CUP", "DEMO-WAFFLE-CONE", "DEMO-WAFFLE-BOWL"};
        String[] names = {containers[0][0], containers[1][0], containers[2][0]};
        String[] units = {containers[0][1], containers[1][1], containers[2][1]};
        String[] costs = {"1", "4", "6"};
        for (int n = 0; n < names.length; n++) {
            boolean exists = false;
            for (Ingredient ingredient : ingredients) if (ingredient.name().equals(names[n])) { exists = true; break; }
            if (!exists) {
                Ingredient i = addIngredient(names[n], units[n], new BigDecimal("100"));
                i.addIngredient(ids[n], d.minusDays(1), d.plusYears(1), new BigDecimal("100"), new BigDecimal(costs[n]), "สต็อกเริ่มต้น");
            }
        }
    }
    public Product addProduct(String name, String category, BigDecimal price, boolean active, Map<Ingredient, BigDecimal> recipe) {
        validateRecipe(recipe);
        Product p = new Product("P" + (products.size() + 1), name, category, price, active, recipe); products.add(p); return p;
    }
    public Product addProduct(String name, BigDecimal price, boolean active, Map<Ingredient, BigDecimal> recipe) {
        return addProduct(name, "ทั่วไป", price, active, recipe);
    }
    private void validateRecipe(Map<Ingredient, BigDecimal> recipe) {
        if (recipe == null || !ingredients.containsAll(recipe.keySet())) throw new IllegalArgumentException("สูตรต้องใช้วัตถุดิบในระบบ");
    }
    public void updateProduct(Product p, String name, String category, BigDecimal price, boolean active, Map<Ingredient, BigDecimal> recipe) {
        if (!products.contains(p)) throw new IllegalArgumentException("ไม่พบสินค้า");
        validateRecipe(recipe); p.updateProduct(name, category, price, active, recipe);
    }
    public void updateProduct(Product p, String name, BigDecimal price, boolean active, Map<Ingredient, BigDecimal> recipe) {
        updateProduct(p, name, p.category(), price, active, recipe);
    }
    public Order draft(Member member) { return (member == null ? guest() : member).customerOrder(this); }
    Order createOrder(Customer customer) {
        if (!customers.contains(customer)) throw new IllegalArgumentException("ไม่พบลูกค้า");
        Order o = new Order("O" + (orders.size() + 1), customer, employee, now()); orders.add(o); return o;
    }
    private void known(Order o) { if (!orders.contains(o)) throw new IllegalArgumentException("ไม่พบออร์เดอร์"); }
    public void pay(Order o, Order.Payment method, BigDecimal tendered, boolean qrConfirmed) {
        known(o); o.require(Order.Status.WAITING_PAYMENT);
        if (o.items().isEmpty()) throw new IllegalArgumentException("ออร์เดอร์ไม่มีสินค้า");
        if (method == null) throw new IllegalArgumentException("ต้องเลือกวิธีชำระเงิน");
        if (method == Order.Payment.QR && !qrConfirmed) throw new IllegalArgumentException("ยังไม่ได้ยืนยัน QR สำเร็จ");
        if (tendered == null || tendered.signum() < 0) throw new IllegalArgumentException("จำนวนเงินไม่ถูกต้อง");
        tendered = tendered.setScale(2, RoundingMode.UNNECESSARY);
        if (method == Order.Payment.QR && tendered.compareTo(o.net()) != 0) throw new IllegalArgumentException("QR ต้องชำระตรงยอดสุทธิ");
        if (tendered.compareTo(o.net()) < 0) throw new IllegalArgumentException("ชำระเงินไม่ครบ");
        Member member = o.customer() instanceof Member m ? m : null;
        if (member != null) member.validateDelta(-o.requestedPoints());
        Map<IngredientLot, BigDecimal> reserved = new HashMap<>();
        Map<OrderItem, List<StockAllocation>> used = new LinkedHashMap<>();
        LocalDateTime at = now();
        for (OrderItem item : o.items()) {
            List<StockAllocation> itemAllocations = new ArrayList<>();
            item.recipe().forEach((ingredient, q) -> itemAllocations.addAll(
                    ingredient.plan(q.multiply(BigDecimal.valueOf(item.quantity())), at.toLocalDate(), reserved)));
            used.put(item, itemAllocations);
        }
        // Planning only reserves quantities in a temporary map; mutations begin after all checks pass.
        if (member != null) member.redeemPointsMember(o.requestedPoints(), o.id(), "ใช้คะแนนเป็นส่วนลด", at);
        used.values().forEach(list -> list.forEach(a -> a.lot().change(a.quantity().negate())));
        o.completePayment(method, tendered, used, at);
    }
    public void start(Order o) { known(o); o.require(Order.Status.WAITING_PREPARATION); o.transition(Order.Status.PREPARING); }
    public void ready(Order o) { known(o); o.require(Order.Status.PREPARING); o.transition(Order.Status.READY); }
    public void deliver(Order o) {
        known(o); o.require(Order.Status.READY);
        int points = 0;
        if (o.customer() instanceof Member m) points = m.addPointsMember(o.net(), o.id(), "คะแนนจากยอดสุทธิเมื่อส่งมอบ", now());
        o.delivered(points);
    }
    public void cancel(Order o, String reason) {
        known(o);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("ต้องระบุเหตุผลยกเลิก");
        if (o.status() != Order.Status.WAITING_PAYMENT && o.status() != Order.Status.WAITING_PREPARATION)
            throw new IllegalArgumentException("ยกเลิกได้เฉพาะก่อนเริ่มจัดเตรียม");
        LocalDateTime at = now();
        if (o.payment() != null) {
            if (o.customer() instanceof Member m) m.returnPointsMember(o.requestedPoints(), o.id(), reason, at);
            o.allocations().forEach(a -> a.lot().change(a.quantity()));
        }
        o.cancelled(reason.trim(), at);
    }
    void cancel(Order o) { cancel(o, "ยกเลิกในชุดทดสอบ"); }
    public Sales salesReport(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw new IllegalArgumentException("ช่วงวันที่ไม่ถูกต้อง");
        return new Sales("S" + (++reportSequence), start, end, orders);
    }
    public Sales sessionReport() { return salesReport(sessionStartDate, today()); }
    public Sales.Summary summary() { return sessionReport().summary(); }
    public static PosSystem demo() {
        PosSystem s = new PosSystem(Clock.system(ZoneId.of("Asia/Bangkok")));
        s.ensureIceCreamMenu();
        Ingredient vanilla = s.addIngredient("ไอศกรีมวานิลลา", "สกู๊ป", new BigDecimal("100"));
        Ingredient chocolate = s.addIngredient("ไอศกรีมช็อกโกแลต", "สกู๊ป", new BigDecimal("100"));
        Ingredient cone = s.addIngredient("โคน", "ชิ้น", new BigDecimal("100"));
        LocalDate d = s.today();
        vanilla.addIngredient("V01", d.minusDays(5), d.plusDays(2), new BigDecimal("30"), new BigDecimal("8"), "ล็อตสาธิต");
        vanilla.addIngredient("V02", d.minusDays(1), d.plusDays(20), new BigDecimal("70"), new BigDecimal("9"), "ล็อตสาธิต");
        chocolate.addIngredient("C01", d.minusDays(3), d.plusDays(15), new BigDecimal("40"), new BigDecimal("10"), "ล็อตสาธิต");
        chocolate.addIngredient("C00", d.minusDays(10), d.minusDays(1), new BigDecimal("5"), new BigDecimal("9"), "ล็อตหมดอายุสาธิต");
        cone.addIngredient("K01", d.minusDays(3), d.plusDays(60), new BigDecimal("100"), new BigDecimal("2"), "ล็อตสาธิต");
        s.addProduct("วานิลลาโคน 1 สกู๊ป", "ไอศกรีมโคน", new BigDecimal("30"), true, Map.of(vanilla, BigDecimal.ONE, cone, BigDecimal.ONE));
        s.addProduct("ช็อกโกแลตโคน 1 สกู๊ป", "ไอศกรีมโคน", new BigDecimal("35"), true, Map.of(chocolate, BigDecimal.ONE, cone, BigDecimal.ONE));
        s.addProduct("โคนสองรส", "ไอศกรีมโคน", new BigDecimal("55"), true, Map.of(vanilla, BigDecimal.ONE, chocolate, BigDecimal.ONE, cone, BigDecimal.ONE));
        s.register("สมาชิกสาธิต", "0812345678").initializeDemoPoints(100, s.now());
        return s;
    }
}
