"""Build editable draw.io UML and matching SVGs from the compiled Java signatures."""
from pathlib import Path
import json, re, subprocess, textwrap, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'diagrams'
OUT.mkdir(exist_ok=True)
JAVAP = Path.home() / '.vscode/extensions/redhat.java-1.56.0-win32-x64/jre/21.0.12.1-win32-x86_64/bin/javap.exe'
if not JAVAP.exists():
    import shutil
    JAVAP = shutil.which('javap')
if not JAVAP:
    raise SystemExit('JDK javap is required; compile with build.ps1 first')

CLASSES = {
    'Member': ('สมาชิก', ['adjustMember', 'addPointsMember', 'redeemPointsMember', 'returnPointsMember', 'getPointHistoryMember']),
    'Customer': ('ลูกค้า', ['customerRegister', 'customerOrder', 'customerPayment']),
    'Employee': ('พนักงาน', ['receiveEmployeeProduct', 'saveEmployeeProduct', 'recordEmployeeConfirmation', 'prepareEmployeeProduct', 'finishEmployeeProduct', 'deliverEmployeeProduct']),
    'Order': ('ออร์เดอร์', ['addOrderItem', 'calculateOrderTotal', 'applyOrderDiscount', 'cancelOrder', 'updateOrderStatus', 'totalCost']),
    'Sales': ('รายงานยอดขาย', ['calculateSales', 'calculateSalesProfit', 'getTopSellingProducts']),
    'Product': ('สินค้า', ['getSalePrice', 'calculateCostProduct', 'updateProductStatus', 'updateProduct']),
    'Ingredient': ('วัตถุดิบ', ['getAvailableQuantityIngredient', 'addIngredient', 'reduceIngredient', 'isLowIngredient', 'isNearExpiryIngredient', 'ingredientReorderPoint']),
}

def simple(s):
    for prefix in ('java.lang.', 'java.math.', 'java.time.', 'java.util.', 'pos.'):
        s = s.replace(prefix, '')
    return s.replace('$', '.')

def model(name):
    raw = subprocess.check_output([str(JAVAP), '-private', '-classpath', 'build/classes', 'pos.' + name], cwd=ROOT, text=True, encoding='utf-8')
    fields, methods, found = [], [], set()
    for line in raw.splitlines():
        line = line.strip()
        field = re.match(r'private (?:final )?([^;()]+) ([\w]+);$', line)
        if field:
            fields.append('- ' + field[2] + ': ' + simple(field[1]))
        method = re.match(r'(?:(public|protected|private) )?(?:static )?([^;()]+)\((.*)\);$', line)
        if not method:
            continue
        visibility, head, params = method.groups()
        head = simple(head)
        symbol = '+' if visibility == 'public' else '#' if visibility == 'protected' else '-' if visibility == 'private' else '~'
        if head == name:
            methods.append(symbol + ' ' + name + '(' + simple(params) + ')')
        elif ' ' in head:
            returns, op = head.rsplit(' ', 1)
            if op in CLASSES[name][1]:
                methods.append(symbol + ' ' + op + '(' + simple(params) + '): ' + returns)
                found.add(op)
    missing = set(CLASSES[name][1]) - found
    if missing:
        raise ValueError(f'Missing compiled methods in {name}: {missing}')
    if name == 'Order':
        fields.extend(['/ orderTotalAmount: BigDecimal', '/ orderDiscountAmount: BigDecimal', '/ orderNetAmount: BigDecimal', '/ orderProducts: List<Product> (owned snapshots)'])
    if name == 'Ingredient':
        fields.extend(['/ ingredientStockQuantity: BigDecimal (date)', '/ ingredientReorderPoint: BigDecimal'])
    return dict(name=name, thai=CLASSES[name][0], fields=fields, methods=methods)

MODELS = {n: model(n) for n in CLASSES}
(OUT / 'compiled-model.json').write_text(json.dumps(MODELS, ensure_ascii=False, indent=2), encoding='utf-8')

NS = 'http://www.w3.org/2000/svg'
ET.register_namespace('', NS)
GREEN, PAPER, INK, MUTED = '#173f35', '#fffced', '#193b33', '#61716a'

