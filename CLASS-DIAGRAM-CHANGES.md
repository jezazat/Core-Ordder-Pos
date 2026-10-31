# จุดแก้ไข Class Diagram และโปรแกรม

เทียบกับภาพคลาสทั้ง 7 ที่ผู้ใช้ส่งล่าสุด แก้เพื่อให้แผนภาพ ชนิดข้อมูล และการทำงาน Java สอดคล้องกัน โดยคงกติกาธุรกิจที่ยืนยันไว้ก่อนหน้า

## ก่อนแก้ → หลังแก้ → เหตุผล

| คลาส | ในภาพเดิม | หลังแก้ | เหตุผล |
|---|---|---|---|
| Member | `memberpointBalance: Int` | `memberPointBalance: int` | ใช้ชื่อ camelCase และชนิด Java สำหรับคะแนนจำนวนเต็ม |
| Member | `memberpointHistory: String` | `memberPointHistory: List<PointTransaction>` | ประวัติมีหลายรายการ ต้องเก็บชนิดรายการ เวลา จำนวนคะแนน ออร์เดอร์ เหตุผล และยอดหลังรายการ |
| Member | `adjustMember(): String` | `adjustMember(name, phone): void` | ต้องรับข้อมูลที่จะแก้ ชื่อ/เบอร์ไม่ใช่ผลลัพธ์ข้อความ |
| Member | `addPointsMember(amountSpent)` | เพิ่มออร์เดอร์ เหตุผล และเวลา; ผลลัพธ์ `int` | คำนวณจากยอดสุทธิเมื่อส่งมอบและบันทึกประวัติให้ตรวจสอบได้ |
| Member | `redeemPointsMember(points): Boolean` | รับคะแนน ออร์เดอร์ เหตุผล เวลา; ผลลัพธ์ `void` | ข้อมูลผิดใช้ exception พร้อมข้อความ หน้าจอแสดงเหตุผลได้ และต้องบันทึกประวัติการใช้ |
| Member | `returnPointsMember(delta: Decimal, reason)` | คะแนน `int` พร้อมออร์เดอร์ เหตุผล เวลา; ผลลัพธ์ `void` | คะแนนไม่ใช่เงิน ต้องคืนตรงจำนวนที่ใช้ในออร์เดอร์นั้น และป้องกันคืนซ้ำ |
| Member | เมธอดคะแนน/แก้ข้อมูลเป็น public | เมธอดเปลี่ยนข้อมูลเป็น package (`~` ใน UML); อ่านประวัติเป็น public | ให้ PosSystem ตรวจเบอร์ซ้ำ สถานะ และข้อมูลหลายวัตถุร่วมกันก่อนเปลี่ยนข้อมูล |
| Customer | `customerId` และสมาชิกแยกคลาส | คง `customerId`; `Member extends Customer` และมี `memberId` แยก | ลูกค้าสมาชิกเป็นลูกค้าชนิดหนึ่ง รหัสลูกค้ากับรหัสสมาชิกมีหน้าที่ต่างกัน |
| Customer | `customerRegister(): Boolean` | `customerRegister(system, name, phone): Member` | รับข้อมูลสมัครครบและคืนสมาชิกที่สร้างสำเร็จ |
| Customer | `customerOrder(): String` | `customerOrder(system): Order` | คืนออร์เดอร์ที่เพิ่มสินค้าและชำระต่อได้จริง |
| Customer | `customerPayment(amount): Boolean` | รับ system, order, วิธีชำระ, amount, ผลยืนยัน QR; ผลลัพธ์ `void` | ต้องรู้ว่าจะชำระออร์เดอร์ใด และตรวจว่าลูกค้าเป็นเจ้าของออร์เดอร์ |
| Employee | `employeerole` | `employeeRole` | แก้ชื่อเป็น camelCase และเพิ่มข้อมูลบทบาทในเมนู |
| Employee | กล่องขาด `prepareEmployeeProduct` แต่คำอธิบายมี | เพิ่ม `prepareEmployeeProduct(system, order)` และ `finishEmployeeProduct(system, order)` | แยกเริ่มทำกับเตรียมเสร็จให้ตรงเงื่อนไขยกเลิก |
| Employee | เมธอดรับ/บันทึก/ส่งมอบไม่มีพารามิเตอร์; ยืนยันรับแค่ orderId | เติมข้อมูลออร์เดอร์ สินค้า สูตร และการชำระที่ใช้จริง | เมธอดทำงานผ่าน PosSystem ได้จริง ไม่เป็นเมธอดว่าง |
| Order | `ordertotalAmount`, `orderdiscountAmount`, `ordernetAmount` เป็น Decimal ที่เก็บแยก | `/orderTotalAmount`, `/orderDiscountAmount`, `/orderNetAmount`: BigDecimal คำนวณจากรายการและคะแนน | ป้องกันยอดที่เก็บแยกไม่ตรงกัน; `/` ใน UML หมายถึง derived property |
| Order | `orderDateTime: DateTime` | `LocalDateTime` เพิ่ม `paidDateTime`, `cancelledDateTime`, `cancelReason` | รองรับรายงานตามวันทำรายการและเหตุผลคืนเงิน |
| Order | `orderStatus: String`, เปลี่ยนสถานะอิสระ | `Order.Status` enum และเปลี่ยนผ่านสถานะที่ถูกต้องเท่านั้น | ห้ามข้ามการชำระ/การเตรียม หรือย้อนสถานะเพื่อคืนซ้ำ |
| Order | `applyOrderDiscount(discount: Decimal)` | `applyOrderDiscount(points: int)` | ส่วนลดนี้มาจากคะแนน ต้องตรวจชุดละ 10 คะแนนและยอดคงเหลือ |
| Order | `cancelOrder(reason): Boolean` | `cancelOrder(system, reason): void` | คืนคะแนนและสต็อกหลายวัตถุต้องประสานผ่าน system และบังคับเหตุผล |
| Sales | `salesTotalCost` ซ้ำสองครั้ง ครั้งหนึ่งอธิบายว่าเป็นกำไร | แยก `salesTotalCost` และ `salesProfit` | ต้นทุนและกำไรเป็นคนละค่า |
| Sales | constructor `SalesSummary()` ในคลาส Sales | `Sales(id, startDate, endDate, orders)` | Constructor ต้องชื่อเดียวกับคลาสและรับข้อมูลที่จำเป็น |
| Sales | วันที่และยอดรวมในภาพแต่โปรแกรมเดิมไม่มี | เพิ่มรหัสรายงาน ช่วงวันที่ ต้นทุน กำไร อันดับขายดี | รองรับ `calculateSales`, `calculateSalesProfit`, `getTopSellingProducts` ตามภาพ |
| Sales | `getTopSellingProducts(): Map` ไม่ระบุชนิด | `Map<Product, Long>` | ระบุชนิดสินค้าและจำนวนรวม; จำนวนหลายออร์เดอร์ใช้ Long เพื่อไม่จำกัดเท่า int ต่อรายการ |
| Product | `calculatePriceDiscount()` ในกล่อง แต่คำอธิบายเป็นต้นทุน BOM | `calculateCostProduct(date): BigDecimal` | หน้าที่คือคำนวณต้นทุนจากสูตรและล็อต FIFO; ส่วนลดคะแนนเป็นหน้าที่ Order |
| Product | `updateproduct(productId, productne, ...)` | `updateProduct(name, category, price, available, recipe)` | แก้ชื่อผิด รหัสคงเดิม ไม่ต้องรับ ID ซ้ำใน object เดิม และแก้สูตรได้ |
| Product | หมวดสินค้าในภาพ แต่โปรแกรมเดิมไม่มี | เพิ่ม `productCategory`; ชื่ออื่นใช้ `productId`, `productName`, `productBasePrice`, `productIsAvailable` | ให้ Attributes และเมนูตรงกับภาพ |
| Ingredient | `ingredientstockQuantity` เก็บยอดเดียว | `/ingredientStockQuantity` คือผลรวมล็อตที่ใช้ขายได้ ณ วันที่ตรวจ | ไม่เก็บยอดซ้ำกับล็อต และไม่นับล็อตหมดอายุหรือยังไม่ถึงวันรับเข้า |
| Ingredient | `ingredientcostPerUnit`, `ingredientexpireDate` อยู่ระดับชนิดวัตถุดิบ | `ingredientCostPerUnit`, `ingredientExpireDate` อยู่ใน IngredientLot | วัตถุดิบชนิดเดียวมีหลายล็อตที่ต้นทุน/วันหมดอายุต่างกันได้ |
| Ingredient | `ingredientreorderPoint` ค่าระบุเอง | `/ingredientReorderPoint = ingredientNormalStock × 0.5` | คงกติกาเตือนต่ำกว่า 50%; เท่ากับเกณฑ์ไม่เตือน |
| Ingredient | `addIngredient(delta, reason)` | รับรหัสล็อต วันรับเข้า วันหมดอายุ จำนวน ต้นทุน เหตุผล | ข้อมูลครบสำหรับ FIFO และบันทึกเหตุผลรับเข้ากับล็อต |
| Ingredient | `deleteIngredient(delta, reason)` | `reduceIngredient(lotId, quantity, reason, date)` | เป็นการลดจำนวน ไม่ใช่ลบวัตถุดิบ ต้องระบุล็อตและเก็บเหตุผล |
| ทุกคลาส | `Decimal`, `Date`, `DateTime`, `Int`, `Void` | `BigDecimal`, `LocalDate`, `LocalDateTime`, `int`, `void` | ใช้ชนิดข้อมูล Java จริง Constructor ไม่มี return type |

