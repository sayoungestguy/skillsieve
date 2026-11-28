/// <reference lib="dom" />
// page.evaluate callbacks run in the browser, so they use DOM types.
import type { Page } from '@playwright/test';

/**
 * Demo-only overlays: Playwright videos have no mouse pointer and no narration, so draw a pointer
 * dot and a caption bar into the page. Added on every load; never shipped with the app.
 */
const SCRIPT = `
(() => {
  if (window.__demoOverlay) return;
  window.__demoOverlay = true;
  const add = () => {
    const dot = document.createElement('div');
    dot.id = '__demo_dot';
    dot.style.cssText = 'position:fixed;z-index:2147483647;width:18px;height:18px;margin:-9px 0 0 -9px;border-radius:50%;background:rgba(79,70,229,.35);border:2px solid #4F46E5;pointer-events:none;transition:transform .08s;left:-40px;top:-40px';
    const cap = document.createElement('div');
    cap.id = '__demo_caption';
    cap.style.cssText = 'position:fixed;z-index:2147483646;left:50%;bottom:104px;transform:translateX(-50%);max-width:1100px;padding:14px 22px;border-radius:12px;background:rgba(15,23,42,.88);color:#fff;font:500 20px/1.4 Inter,system-ui,sans-serif;text-align:center;box-shadow:0 8px 24px rgba(0,0,0,.25);pointer-events:none;opacity:0;transition:opacity .25s';
    document.body.append(dot, cap);
    const saved = sessionStorage.getItem('__demo_caption');
    if (saved) { cap.textContent = saved; cap.style.opacity = '1'; }
    addEventListener('mousemove', e => { dot.style.left = e.clientX + 'px'; dot.style.top = e.clientY + 'px'; }, true);
    addEventListener('mousedown', () => { dot.style.transform = 'scale(.7)'; }, true);
    addEventListener('mouseup', () => { dot.style.transform = ''; }, true);
  };
  if (document.body) add(); else addEventListener('DOMContentLoaded', add);
})();`;

export async function installOverlay(page: Page): Promise<void> {
  await page.addInitScript(SCRIPT);
}

/** Shows a caption (kept across page loads) and leaves time to read it. */
export async function caption(page: Page, text: string, readMs = 2500): Promise<void> {
  await page.evaluate((t) => {
    sessionStorage.setItem('__demo_caption', t);
    const el = document.getElementById('__demo_caption');
    if (el) {
      el.textContent = t;
      el.style.opacity = t ? '1' : '0';
    }
  }, text);
  await page.waitForTimeout(readMs);
}

/** Moves the visible pointer to an element before clicking it, so viewers can follow. */
export async function point(
  page: Page,
  locator: import('@playwright/test').Locator,
): Promise<void> {
  const box = await locator.boundingBox();
  if (box) await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 20 });
}