def svg_tag(parent, tag, attrs=None, text=None):
    node = ET.SubElement(parent, '{' + NS + '}' + tag, {k: str(v) for k, v in (attrs or {}).items()})
    node.text = text
    return node

def svg_text(parent, x, y, text, size=18, fill=INK, weight='normal', owner=None, mono=False):
    attrs = dict(x=x, y=y, fill=fill, **{'font-size': size, 'font-family': 'Consolas, monospace' if mono else 'Tahoma, Arial, sans-serif', 'font-weight': weight})
    if owner:
        attrs['data-owner'] = owner
    return svg_tag(parent, 'text', attrs, text)

def cell(root, id, value, style, x, y, w, h, parent='1'):
    c = ET.SubElement(root, 'mxCell', id=id, value=value, style=style, vertex='1', parent=parent)
    ET.SubElement(c, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h), **{'as': 'geometry'})
    return c

def svg_root(w, h, title, subtitle):
    s = ET.Element('{' + NS + '}svg', width=str(w), height=str(h), viewBox=f'0 0 {w} {h}')
    svg_tag(s, 'rect', dict(x=0, y=0, width=w, height=h, fill=PAPER))
    svg_text(s, 55, 56, title, 32, weight='bold')
    svg_text(s, 55, 92, subtitle, 18, fill=MUTED)
    return s

def mx_root(w, h):
    m = ET.Element('mxGraphModel', dx=str(w), dy=str(h), grid='1', gridSize='10', page='1', pageScale='1', pageWidth=str(w), pageHeight=str(h), background=PAPER)
    r = ET.SubElement(m, 'root'); ET.SubElement(r, 'mxCell', id='0'); ET.SubElement(r, 'mxCell', id='1', parent='0')
    return m, r

TEXT_STYLE = 'text;html=0;align=left;verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontFamily=Tahoma;fontColor=' + INK + ';spacingLeft=0;spacingTop=0;'

def text_cell(root, id, text, x, y, w, h, size=18, color=INK, parent='1'):
    return cell(root, id, text, TEXT_STYLE + f'fontSize={size};fontColor={color};', x, y, w, h, parent)

def card(svg, mx, name, x, y, w, lines, detail=False):
    header = 48
    line_h = 24 if detail else 26
    h = header + 20 + line_h * len(lines) + 12
    if not detail:
        h = max(h, 130)
    svg_tag(svg, 'rect', dict(id='card-' + name, x=x, y=y, width=w, height=h, fill='white', stroke=GREEN, **{'stroke-width': 2}))
    svg_tag(svg, 'rect', dict(x=x, y=y, width=w, height=header, fill=GREEN))
    title_size = 18 if not detail and len(name) >= 13 else 22
    svg_text(svg, x+16, y+32, name + ' / ' + MODELS[name]['thai'], title_size, 'white', 'bold', name)
    cell(mx, name, '', 'group;', x, y, w, h)
    cell(mx, name+'-box', '', f'rounded=0;fillColor=#ffffff;strokeColor={GREEN};strokeWidth=2;', 0, 0, w, h, name)
    cell(mx, name+'-header-bg', '', f'fillColor={GREEN};strokeColor={GREEN};', 0, 0, w, header, name)
    text_cell(mx, name+'-title', name+' / '+MODELS[name]['thai'], 16, 10, w-32, 32, title_size, '#ffffff', name)
    contents = text_cell(mx, name+'-contents', '\n'.join(lines), 16, header+12, w-32, h-header-18, 16 if detail else 18, parent=name)
    if detail: contents.set('style', contents.get('style').replace('fontFamily=Tahoma', 'fontFamily=Consolas'))
    for index, line in enumerate(lines):
        if line == '---':
            yy = y+header+20+index*line_h
            svg_tag(svg, 'line', dict(x1=x+16, x2=x+w-16, y1=yy-5, y2=yy-5, stroke='#c5d4cb'))
        else:
            svg_text(svg, x+16, y+header+29+index*line_h, line, 16 if detail else 18, owner=name, mono=detail)
    return (x, y, w, h)

