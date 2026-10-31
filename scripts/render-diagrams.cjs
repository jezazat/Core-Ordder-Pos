const fs = require('fs');
const path = require('path');
const { chromium } = require('C:/Users/USER/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');

(async () => {
  const directory = path.resolve(__dirname, '../diagrams');
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const results = [];
  try {
    for (const name of ['overview', 'details']) {
      const page = await browser.newPage({ viewport: { width: 2400, height: 1600 }, deviceScaleFactor: 1 });
      const svgSource = fs.readFileSync(path.join(directory, name + '.svg'), 'utf8').replace(/<\?xml[^>]*\?>/, '');
      await page.setContent('<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;padding:0}svg{display:block}</style></head><body>' + svgSource + '</body></html>');
      await page.evaluate(() => document.fonts.ready);
      const measurements = await page.evaluate(() => {
        const svg = document.querySelector('svg');
        const width = Number(svg.getAttribute('width')), height = Number(svg.getAttribute('height'));
        const overflow = [];
        for (const text of document.querySelectorAll('text[data-owner]')) {
          const box = document.getElementById('card-' + text.dataset.owner).getBBox();
          const bounds = text.getBBox();
          if (bounds.x < box.x - 1 || bounds.x + bounds.width > box.x + box.width + 1 || bounds.y < box.y - 1 || bounds.y + bounds.height > box.y + box.height + 1)
            overflow.push({ owner: text.dataset.owner, text: text.textContent });
        }
        return { width, height, cards: document.querySelectorAll('rect[id^="card-"]').length, overflow };
      });
      await page.setViewportSize({ width: measurements.width, height: measurements.height });
      await page.screenshot({ path: path.join(directory, name + '.png'), fullPage: true });
      results.push({ page: name, ...measurements });
      await page.close();
    }
    fs.writeFileSync(path.join(directory, 'layout-check.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify(results));
    if (results.some(r => r.overflow.length || r.cards !== 7)) throw new Error('Each diagram must have exactly seven class cards without overflow');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