## ไดอะแกรมฉบับแก้ใหม่: 7 คาร์ดเท่านั้น

ทั้งหน้า Overview และ Attributes & Operations แสดงเฉพาะ Member, Customer, Employee, Order, Sales, Product และ Ingredient ตามภาพที่ผู้ใช้ส่ง ไม่แสดงคาร์ด OrderItem, RecipeItem, IngredientLot, StockAllocation หรือ PointTransaction เพิ่ม

รายละเอียดรายการขาย สูตร ล็อต และประวัติคะแนนยังใช้ภายในโค้ดเพื่อให้ระบบทำงานถูกต้อง แสดงเป็น attributes หรือคำอธิบายในคลาสหลักโดยไม่มีคาร์ดแยก

- **Composition — Order ◆ Product:** Product ที่อยู่ในออร์เดอร์เป็นสำเนาข้อมูลสินค้าเฉพาะรายการนั้น (`Order.orderProducts()`); แต่ละออร์เดอร์ได้สำเนาคนละ object และสำเนาแก้ไขไม่ได้ สินค้าหลักในร้านยังอยู่เมื่อยกเลิกออร์เดอร์
- **Aggregation — Sales ◇ Order:** รายงานรวมอ้างอิงออร์เดอร์ หลายรายงานใช้ออร์เดอร์เดียวกันได้
- **Aggregation — Product ◇ Ingredient:** สินค้าใช้วัตถุดิบตามสูตร วัตถุดิบหนึ่งชนิดใช้ได้หลายสินค้าและมีชีวิตอิสระจากสินค้า
- **Inheritance — Member → Customer:** สมาชิกเป็นลูกค้าประเภทหนึ่ง
- **Association — Customer–Order / Employee–Order:** ลูกค้าสั่งซื้อและพนักงานรับออร์เดอร์