def route(svg, mx, eid, source, target, points, kind, label, start, end):
    color = GREEN
    svg_tag(svg, 'polyline', dict(points=' '.join(f'{x},{y}' for x,y in points), fill='none', stroke=color, **{'stroke-width': 2.3}))
    def vector(a, b):
        dx,dy=b[0]-a[0], b[1]-a[1]; norm=(dx*dx+dy*dy)**.5
        return dx/norm,dy/norm
    a,b=points[0],points[1]; dx,dy=vector(a,b); px,py=-dy,dx
    if kind in ('composition','aggregation'):
        vertices=[a, (a[0]+dx*12+px*7,a[1]+dy*12+py*7), (a[0]+dx*24,a[1]+dy*24), (a[0]+dx*12-px*7,a[1]+dy*12-py*7)]
        svg_tag(svg,'polygon',dict(points=' '.join(f'{x},{y}' for x,y in vertices),fill=color if kind=='composition' else 'white',stroke=color,**{'stroke-width':2}))
    if kind=='inheritance':
        tip=points[-1]; dx,dy=vector(points[-2],tip); px,py=-dy,dx
        vertices=[tip,(tip[0]-dx*20+px*11,tip[1]-dy*20+py*11),(tip[0]-dx*20-px*11,tip[1]-dy*20-py*11)]
        svg_tag(svg,'polygon',dict(points=' '.join(f'{x},{y}' for x,y in vertices),fill='white',stroke=color,**{'stroke-width':2}))
    if label:
        # Put the relationship label in the middle of the longest route segment.
        segment=max(zip(points,points[1:]),key=lambda pair: abs(pair[1][0]-pair[0][0])+abs(pair[1][1]-pair[0][1]))
        xx=(segment[0][0]+segment[1][0])/2; yy=(segment[0][1]+segment[1][1])/2
        label_w=len(label)*9+12
        svg_tag(svg,'rect',dict(x=xx-label_w/2,y=yy-24,width=label_w,height=25,fill=PAPER))
        svg_text(svg,xx-label_w/2+6,yy-6,label,16)
    for value,point,adjacent in [(start,points[0],points[1]),(end,points[-1],points[-2])]:
        if not value: continue
        dx,dy=vector(point,adjacent)
        xx = point[0]+dx*45-(len(value)*10 if dx<0 else 0)+(10 if dx>=0 else 0)
        svg_text(svg,xx,point[1]+dy*30-8,value,17,weight='bold')
    arrow='startArrow=diamond;startFill=1;' if kind=='composition' else 'startArrow=diamond;startFill=0;' if kind=='aggregation' else 'endArrow=block;endFill=0;' if kind=='inheritance' else 'endArrow=none;'
    style=f'edgeStyle=orthogonalEdgeStyle;rounded=0;html=0;strokeColor={color};strokeWidth=2;fontFamily=Tahoma;fontSize=16;labelBackgroundColor={PAPER};'+arrow
    if kind in ('composition','aggregation'): style+='endArrow=none;'
    c=ET.SubElement(mx,'mxCell',id=eid,value=label,style=style,edge='1',parent='1',source=source,target=target)
    g=ET.SubElement(c,'mxGeometry',relative='1',**{'as':'geometry'})
    arr=ET.SubElement(g,'Array',**{'as':'points'})
    for x,y in points[1:-1]: ET.SubElement(arr,'mxPoint',x=str(x),y=str(y))
    for suffix,value,pos in [('source',start,-.88),('target',end,.88)]:
        if not value: continue
        lab=ET.SubElement(mx,'mxCell',id=eid+'-'+suffix,value=value,style='edgeLabel;html=0;align=center;verticalAlign=middle;resizable=0;fontSize=17;fontFamily=Tahoma;',vertex='1',connectable='0',parent=eid)
        geom=ET.SubElement(lab,'mxGeometry',x=str(pos),y='-15',relative='1',**{'as':'geometry'})
        ET.SubElement(geom,'mxPoint',**{'as':'offset'})

