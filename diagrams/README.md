# Class Diagram — 7 คาร์ดตามภาพ

ไฟล์ `pos-class-diagram.drawio` มีสองหน้า แต่ละหน้าแสดงเพียง 7 คาร์ด: Member, Customer, Employee, Order, Sales, Product และ Ingredient ไม่มีคาร์ดรายละเอียดเพิ่ม

- `overview.png` / `overview.svg`: ภาพรวมพร้อม attributes และ operations หลัก ความสัมพันธ์ Composition 1 เส้น, Aggregation 2 เส้น, Inheritance 1 เส้น และ Association 2 เส้น
- `details.png` / `details.svg`: รายละเอียด Attributes และ Operations ของ 7 คลาสที่อ่านจาก Java ที่คอมไพล์จริง
- `compiled-model.json`: เมทาดาทาของ 7 คลาสที่ใช้สร้างภาพ
- `layout-check.json`: ตรวจจำนวนคาร์ดต้องเท่ากับ 7 และไม่มีข้อความล้น

เปิดไฟล์ draw.io ด้วย diagrams.net → File → Open From → Device กล่องคลาสเป็นกลุ่มที่ย้ายพร้อมหัวและเนื้อหาได้ เส้นยึดกับกล่องคลาส

เพชรทึบ Order–Product หมายถึงสำเนาข้อมูลสินค้าเฉพาะออร์เดอร์ สินค้าหลักยังอยู่เมื่อยกเลิก เพชรโปร่ง Sales–Order คือการรวมรายงาน และ Product–Ingredient คือวัตถุดิบที่ใช้ร่วมกันตามสูตร

ชื่อ PointTransaction, IngredientLot หรือชนิดข้อมูลรายละเอียดที่ปรากฏใน attributes เป็นข้อมูลภายในโปรแกรม ไม่มีคาร์ดคลาสเพิ่มในไดอะแกรม

SVG เป็น vector สำหรับขยาย PNG ภาพรวม 2260×1740 และรายละเอียด 2400×2166 พิกเซล

สร้างภาพใหม่: รัน build.ps1 แล้ว scripts/build-diagrams.py และ scripts/render-diagrams.cjs ตรวจโครงสร้างด้วย scripts/verify-diagrams.py โปรแกรม Java ใช้งานปกติไม่ต้องใช้ Python หรือ Node