Multiplicity Order–Product เป็น 1 กับ 0..* เพราะรองรับร่างว่าง ชำระแล้วต้องมีอย่างน้อย 1 รายการ Product–Ingredient เป็น 0..* กับ 1..* เพราะสูตรสินค้าต้องมีวัตถุดิบและใช้วัตถุดิบร่วมกันได้

| สิ่งที่แก้จากรุ่นก่อน | รุ่นใหม่ | เหตุผล |
|---|---|---|
| ไดอะแกรมมี 12 คาร์ด | เหลือ 7 คาร์ดต่อหน้า และมีเพียง 7 ชื่อคลาสในไฟล์ draw.io | ยึดจำนวนและชื่อคาร์ดตามภาพที่ผู้ใช้ส่ง |
| Composition เชื่อมไปคาร์ดรายละเอียดเพิ่มเติม | Composition Order–Product ที่เป็นสำเนาเฉพาะออร์เดอร์ | แสดง Composition ภายใน 7 คลาสโดยรักษาสินค้าหลักในร้าน |
| รายการขายอ้างสินค้าหลักโดยไม่มีสำเนา Product object | เพิ่มสำเนา Product อ่านอย่างเดียวต่อรายการ | โค้ดตรงกับความเป็นเจ้าของใน UML และไม่เปลี่ยนข้อมูลย้อนหลัง |
| ข้อมูลล็อต/ประวัติคะแนนเป็นคาร์ดแยก | เป็นรายละเอียดภายใน attributes/โค้ด | ไม่เพิ่มคาร์ดไดอะแกรมเกิน 7 |