def overview():
    w,h=2260,1740
    title='POS — Class Diagram: Composition & Aggregation'
    subtitle='7 คาร์ดตามภาพ: Member / Customer / Employee / Order / Sales / Product / Ingredient'
    svg=svg_root(w,h,title,subtitle)
    graph,mx=mx_root(w,h)
    text_cell(mx,'title',title,55,22,2150,46,32)
    text_cell(mx,'subtitle',subtitle,55,75,2150,30)
    specs={
        'Member':(60,160,560,[
            '- memberId: String', '- memberName: String', '- memberPhone: String',
            '- memberPointBalance: int', '- memberPointHistory: List<PointTransaction>',
            '---', 'Member(...)', 'adjustMember(...): void', 'addPointsMember(...): int', 'redeemPointsMember(...): void',
            'getPointHistoryMember(): List<PointTransaction>', 'returnPointsMember(...): void']),
        'Customer':(60,630,560,[
            '- customerId: String', '---', 'Customer(...)', 'customerRegister(...): Member',
            'customerOrder(...): Order', 'customerPayment(...): void']),
        'Employee':(60,1100,560,[
            '- employeeId: String', '- employeeName: String', '- employeeRole: String',
            '---', 'Employee(...)', 'receiveEmployeeProduct(...): Order', 'saveEmployeeProduct(...): Product', 'recordEmployeeConfirmation(...): void',
            'prepareEmployeeProduct(...): void', 'deliverEmployeeProduct(...): void']),
        'Order':(820,390,520,[
            '- orderId: String', '- orderDateTime: LocalDateTime',
            '/ orderTotalAmount: BigDecimal', '/ orderDiscountAmount: BigDecimal',
            '/ orderNetAmount: BigDecimal', '- orderStatus: Order.Status', '---', 'Order(...)',
            'addOrderItem(...): void', 'calculateOrderTotal(): BigDecimal',
            'applyOrderDiscount(...): void', 'cancelOrder(...): void',
            'updateOrderStatus(...): void']),
        'Sales':(820,1110,520,[
            '- salesId: String', '- salesStartDate: LocalDate', '- salesEndDate: LocalDate',
            '- salesTotal: BigDecimal', '- salesTotalCost: BigDecimal',
            '- salesProfit: BigDecimal', '- salesOrderCount: int', '---', 'Sales(...)',
            'calculateSales(...): BigDecimal', 'calculateSalesProfit(): BigDecimal',
            'getTopSellingProducts(...): Map']),
        'Product':(1660,750,520,[
            '- productId: String', '- productName: String', '- productCategory: String',
            '- productBasePrice: BigDecimal', '- productIsAvailable: boolean', '---', 'Product(...)',
            'getSalePrice(): BigDecimal', 'calculateCostProduct(...): BigDecimal',
            'updateProductStatus(...): void', 'updateProduct(...): void']),
        'Ingredient':(1660,160,520,[
            '- ingredientId: String', '- ingredientName: String', '- ingredientUnit: String',
            '/ ingredientStockQuantity: BigDecimal', '- ingredientNormalStock: BigDecimal',
            '/ ingredientReorderPoint: BigDecimal', '---', 'Ingredient(...)',
            'getAvailableQuantityIngredient(...): BigDecimal',
            'addIngredient(...): void', 'reduceIngredient(...): void',
            'isLowIngredient(...): boolean', 'isNearExpiryIngredient(...): boolean']),
    }
    links=[
        ('r1','Member','Customer',[(340,552),(340,630)],'inheritance','เป็นลูกค้าประเภทหนึ่ง','',''),
        ('r2','Customer','Order',[(620,735),(700,735),(700,586),(820,586)],'association','สั่งซื้อ','1','0..*'),
        ('r3','Employee','Order',[(620,1244),(760,1244),(760,730),(820,730)],'association','รับออร์เดอร์','1','0..*'),
        ('r4','Sales','Order',[(1080,1110),(1080,808)],'aggregation','สรุปรวมยอดขาย','0..*','0..*'),
        ('r5','Order','Product',[(1340,586),(1480,586),(1480,920),(1660,920)],'composition','สินค้าสำเนาในออร์เดอร์','1','0..*'),
        ('r6','Product','Ingredient',[(1920,750),(1920,578)],'aggregation','ใช้วัตถุดิบตามสูตร','0..*','1..*'),
    ]
    for args in links: route(svg,mx,*args)
    for name,(x,y,bw,lines) in specs.items(): card(svg,mx,name,x,y,bw,lines)
    notes=[
        '◆ Composition: Order เป็นเจ้าของสำเนา Product ของรายการขาย; สินค้าหลักในร้านยังอยู่เมื่อยกเลิกออร์เดอร์',
        '◇ Aggregation: Sales รวม Order และ Product ใช้ Ingredient ร่วมกันได้ | △ Inheritance: Member เป็น Customer',
        'ร่างมีสินค้าได้ 0 รายการ; ก่อนชำระต้องมีอย่างน้อย 1 | (...) ย่อพารามิเตอร์ รายละเอียดดูหน้า Attributes & Operations',
    ]
    for idx,note in enumerate(notes):
        svg_text(svg,55,1615+idx*34,note,18)
        text_cell(mx,'legend-'+str(idx),note,55,1591+idx*34,2150,30,18)
    return svg,graph

