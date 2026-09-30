import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
const release=JSON.parse(readFileSync(new URL('../src/release.json',import.meta.url)));
for(const width of [320,390,768,834,1024,1440]){
 test(`layout and accessibility at ${width}px`,async({page})=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.setViewportSize({width,height:900});await page.goto('/');await page.evaluate(()=>document.fonts.ready);
  await expect(page.getByRole('heading',{level:1})).toBeVisible();await expect(page.locator('.phone')).toHaveCount(2);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.locator('.saved-showcase').scrollIntoViewIfNeeded();
  await expect.poll(()=>page.locator('img').evaluateAll(imgs=>imgs.every(i=>i.complete&&i.naturalWidth>0))).toBe(true);
  await page.evaluate(()=>scrollTo(0,0));
  const overlaps=await page.locator('h1,h2,h3').evaluateAll(headings=>headings.flatMap(h=>{const p=h.nextElementSibling;if(!p||p.tagName!=='P')return [];const a=h.getBoundingClientRect(),b=p.getBoundingClientRect();return a.bottom>b.top+1&&a.top<b.bottom-1&&a.right>b.left+1&&a.left<b.right-1?[h.textContent]:[];}));expect(overlaps).toEqual([]);
  const result=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();expect(result.violations).toEqual([]);
  await page.screenshot({path:`test-results/site-${width}.png`,fullPage:true});expect(errors).toEqual([]);
 });
}
test('mobile navigation, information dialog, and keyboard dismissal',async({page})=>{
 await page.setViewportSize({width:390,height:844});await page.goto('/');
 const menu=page.locator('.menu-toggle');await menu.click();await expect(menu).toHaveAttribute('aria-expanded','true');
 await page.getByRole('navigation',{name:'Main navigation'}).getByRole('link',{name:'Get the app'}).click();await expect(page).toHaveURL(/#download$/);await expect(menu).toHaveAttribute('aria-expanded','false');
 await page.getByRole('button',{name:'Support',exact:true}).click();await expect(page.getByRole('dialog')).toBeVisible();await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).not.toBeVisible();await expect(page.getByRole('button',{name:'Support',exact:true})).toBeFocused();
});
// Privacy and Terms are their own pages now, not dialogs.
for(const [name,heading] of [['Privacy','Privacy policy'],['Terms','Terms of use']]){
 test(`the ${name} page opens, reads back to the site, and has no violations`,async({page})=>{
  await page.setViewportSize({width:390,height:844});await page.goto('/');
  await page.getByRole('button',{name,exact:true}).click();
  await expect(page.getByRole('heading',{level:1,name:heading})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  const result=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();expect(result.violations).toEqual([]);
  await page.getByRole('link',{name:'Back to site'}).click();
  await expect(page.getByRole('heading',{level:1,name:/A place for everything/})).toBeVisible();
 });
}
test('APK button serves the actual build with a matching checksum',async({page,request},testInfo)=>{
 const head=await request.head(release.url);expect(head.status()).toBe(200);expect(Number(head.headers()['content-length'])).toBe(release.bytes);
 await page.goto('/#download');const event=page.waitForEvent('download');await page.getByRole('link',{name:'Download Android APK'}).click();const download=await event;
 expect(download.suggestedFilename()).toBe('pack-a-bunch-preview.apk');const file=testInfo.outputPath('download.apk');await download.saveAs(file);
 expect(createHash('sha256').update(readFileSync(file)).digest('hex')).toBe(release.sha256);
});
test('scroll reveal completes and reduced motion makes content immediately visible',async({page})=>{
 await page.emulateMedia({reducedMotion:'no-preference'});await page.goto('/');await expect(page.locator('.reveal-pending').first()).toBeAttached();
 const card=page.locator('.step-card').first();await card.scrollIntoViewIfNeeded();await expect(card).not.toHaveClass(/reveal-pending/);await expect(card).toHaveCSS('opacity','1');
 await page.emulateMedia({reducedMotion:'reduce'});await expect(page.locator('.reveal-pending')).toHaveCount(0);await expect(page.locator('html')).toHaveCSS('scroll-behavior','auto');
});