## กติกาที่คงไว้และการตีความรายงาน

คะแนนคิดจากยอดสุทธิเมื่อส่งมอบ ใช้คะแนนชุดละ 10 แลกลดชุดละ 10 บาท คืนคะแนนที่ใช้และวัตถุดิบเฉพาะก่อนเริ่มจัดเตรียม ตัด FIFO หลังชำระครบ ข้ามล็อตหมดอายุ รับเงินสดหรือ QR จำลอง

รายงานนับวันชำระและวันยกเลิกแยกกัน การคืนข้ามวันจึงทำให้ช่วงวันคืนมียอดสุทธิ/ต้นทุน/กำไรติดลบได้ ซึ่งเป็นการกลับรายการของวันนั้น ไม่ย้ายคืนไปวันขาย กำไรนี้เป็นกำไรขั้นต้นจากวัตถุดิบ ยังไม่หักค่าแรงหรือค่าใช้จ่ายอื่น

`orderTotalAmount` หมายถึงยอดก่อนส่วนลด ส่วน `calculateOrderTotal()` คืนยอดสุทธิที่ต้องชำระ ชื่อ derived properties ใน diagram แสดงคู่กับชนิดข้อมูลและมีเมธอดชื่อเดียวกันใน Java

Diagram แสดง Attributes ของ 7 คลาสหลักและ Operations ที่เกี่ยวข้องกับโดเมนจาก `javap` ของคลาสที่คอมไพล์จริง ไม่รวม getters ทุกตัวหรือเมธอดช่วยภายในทั้งหมด `Employee`, `RecipeItem`, `StockAllocation`, `PointTransaction` เป็น Java records ซึ่งมี fields private final และ constructor ตามที่แสดง

โค้ดคงตัวช่วยชื่อสั้นบางตัวสำหรับหน้าจอและชุดทดสอบเดิม เช่น `price`, `subtotal`, `net`, `usable`, `receive` โดย `receive` แบบเก่าใช้ต้นทุน 0 สำหรับ fixture เท่านั้น เมนูรับเข้าจริงใช้ `addIngredient` และรับต้นทุน/เหตุผลครบ เมธอดไม่มีเหตุผล `cancel` และ `changePoints` ใช้สำหรับชุดทดสอบที่ระบุเหตุผลอัตโนมัติ; หน้าจอไม่ใช้ทางลัดเหล่านี้
