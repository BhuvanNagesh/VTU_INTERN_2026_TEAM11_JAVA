import puppeteer from 'puppeteer';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const mindmapDef = `mindmap
  root((WealthWise))
    Authentication
      Sign Up
      Sign In
      Forgot Password
      OTP Reset
    Profile
      View Profile
      Update Details
      Change Password
      PAN Encryption
    Transactions
      Manual Entry
      Bulk SIP Generator
      CAS PDF Import
      Transaction Reversal
      Portfolio Summary
    Analytics
      Risk Profiling
      Volatility and Sharpe
      Fund Overlap Matrix
      Growth Timeline
    SIP Intelligence
      Active SIP Dashboard
      SIP vs Lumpsum
      Day Optimization
      Step-Up Projection
    Goal Planning
      Goal CRUD
      Monte Carlo Sim
      Deterministic Proj
      Required SIP Calc
      Goal-Fund Linking
    Scheme Data
      AMFI Seed 45K
      Scheme Search
      NAV Fetching
      NAV Caching`;

const html = `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.3/dist/mermaid.min.js"></script>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { background: white; padding: 30px; }
  #diagram { width: 100%; }
  #diagram svg { width: 100% !important; height: auto !important; }
</style>
</head>
<body>
<div id="diagram"></div>
<script>
  window.mindmapDone = false;
  mermaid.initialize({
    startOnLoad: false,
    theme: 'base',
    themeVariables: {
      fontSize: '14px',
      fontFamily: 'Inter, sans-serif',
      primaryColor: '#e8f0fe',
      primaryBorderColor: '#0066cc',
      primaryTextColor: '#1a1a2e',
      lineColor: '#0066cc',
      secondaryColor: '#f0f4f8',
    }
  });

  async function render() {
    try {
      const src = ${JSON.stringify(mindmapDef)};
      const { svg } = await mermaid.render('mindmap-main', src);
      document.getElementById('diagram').innerHTML = svg;
      const svgEl = document.querySelector('#diagram svg');
      if (svgEl) {
        svgEl.removeAttribute('width');
        svgEl.removeAttribute('height');
        svgEl.style.width = '100%';
        svgEl.style.height = 'auto';
      }
    } catch(e) {
      document.body.innerHTML = '<p style="color:red">Error: ' + e.message + '</p>';
    }
    window.mindmapDone = true;
  }
  render();
</script>
</body>
</html>`;

const htmlPath = path.join(__dirname, 'mindmap_temp.html');
fs.writeFileSync(htmlPath, html, 'utf-8');
console.log('HTML written');

const browser = await puppeteer.launch({
  headless: true,
  args: ['--no-sandbox', '--disable-setuid-sandbox']
});
const page = await browser.newPage();
await page.setViewport({ width: 1400, height: 800 });

const fileUrl = `file:///${htmlPath.replace(/\\/g, '/')}`;
await page.goto(fileUrl, { waitUntil: 'networkidle2', timeout: 60000 });
await page.waitForFunction(() => window.mindmapDone === true, { timeout: 40000, polling: 300 });
await new Promise(r => setTimeout(r, 3000));

const info = await page.evaluate(() => {
  const svg = document.querySelector('#diagram svg');
  if (!svg) return null;
  const bb = svg.getBoundingClientRect();
  return { w: Math.round(bb.width), h: Math.round(bb.height), hasContent: svg.innerHTML.length > 100 };
});
console.log('SVG info:', info);

const pngPath = path.join(__dirname, 'mindmap_prerender.png');
await page.screenshot({ path: pngPath, fullPage: true, omitBackground: false });
console.log('PNG saved:', pngPath);

const stat = fs.statSync(pngPath);
console.log('PNG size:', (stat.size / 1024).toFixed(1), 'KB');

fs.unlinkSync(htmlPath);
await browser.close();
console.log('Done');