def detail():
    width=2400; card_w=740; gap=35; x0=55; y=150
    wrapped={}
    row_heights=[]
    names=list(CLASSES)
    for name,data in MODELS.items():
        lines=['ATTRIBUTES']
        for field in data['fields']: lines.extend(textwrap.wrap(field, width=76, subsequent_indent='  '))
        lines.extend(['---','OPERATIONS (selected domain API)'])
        for op in data['methods']: lines.extend(textwrap.wrap(op, width=76, subsequent_indent='  '))
        wrapped[name]=lines
    rows = (len(names) + 2) // 3
    for row in range(rows):
        row_heights.append(max(48+20+24*len(wrapped[n])+12 for n in names[row*3:row*3+3]))
    height=y+sum(row_heights)+gap*(rows-1)+170
    svg=svg_root(width,height,'POS — Attributes & Operations: 7 Classes','เฉพาะ Member, Customer, Employee, Order, Sales, Product, Ingredient | ยึดคาร์ดจากภาพที่ส่งมา')
    graph,mx=mx_root(width,height)
    text_cell(mx,'title','POS — Attributes & Operations: 7 Classes',55,22,2300,46,32)
    text_cell(mx,'subtitle','เฉพาะ 7 คาร์ดตามภาพ | ชื่อและชนิดข้อมูลอ่านจากคลาสที่คอมไพล์จริง',55,75,2300,30)
    for row in range(rows):
        for col,name in enumerate(names[row*3:row*3+3]):
            card(svg,mx,name,x0+col*(card_w+gap),y,card_w,wrapped[name],detail=True)
        y+=row_heights[row]+gap
    notes=['+ public   - private   ~ package   / derived: คำนวณจากข้อมูลต้นทาง   |   constructors ไม่มี return type',
           'เมธอดธุรกรรมคะแนนเป็น package เพื่อให้ PosSystem คุมลำดับและตรวจข้อมูลร่วมกัน; ไม่เปิดให้หน้าจอเพิ่ม/คืนคะแนนโดยตรง',
           'ข้อมูลล็อต ประวัติคะแนน และรายการขายเป็นรายละเอียดภายในโปรแกรม; ไม่เพิ่มเป็นคาร์ดไดอะแกรม']
    for idx,note in enumerate(notes):
        svg_text(svg,55,y+20+idx*32,note,18)
        text_cell(mx,'legend-'+str(idx),note,55,y-4+idx*32,2300,30,18)
    return svg,graph

document=ET.Element('mxfile',host='app.diagrams.net',version='24.7.17',type='device')
for name,build in [('overview',overview),('details',detail)]:
    svg,graph=build()
    ET.ElementTree(svg).write(OUT/f'{name}.svg',encoding='utf-8',xml_declaration=True)
    diagram=ET.SubElement(document,'diagram',id=name,name='Overview — Composition & Aggregation' if name=='overview' else 'Attributes & Operations')
    diagram.append(graph)
ET.ElementTree(document).write(OUT/'pos-class-diagram.drawio',encoding='utf-8',xml_declaration=True)
print('Generated 2 SVG pages + editable 2-page draw.io; signatures verified against compiled Java')
