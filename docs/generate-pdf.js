import fs from 'fs';
import path from 'path';
import { marked } from 'marked';
import puppeteer from 'puppeteer';

const DOCS_DIR = decodeURIComponent(path.dirname(new URL(import.meta.url).pathname)).replace(/^\/([A-Z]:)/, '$1');

const docFiles = [
  '01_Project_Synopsis.md',
  '02_Software_Requirements_Specification.md',
  '03_System_Design_HLD_LLD.md',
  '04_API_Documentation.md',
  '05_Deployment_Guide.md',
  '06_User_Manual.md',
  '07_Testing_Documentation.md',
  '08_Azure_Deployment_Guide.md',
];

// Read and combine all markdown files
let combinedMd = '';
for (let i = 0; i < docFiles.length; i++) {
  const filePath = path.join(DOCS_DIR, docFiles[i]);
  const content = fs.readFileSync(filePath, 'utf-8');
  if (i > 0) {
    combinedMd += '\n\n<div class="page-break"></div>\n\n';
  }
  combinedMd += content;
}

// Custom renderer to handle mermaid code blocks
const renderer = new marked.Renderer();
renderer.code = function({ text, lang }) {
  if (lang === 'mermaid') {
    return `<div class="mermaid-wrapper"><div class="mermaid">${text}</div></div>`;
  }
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
  return `<pre><code class="language-${lang || 'text'}">${escaped}</code></pre>`;
};

marked.use({ renderer });
const htmlContent = marked.parse(combinedMd);

