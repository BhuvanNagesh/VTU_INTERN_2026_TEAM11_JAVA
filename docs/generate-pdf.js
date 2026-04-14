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
];

const docTitles = [
  'Project Synopsis',
  'Software Requirements Specification',
  'System Design (HLD + LLD)',
  'API Documentation',
  'Deployment Guide',
  'User Manual',
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

// Configure marked
marked.setOptions({
  gfm: true,
  breaks: false,
});

// Custom renderer to handle mermaid code blocks
const renderer = new marked.Renderer();
const originalCodeRenderer = renderer.code;

renderer.code = function({ text, lang }) {
  if (lang === 'mermaid') {
    return `<div class="mermaid">${text}</div>`;
  }
  // For regular code blocks
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
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>WealthWise — Project Documentation</title>
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap');

  :root {
    --primary: #0066cc;
    --primary-light: #e8f0fe;
    --heading-color: #1a1a2e;
    --text-color: #2d2d2d;
    --text-muted: #666;
    --border-color: #e0e0e0;
    --bg-code: #f6f8fa;
    --bg-table-header: #f0f4f8;
    --bg-blockquote: #fef9e7;
    --accent-green: #00a67d;
    --accent-blue: #2563eb;
  }

  * { margin: 0; padding: 0; box-sizing: border-box; }

  body {
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    font-size: 11pt;
    line-height: 1.7;
    color: var(--text-color);
    background: white;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  /* Cover Page */
  .cover-page {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    text-align: center;
    padding: 3cm;
    background: linear-gradient(135deg, #0f0c29 0%, #302b63 50%, #24243e 100%);
    color: white;
    page-break-after: always;
  }

  .cover-page .logo {
    font-size: 56pt;
    font-weight: 800;
    letter-spacing: -2px;
    margin-bottom: 8px;
    background: linear-gradient(90deg, #00d09c, #4fc3f7);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  .cover-page .subtitle {
    font-size: 16pt;
    font-weight: 300;
    color: #b0b0d0;
    margin-bottom: 48px;
    max-width: 500px;
  }

  .cover-page .divider {
    width: 120px;
    height: 3px;
    background: linear-gradient(90deg, #00d09c, #4fc3f7);
    margin: 32px auto;
    border-radius: 2px;
  }

  .cover-page .doc-title {
    font-size: 22pt;
    font-weight: 600;
    color: #e0e0f0;
    margin-top: 16px;
  }

  .cover-page .doc-meta {
    font-size: 11pt;
    color: #8888aa;
    margin-top: 24px;
    line-height: 2;
  }

  /* TOC */
  .toc-page {
    page-break-after: always;
    padding: 1cm 0;
  }

  .toc-page h2 {
    font-size: 20pt;
    color: var(--heading-color);
    margin-bottom: 24px;
    padding-bottom: 12px;
    border-bottom: 3px solid var(--primary);
  }

  .toc-page ol {
    list-style: none;
    counter-reset: toc;
    padding: 0;
  }

  .toc-page ol li {
    counter-increment: toc;
    padding: 12px 0;
    border-bottom: 1px solid #f0f0f0;
    font-size: 13pt;
    font-weight: 500;
  }

  .toc-page ol li::before {
    content: counter(toc) ".";
    color: var(--primary);
    font-weight: 700;
    margin-right: 12px;
    font-size: 14pt;
  }

  .toc-page ol li .toc-desc {
    display: block;
    font-size: 10pt;
    font-weight: 400;
    color: var(--text-muted);
    margin-top: 4px;
    padding-left: 28px;
  }

  /* Page breaks */
  .page-break { page-break-before: always; }

  /* Headings */
  h1 {
    font-size: 24pt;
    font-weight: 800;
    color: var(--heading-color);
    margin: 36px 0 16px;
    padding-bottom: 12px;
    border-bottom: 3px solid var(--primary);
    letter-spacing: -0.5px;
    page-break-after: avoid;
  }

  h2 {
    font-size: 16pt;
    font-weight: 700;
    color: var(--heading-color);
    margin: 32px 0 12px;
    padding-bottom: 8px;
    border-bottom: 2px solid var(--border-color);
    page-break-after: avoid;
  }

  h3 {
    font-size: 13pt;
    font-weight: 600;
    color: #333;
    margin: 24px 0 8px;
    page-break-after: avoid;
  }

  h4 {
    font-size: 11.5pt;
    font-weight: 600;
    color: #444;
    margin: 18px 0 6px;
    page-break-after: avoid;
  }

  /* Paragraphs */
  p { margin: 8px 0; }

  /* Links */
  a { color: var(--primary); text-decoration: none; }

  /* Lists */
  ul, ol { margin: 8px 0 8px 24px; }
  li { margin: 4px 0; }

  /* Tables */
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 16px 0;
    font-size: 10pt;
    page-break-inside: auto;
  }

  thead {
    background: var(--bg-table-header);
  }

  th {
    padding: 10px 12px;
    text-align: left;
    font-weight: 600;
    color: var(--heading-color);
    border: 1px solid var(--border-color);
    font-size: 9.5pt;
    text-transform: uppercase;
    letter-spacing: 0.3px;
  }

  td {
    padding: 8px 12px;
    border: 1px solid var(--border-color);
    vertical-align: top;
  }

  tr:nth-child(even) { background: #fafbfc; }
  tr { page-break-inside: avoid; }

  /* Code */
  code {
    font-family: 'JetBrains Mono', 'Cascadia Code', 'Fira Code', monospace;
    font-size: 9.5pt;
    background: var(--bg-code);
    padding: 2px 6px;
    border-radius: 4px;
    border: 1px solid #e8e8e8;
    color: #d63384;
  }

  pre {
    background: #1e1e2e;
    color: #cdd6f4;
    padding: 16px 20px;
    border-radius: 8px;
    overflow-x: auto;
    margin: 16px 0;
    font-size: 9pt;
    line-height: 1.6;
    page-break-inside: avoid;
  }

  pre code {
    background: none;
    border: none;
    padding: 0;
    color: #cdd6f4;
    font-size: 9pt;
  }

  /* Blockquotes */
  blockquote {
    border-left: 4px solid var(--primary);
    background: var(--primary-light);
    padding: 12px 20px;
    margin: 16px 0;
    border-radius: 0 8px 8px 0;
    font-style: italic;
    color: #444;
    page-break-inside: avoid;
  }

  blockquote em { font-style: italic; }

  /* Horizontal rules */
  hr {
    border: none;
    height: 1px;
    background: var(--border-color);
    margin: 24px 0;
  }

  /* Strong */
  strong { font-weight: 600; color: #1a1a1a; }

  /* Mermaid diagrams */
  .mermaid {
    display: flex;
    justify-content: center;
    margin: 20px 0;
    padding: 16px;
    background: #fafbfc;
    border: 1px solid var(--border-color);
    border-radius: 8px;
    page-break-inside: avoid;
    overflow: visible;
  }

  .mermaid svg {
    max-width: 100% !important;
    height: auto !important;
  }

  /* Section markers */
  .section-number {
    color: var(--primary);
    font-weight: 700;
  }

  /* Page header/footer for print */
  @page {
    size: A4;
    margin: 2cm 2.2cm 2.5cm 2.2cm;

    @bottom-center {
      content: "WealthWise Documentation";
      font-size: 8pt;
      color: #999;
    }

    @bottom-right {
      content: counter(page);
      font-size: 8pt;
      color: #999;
    }
  }

  @page :first {
    margin: 0;
    @bottom-center { content: none; }
    @bottom-right { content: none; }
  }

  /* Print specific */
  @media print {
    body { font-size: 10.5pt; }
    .cover-page { min-height: 100vh; }
    .page-break { page-break-before: always; }
    h1 { font-size: 20pt; }
    h2 { font-size: 14pt; }
    h3 { font-size: 12pt; }
    pre { font-size: 8.5pt; }
    table { font-size: 9pt; }
  }
</style>
</head>
<body>

<!-- Cover Page -->
<div class="cover-page">
  <div class="logo">WealthWise</div>
  <div class="subtitle">An Intelligent Portfolio Analytics and Goal-Planning Platform for Indian Mutual Fund Investors</div>
  <div class="divider"></div>
  <div class="doc-title">Comprehensive Project Documentation</div>
  <div class="doc-meta">
    Project Synopsis &bull; SRS &bull; System Design &bull; API Documentation &bull; Deployment Guide &bull; User Manual<br>
    Spring Boot 3.2 &bull; React 19 &bull; PostgreSQL &bull; Docker<br>
    <br>
    April 2026
  </div>
</div>

<!-- Table of Contents -->
<div class="toc-page">
  <h2>Table of Contents</h2>
  <ol>
    <li>
      Project Synopsis
      <span class="toc-desc">Abstract, Problem Statement, Objectives, Scope, Technology Stack</span>
    </li>
    <li>
      Software Requirements Specification (SRS)
      <span class="toc-desc">Functional Requirements (30 FRs), Non-Functional Requirements (30 NFRs), System Features Matrix, Use Cases</span>
    </li>
    <li>
      System Design — High-Level Design (HLD)
      <span class="toc-desc">Architecture Overview, Technology Justification, Caching Architecture, Security Architecture</span>
    </li>
    <li>
      System Design — Low-Level Design (LLD)
      <span class="toc-desc">Module Breakdown, Class Diagram, Sequence Diagrams, Data Flow Diagrams, Database Schema, Pseudocode</span>
    </li>
    <li>
      API Documentation
      <span class="toc-desc">37 Endpoints across 9 API Groups, Authentication, Request/Response Samples</span>
    </li>
    <li>
      Deployment Guide
      <span class="toc-desc">Prerequisites, Environment Configuration, Local Setup, Docker Builds, Render Deployment, Troubleshooting</span>
    </li>
    <li>
      User Manual
      <span class="toc-desc">Feature Guide, Navigation, Screen Descriptions, FAQ</span>
    </li>
  </ol>
</div>

<!-- Document Content -->
${htmlContent}

<script>
  mermaid.initialize({
    startOnLoad: true,
    theme: 'default',
    securityLevel: 'loose',
    themeVariables: {
      fontSize: '14px',
      fontFamily: 'Inter, sans-serif',
    },
    flowchart: {
      useMaxWidth: true,
      htmlLabels: true,
      curve: 'basis',
      padding: 15,
      nodeSpacing: 50,
      rankSpacing: 50,
    },
    sequence: {
      useMaxWidth: true,
      actorFontSize: 13,
      messageFontSize: 12,
      noteFontSize: 11,
      wrap: true,
      width: 180,
    },
    er: {
      useMaxWidth: true,
      fontSize: 12,
    },
    classDiagram: {
      useMaxWidth: true,
    },
    mindmap: {
      useMaxWidth: true,
    },
  });
</script>
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
  args: ['--no-sandbox', '--disable-setuid-sandbox'],
});

const page = await browser.newPage();
await page.setViewport({ width: 1200, height: 800 });

// Load the HTML file
const fileUrl = `file:///${htmlPath.replace(/\\/g, '/')}`;
console.log(`Loading: ${fileUrl}`);
await page.goto(fileUrl, { waitUntil: 'networkidle0', timeout: 120000 });

// Wait for Mermaid to render
console.log('Waiting for Mermaid diagrams to render...');
await page.waitForFunction(() => {
  const mermaidDivs = document.querySelectorAll('.mermaid');
  if (mermaidDivs.length === 0) return true;
  return Array.from(mermaidDivs).every(div => div.querySelector('svg') !== null);
}, { timeout: 60000 });

// Extra wait for rendering to complete
await new Promise(r => setTimeout(r, 3000));

// Generate PDF
const pdfPath = path.join(DOCS_DIR, 'WealthWise_Complete_Documentation.pdf');
console.log('Generating PDF...');
await page.pdf({
  path: pdfPath,
  format: 'A4',
  printBackground: true,
  margin: {
    top: '2cm',
    right: '2.2cm',
    bottom: '2.5cm',
    left: '2.2cm',
  },
  displayHeaderFooter: true,
  headerTemplate: '<div></div>',
  footerTemplate: `
    <div style="width:100%; font-size:8pt; color:#999; padding:0 2.2cm; display:flex; justify-content:space-between;">
      <span>WealthWise — Project Documentation</span>
      <span>Page <span class="pageNumber"></span> of <span class="totalPages"></span></span>
    </div>
  `,
});

console.log(`PDF generated: ${pdfPath}`);
await browser.close();
console.log('Done!');
