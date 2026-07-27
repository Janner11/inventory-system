// FRONT-008: audita accesibilidad con Lighthouse real contra las paginas principales
// de la app, incluyendo las que requieren sesion autenticada. Lighthouse siempre navega
// la pagina el mismo (necesita controlar el ciclo de vida completo de la carga), por eso
// se usa launchPersistentContext (perfil real de Chrome, no un "browser context" aislado
// de Playwright) + --remote-debugging-port: Lighthouse se conecta a ese mismo puerto y
// hereda las cookies/sesion de Keycloak ya autenticadas por Playwright.
//
// Uso: node scripts/lighthouse-audit.mjs [--base-url=http://localhost:5173]
import { chromium } from '@playwright/test';
import lighthouse from 'lighthouse';
import { mkdirSync, writeFileSync, rmSync } from 'node:fs';
import path from 'node:path';

const args = Object.fromEntries(
  process.argv.slice(2).map((arg) => {
    const [key, value] = arg.replace(/^--/, '').split('=');
    return [key, value ?? true];
  }),
);

const BASE_URL = args['base-url'] ?? process.env.BASE_URL ?? 'http://localhost:5173';
const DEBUG_PORT = 9222;
const REPORT_DIR = path.join(process.cwd(), 'lighthouse-reports');
const PROFILE_DIR = path.join(process.cwd(), '.lighthouse-chrome-profile');

const PAGES = [
  { name: 'login', route: '/' },
  { name: 'dashboard', route: '/dashboard' },
  { name: 'products', route: '/products' },
  { name: 'product-form', route: '/products/new' },
  { name: 'stock', route: '/stock' },
  { name: 'audit', route: '/audit' },
  { name: 'reports', route: '/reports' },
];

async function login(page) {
  await page.goto(`${BASE_URL}/`);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.waitForURL(/realms\/inventario/);
  await page.fill('#username', 'admin@test.com');
  await page.fill('#password', 'admin123');
  await page.click('#kc-login');
  await page.waitForURL('**/dashboard');
}

async function main() {
  rmSync(REPORT_DIR, { recursive: true, force: true });
  mkdirSync(REPORT_DIR, { recursive: true });
  rmSync(PROFILE_DIR, { recursive: true, force: true });

  const context = await chromium.launchPersistentContext(PROFILE_DIR, {
    headless: true,
    args: [`--remote-debugging-port=${DEBUG_PORT}`],
  });
  const page = context.pages()[0] ?? (await context.newPage());

  console.log(`Autenticando contra ${BASE_URL}...`);
  await login(page);
  console.log('Autenticado. Iniciando auditoria de accesibilidad por pagina...\n');

  const results = [];

  for (const target of PAGES) {
    const url = `${BASE_URL}${target.route}`;
    // Navegacion previa con Playwright: fuerza a que la SPA (React Router) ya este
    // "tibia" y evita que la primera carga real (la que audita Lighthouse) sea la
    // primerísima carga en frio del bundle.
    await page.goto(url, { waitUntil: 'networkidle' });

    const runnerResult = await lighthouse(url, {
      port: DEBUG_PORT,
      output: ['html', 'json'],
      onlyCategories: ['accessibility'],
      logLevel: 'error',
    });

    const score = Math.round(runnerResult.lhr.categories.accessibility.score * 100);
    const failedAudits = Object.values(runnerResult.lhr.audits).filter(
      (audit) => audit.score !== null && audit.score < 1 && runnerResult.lhr.categories.accessibility.auditRefs.some((ref) => ref.id === audit.id),
    );

    results.push({ page: target.name, route: target.route, score, failedAudits: failedAudits.length });

    writeFileSync(path.join(REPORT_DIR, `${target.name}.html`), runnerResult.report[0]);
    writeFileSync(path.join(REPORT_DIR, `${target.name}.json`), runnerResult.report[1]);

    console.log(`${target.name.padEnd(14)} ${target.route.padEnd(16)} score=${score}  hallazgos=${failedAudits.length}`);
    if (failedAudits.length > 0) {
      failedAudits.forEach((audit) => {
        console.log(`   - [${audit.id}] ${audit.title}`);
      });
    }
  }

  console.log('\nResumen:');
  console.table(results);

  await context.close();

  const belowThreshold = results.filter((r) => r.score < 90);
  if (belowThreshold.length > 0) {
    console.log(`\n${belowThreshold.length} pagina(s) por debajo de 90: ${belowThreshold.map((r) => r.page).join(', ')}`);
    process.exitCode = 1;
  } else {
    console.log('\nTodas las paginas alcanzan >= 90 de accesibilidad.');
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