const fullHtml = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>WealthWise — Project Documentation</title>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.3/dist/mermaid.min.js"><\/script>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap');

  * { margin: 0; padding: 0; box-sizing: border-box; }

  body {
    font-family: 'Inter', -apple-system, sans-serif;
    font-size: 11.5pt;
    line-height: 1.75;
    color: #1a1a1a;
    background: white;
    width: 210mm;
    min-width: 210mm;
  }

  /* ── Cover Page ───────────────────────────────────────── */
  .cover-page {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    min-height: 297mm;
    text-align: center;
    padding: 40mm 30mm;
    background: linear-gradient(135deg, #0f0c29 0%, #302b63 50%, #24243e 100%);
    color: white;
    page-break-after: always;
  }
  .cover-logo {
    font-size: 52pt;
    font-weight: 800;
    letter-spacing: -2px;
    background: linear-gradient(90deg, #00d09c, #4fc3f7);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    margin-bottom: 12px;
  }
  .cover-subtitle {
    font-size: 14pt;
    font-weight: 300;
    color: #b0b0d0;
    margin-bottom: 40px;
    max-width: 420px;
    line-height: 1.5;
  }
  .cover-divider {
    width: 100px;
    height: 3px;
    background: linear-gradient(90deg, #00d09c, #4fc3f7);
    margin: 28px auto;
    border-radius: 2px;
  }
  .cover-doc-title {
    font-size: 20pt;
    font-weight: 600;
    color: #e0e0f0;
    margin-top: 12px;
  }
  .cover-meta {
    font-size: 10.5pt;
    color: #8888aa;
    margin-top: 20px;
    line-height: 2.2;
  }

  /* ── TOC ──────────────────────────────────────────────── */
  .toc-page {
    page-break-after: always;
    padding: 20mm 18mm;
  }
  .toc-page h2 {
    font-size: 22pt;
    color: #1a1a2e;
    margin-bottom: 20px;
    padding-bottom: 10px;
    border-bottom: 3px solid #0066cc;
  }
  .toc-list { list-style: none; counter-reset: toc; }
  .toc-list li {
    counter-increment: toc;
    padding: 14px 0;
    border-bottom: 1px solid #f0f0f0;
    font-size: 13pt;
    font-weight: 500;
  }
  .toc-list li::before {
    content: counter(toc) ".";
    color: #0066cc;
    font-weight: 700;
    margin-right: 12px;
    font-size: 13pt;
  }
  .toc-desc {
    display: block;
    font-size: 9.5pt;
    font-weight: 400;
    color: #666;
    margin-top: 3px;
    padding-left: 30px;
  }

  /* ── Page Break ───────────────────────────────────────── */
  .page-break { page-break-before: always; }

  /* ── Headings ─────────────────────────────────────────── */
  h1 {
    font-size: 22pt;
    font-weight: 800;
    color: #1a1a2e;
    margin: 32px 0 14px;
    padding-bottom: 10px;
    border-bottom: 3px solid #0066cc;
    letter-spacing: -0.5px;
    page-break-after: avoid;
  }
  h2 {
    font-size: 15pt;
    font-weight: 700;
    color: #1a1a2e;
    margin: 28px 0 10px;
    padding-bottom: 6px;
    border-bottom: 1.5px solid #e0e0e0;
    page-break-after: avoid;
  }
  h3 {
    font-size: 12.5pt;
    font-weight: 600;
    color: #333;
    margin: 22px 0 8px;
    page-break-after: avoid;
  }
  h4 {
    font-size: 11pt;
    font-weight: 600;
    color: #444;
    margin: 16px 0 6px;
    page-break-after: avoid;
  }

  p { margin: 7px 0; }
  a { color: #0066cc; text-decoration: none; }
  ul, ol { margin: 8px 0 8px 24px; }
  li { margin: 4px 0; }
  strong { font-weight: 600; color: #111; }
  hr { border: none; height: 1px; background: #e0e0e0; margin: 20px 0; }

  /* ── Tables ───────────────────────────────────────────── */
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 14px 0;
    font-size: 9.5pt;
    page-break-inside: auto;
  }
  thead { background: #f0f4f8; }
  th {
    padding: 9px 11px;
    text-align: left;
    font-weight: 600;
    color: #1a1a2e;
    border: 1px solid #ddd;
    font-size: 9pt;
    text-transform: uppercase;
    letter-spacing: 0.3px;
  }
  td {
    padding: 7px 11px;
    border: 1px solid #ddd;
    vertical-align: top;
  }
  tr:nth-child(even) { background: #fafbfc; }
  tr { page-break-inside: avoid; }

  /* ── Code ─────────────────────────────────────────────── */
  code {
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 9pt;
    background: #f5f5f5;
    padding: 2px 5px;
    border-radius: 3px;
    border: 1px solid #e8e8e8;
    color: #c7254e;
  }
  pre {
    background: #1e1e2e;
    color: #cdd6f4;
    padding: 14px 18px;
    border-radius: 6px;
    margin: 14px 0;
    font-size: 8.5pt;
    line-height: 1.6;
    page-break-inside: avoid;
    white-space: pre-wrap;
    word-wrap: break-word;
  }
  pre code {
    background: none;
    border: none;
    padding: 0;
    color: #cdd6f4;
    font-size: 8.5pt;
  }

  /* ── Blockquotes ──────────────────────────────────────── */
  blockquote {
    border-left: 4px solid #0066cc;
    background: #e8f0fe;
    padding: 10px 18px;
    margin: 14px 0;
    border-radius: 0 6px 6px 0;
    font-style: italic;
    color: #444;
    page-break-inside: avoid;
  }

  /* ── Mermaid Diagrams — KEY FIX ───────────────────────── */
  .mermaid-wrapper {
    width: 100%;
    margin: 20px 0;
    page-break-inside: avoid;
    display: block;
  }

  .mermaid {
    display: block;
    width: 100%;
    min-height: 80px;
    text-align: center;
    padding: 16px 8px;
    background: #f8faff;
    border: 1px solid #d8e3f0;
    border-radius: 8px;
    overflow: visible;
  }

  /* Make all mermaid SVGs full width */
  .mermaid svg {
    width: 100% !important;
    max-width: 100% !important;
    height: auto !important;
    display: block;
    margin: 0 auto;
  }

  /* Sequence diagrams need more space */
  .mermaid svg[id^="sequence"],
  .mermaid svg[aria-roledescription="sequence"],
  .mermaid svg[aria-roledescription="er"] {
    min-height: 300px;
  }

  /* ── Print / Page ─────────────────────────────────────── */
  @page {
    size: A4;
    margin: 22mm 20mm 25mm 20mm;
  }
  @page :first { margin: 0; }

  @media print {
    body { font-size: 10.5pt; }
    .page-break { page-break-before: always; }
    h1 { font-size: 19pt; }
    h2 { font-size: 14pt; }
    h3 { font-size: 12pt; }
  }
</style>
</head>
<body>

<!-- Cover Page -->
<div class="cover-page">
  <div class="cover-logo">WealthWise</div>
  <div class="cover-subtitle">
    An Intelligent Portfolio Analytics and Goal-Planning Platform<br>for Indian Mutual Fund Investors
  </div>
  <div class="cover-divider"></div>
  <div class="cover-doc-title">Comprehensive Project Documentation</div>
  <div class="cover-meta">
    Project Synopsis &bull; SRS &bull; System Design &bull; API Docs &bull; Deployment Guide &bull; User Manual &bull; Testing &bull; Azure Deployment<br>
    Spring Boot 3.2 &bull; React 19 &bull; PostgreSQL 15 &bull; Docker &bull; Microsoft Azure<br>
    <br>April 2026
  </div>
</div>

<!-- Table of Contents -->
<div class="toc-page">
  <h2>Table of Contents</h2>
  <ol class="toc-list">
    <li>Project Synopsis
      <span class="toc-desc">Abstract, Problem Statement, Objectives, Scope, Technology Stack</span>
    </li>
    <li>Software Requirements Specification (SRS)
      <span class="toc-desc">30 Functional Requirements, 30 Non-Functional Requirements, Feature Matrix, 4 Use Cases</span>
    </li>
    <li>System Design — High-Level Design (HLD)
      <span class="toc-desc">Architecture Overview, Technology Justification, Caching, Security Architecture</span>
    </li>
    <li>System Design — Low-Level Design (LLD)
      <span class="toc-desc">Class Diagram, 3 Sequence Diagrams, DFD Level 0/1, ER Diagram, Algorithm Pseudocode</span>
    </li>
    <li>API Documentation
      <span class="toc-desc">37 Endpoints across 9 API groups, JWT authentication, Request/Response samples</span>
    </li>
    <li>Deployment Guide
      <span class="toc-desc">Prerequisites, Environment Variables, Local Setup, Docker, Render Deployment, Troubleshooting</span>
    </li>
    <li>User Manual
      <span class="toc-desc">Feature Guide, Navigation, Screen Descriptions, 15 FAQs</span>
    </li>
    <li>Testing Documentation
      <span class="toc-desc">280 Automated Tests, 15 Test Suites, Testcontainers, MSW, Playwright E2E, k6 Load Testing, OWASP, Storybook</span>
    </li>
    <li>Azure Deployment Guide
      <span class="toc-desc">Azure App Service, Static Web Apps, GitHub Actions CI/CD, Service Principal, Supabase Pooler, Monitoring &amp; Troubleshooting</span>
    </li>
  </ol>
</div>

<!-- Document Content -->
${htmlContent}

<script>
  window.mermaidReady = false;

  mermaid.initialize({
    startOnLoad: false,
    theme: 'base',
    themeVariables: {
      fontSize: '13px',
      fontFamily: 'Inter, sans-serif',
      primaryColor: '#e8f0fe',
      primaryBorderColor: '#0066cc',
      primaryTextColor: '#1a1a2e',
      lineColor: '#0066cc',
      secondaryColor: '#f0f4f8',
      tertiaryColor: '#fff',
      noteBkgColor: '#fffbea',
      noteTextColor: '#333',
      activationBkgColor: '#e8f0fe',
    },
    flowchart: {
      useMaxWidth: false,
      htmlLabels: true,
      curve: 'basis',
      padding: 20,
      nodeSpacing: 60,
      rankSpacing: 60,
      diagramPadding: 20,
    },
    sequence: {
      useMaxWidth: false,
      actorFontSize: 13,
      messageFontSize: 12,
      noteFontSize: 11,
      wrap: true,
      width: 200,
      height: 40,
      diagramMarginX: 20,
      diagramMarginY: 20,
    },
    er: {
      useMaxWidth: false,
      fontSize: 12,
      diagramPadding: 20,
    },
    classDiagram: {
      useMaxWidth: false,
      diagramPadding: 20,
    },
  });

  async function renderAll() {
    const elements = document.querySelectorAll('.mermaid');
    console.log('Rendering', elements.length, 'Mermaid diagrams...');

    for (let i = 0; i < elements.length; i++) {
      const el = elements[i];
      const src = el.textContent.trim();
      if (!src) continue;

      try {
        const id = 'mermaid-' + i;
        const { svg } = await mermaid.render(id, src);
        el.innerHTML = svg;

        // Ensure SVG stretches properly
        const svgEl = el.querySelector('svg');
        if (svgEl) {
          svgEl.removeAttribute('width');
          svgEl.removeAttribute('height');
          svgEl.style.width = '100%';
          svgEl.style.maxWidth = '100%';
          svgEl.style.height = 'auto';
          // If viewBox not set, set a default
          if (!svgEl.getAttribute('viewBox')) {
            const w = svgEl.scrollWidth || 800;
            const h = svgEl.scrollHeight || 400;
            svgEl.setAttribute('viewBox', \`0 0 \${w} \${h}\`);
          }
        }
        console.log('Rendered diagram', i + 1, '/', elements.length);
      } catch (err) {
        console.error('Failed to render diagram', i, ':', err.message);
        el.innerHTML = '<p style="color:#c00;padding:10px;font-size:10pt;">⚠️ Diagram rendering failed: ' + err.message + '</p>';
      }
    }

    window.mermaidReady = true;
    console.log('All diagrams rendered.');
  }

  renderAll();
<\/script>
</body>
</html>`;

// Write HTML
const htmlPath = path.join(DOCS_DIR, 'WealthWise_Documentation.html');
fs.writeFileSync(htmlPath, fullHtml, 'utf-8');
console.log(`HTML written to: ${htmlPath}`);

// Generate PDF with Puppeteer
console.log('Launching Puppeteer...');
const browser = await puppeteer.launch({
  headless: true,
  args: [
    '--no-sandbox',
    '--disable-setuid-sandbox',
    '--disable-web-security',
    '--allow-file-access-from-files',
    '--enable-local-file-accesses',
  ],
});

const page = await browser.newPage();

// A4 width at 96 DPI = 794px, at 120 DPI = 992px
// Use wider viewport so diagrams render large then scale down
await page.setViewport({ width: 1400, height: 1080, deviceScaleFactor: 1 });

const fileUrl = `file:///${htmlPath.replace(/\\/g, '/')}`;
console.log(`Loading: ${fileUrl}`);

await page.goto(fileUrl, {
  waitUntil: 'networkidle2',
  timeout: 120000,
});

// Wait for mermaid rendering to complete
console.log('Waiting for Mermaid diagrams...');
await page.waitForFunction(() => window.mermaidReady === true, { timeout: 90000, polling: 500 });

// Extra settle time for SVG layout
console.log('Extra settle time...');
await new Promise(r => setTimeout(r, 4000));

// Check all diagrams rendered
const diagStats = await page.evaluate(() => {
  const all = document.querySelectorAll('.mermaid');
  const withSvg = document.querySelectorAll('.mermaid svg');
  const errored = document.querySelectorAll('.mermaid p');
  return {
    total: all.length,
    rendered: withSvg.length,
    errored: errored.length,
  };
});
console.log(`Diagram stats: ${diagStats.rendered}/${diagStats.total} rendered, ${diagStats.errored} errored`);

// Generate PDF
const pdfPath = path.join(DOCS_DIR, 'WealthWise_Complete_Documentation.pdf');
console.log('Generating PDF...');
await page.pdf({
  path: pdfPath,
  format: 'A4',
  printBackground: true,
  margin: {
    top: '22mm',
    right: '20mm',
    bottom: '25mm',
    left: '20mm',
  },
  displayHeaderFooter: true,
  headerTemplate: '<div></div>',
  footerTemplate: `
    <div style="width:100%;font-size:8pt;color:#999;padding:0 20mm;display:flex;justify-content:space-between;font-family:Inter,sans-serif;">
      <span>WealthWise — Project Documentation</span>
      <span>Page <span class="pageNumber"></span> of <span class="totalPages"></span></span>
    </div>
  `,
  preferCSSPageSize: false,
  tagged: true,
});

await browser.close();
console.log(`PDF generated at: ${pdfPath}`);

const stat = fs.statSync(pdfPath);
console.log(`PDF size: ${(stat.size / 1024 / 1024).toFixed(2)} MB`);
console.log('Done!');
