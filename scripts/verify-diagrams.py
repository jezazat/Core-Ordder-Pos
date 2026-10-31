from pathlib import Path
import json, xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
diagrams = root / 'diagrams'
doc = ET.parse(diagrams / 'pos-class-diagram.drawio').getroot()
pages = doc.findall('diagram')
assert len(pages) == 2
classes = set(json.loads((diagrams / 'compiled-model.json').read_text(encoding='utf-8')))
assert classes == {'Member', 'Customer', 'Employee', 'Order', 'Sales', 'Product', 'Ingredient'}
counts = {}
for page in pages:
    cells = page.findall('./mxGraphModel/root/mxCell')
    by_id = {c.get('id'): c for c in cells}
    assert len(by_id) == len(cells), 'Duplicate cell IDs'
    assert sum(c.get('style') == 'group;' for c in cells) == 7, 'Exactly seven class cards are required'
    for name in classes:
        assert by_id[name].get('style') == 'group;'
        for suffix in ('-box', '-title', '-header-bg', '-contents'):
            assert by_id[name + suffix].get('parent') == name, 'Class content must move with its group'
    edges = [c for c in cells if c.get('edge') == '1']
    for edge in edges:
        assert edge.get('source') in classes and edge.get('target') in classes
    counts[page.get('id')] = dict(classes=len(classes), edges=len(edges))
    if page.get('id') == 'overview':
        comp = [c for c in edges if 'startArrow=diamond;startFill=1;' in c.get('style')]
        agg = [c for c in edges if 'startArrow=diamond;startFill=0;' in c.get('style')]
        inheritance = [c for c in edges if 'endArrow=block;endFill=0;' in c.get('style')]
        assert len(comp) == 1 and len(agg) == 2 and len(inheritance) == 1 and len(edges) == 6
        assert {(c.get('source'), c.get('target')) for c in comp} == {
            ('Order', 'Product')}
        assert {(c.get('source'), c.get('target')) for c in agg} == {('Sales', 'Order'), ('Product', 'Ingredient')}
        for edge in edges:
            if edge not in inheritance:
                assert by_id[edge.get('id')+'-source'].get('value')
                assert by_id[edge.get('id')+'-target'].get('value')
for result in json.loads((diagrams / 'layout-check.json').read_text()):
    assert result['cards'] == 7 and not result['overflow']
    png = diagrams / (result['page'] + '.png')
    import struct
    data = png.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II', data[16:24]) == (result['width'], result['height'])
print(json.dumps(counts))
print('PASS editable groups, references, ownership, multiplicities, SVG text bounds, PNG dimensions')
