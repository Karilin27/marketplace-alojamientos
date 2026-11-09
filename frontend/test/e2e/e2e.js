/* Prueba de punta a punta del frontend contra la API simulada. Requiere playwright: node test/e2e/e2e.js */
const { chromium } = require('playwright');
const { iniciar, ALOJ } = require('./mock-api');
const assert = require('node:assert/strict');

(async () => {
  const puerto = 8099, base = 'http://127.0.0.1:' + puerto;
  const srv = await iniciar(puerto);
  const navegador = await chromium.launch();
  const ctx = await navegador.newContext();
  await ctx.addInitScript(() => { localStorage.setItem('token', 'jwt-de-prueba'); });
  await ctx.route('**/js/reservas/config.js', (r) => r.fulfill({ contentType: 'text/javascript', body: `window.RESERVAS_CONFIG={API_BASE:'${base}',TOKEN_KEY:'token',LOGIN_URL:'/login.html'};` }));
  const page = await ctx.newPage();
  const errores = []; page.on('pageerror', (e) => errores.push(e.message)); page.on('console', (m) => { if (m.type() === 'error') errores.push(m.text()); });
  const fecha = (dias) => { const d = new Date(); d.setDate(d.getDate() + dias); return d.toISOString().slice(0, 10); };
  const paso = (n) => console.log('OK  ' + n);

  // 1) validación en cliente
  await page.goto(`${base}/reservas/reservar.html?alojamiento=${ALOJ}`);
  await page.click('#btn-cotizar');
  assert.match(await page.textContent('#e-llegada'), /llegada/i); paso('validación: llegada obligatoria');
  await page.fill('#llegada', fecha(30)); await page.fill('#salida', fecha(30)); await page.click('#btn-cotizar');
  assert.match(await page.textContent('#e-salida'), /posterior/); paso('validación: salida posterior a llegada');
  await page.fill('#salida', fecha(33)); await page.fill('#huespedes', '5'); await page.click('#btn-cotizar');
  assert.match(await page.textContent('#e-huespedes'), /Máximo 4/); paso('validación: capacidad máxima');

  // 2) cotización con servicio adicional (CAL-02: 740.000)
  await page.fill('#huespedes', '2'); await page.check('#servicios input[type=checkbox]'); await page.click('#btn-cotizar');
  await page.waitForSelector('#cotizacion:not(.oculto)');
  const filas = await page.$$eval('#desglose tr', (t) => t.map((x) => x.innerText.replace(/\s+/g, ' ')));
  assert.ok(filas.some((f) => /Total.*740\.000/.test(f)), 'total 740.000: ' + JSON.stringify(filas)); paso('cotización CAL-02 = $740.000');
  assert.match(await page.textContent('#politica'), /MODERADA.*100% de devolución/); paso('la cotización muestra la política de cancelación');

  // 3) reservar -> pago con cuenta regresiva
  await page.click('#btn-reservar'); await page.waitForURL(/pago\.html\?reserva=/);
  await page.waitForSelector('#cuenta');
  const cuenta = await page.textContent('#cuenta'); assert.match(cuenta, /^1[45]:\d\d$/); paso('cuenta regresiva de 15 min: ' + cuenta);

  // 4) pago aprobado
  await page.click('#btn-pagar'); await page.waitForSelector('#mensaje .exito');
  assert.match(await page.textContent('#resumen'), /Confirmada/); paso('pago aprobado -> reserva Confirmada');

  // 5) mis reservas y cancelación con devolución estimada
  await page.goto(`${base}/reservas/mis-reservas.html`);
  await page.waitForSelector('#lista li strong');
  assert.match(await page.textContent('#lista'), /Confirmada/); paso('mis reservas lista la reserva');
  await page.click('text=Cancelar reserva');
  assert.match(await page.textContent('#dlg-texto'), /devolución estimada es \$740\.000/); paso('diálogo muestra devolución estimada $740.000');
  await page.click('#dlg-ok'); await page.waitForSelector('#mensaje .exito');
  await page.waitForFunction(() => document.querySelector('#lista').innerText.includes('Cancelada'));
  assert.match(await page.textContent('#lista'), /Reembolso/); paso('cancelación -> Cancelada con reembolso');

  // 6) notificaciones
  await page.goto(`${base}/reservas/notificaciones.html`);
  await page.waitForSelector('#lista li.no-leida');
  const antes = await page.$$eval('#lista li.no-leida', (l) => l.length);
  await page.click('#lista li.no-leida button'); await page.waitForTimeout(200);
  const despues = await page.$$eval('#lista li.no-leida', (l) => l.length);
  assert.equal(despues, antes - 1); paso('marcar notificación como leída');

  // 7) pago rechazado
  await page.goto(`${base}/reservas/reservar.html?alojamiento=${ALOJ}`);
  await page.fill('#llegada', fecha(60)); await page.fill('#salida', fecha(62)); await page.click('#btn-cotizar');
  await page.waitForSelector('#cotizacion:not(.oculto)'); await page.click('#btn-reservar'); await page.waitForURL(/pago\.html/);
  await page.selectOption('#metodo', 'pm_card_chargeDeclined'); await page.click('#btn-pagar');
  await page.waitForSelector('#mensaje .error'); assert.match(await page.textContent('#mensaje'), /rechazado/i); paso('pago rechazado muestra el error');

  // 8) disponibilidad del anfitrión
  await page.goto(`${base}/reservas/disponibilidad.html?alojamiento=${ALOJ}`);
  await page.click('#agregar'); await page.click('#guardar');
  assert.match(await page.textContent('#mensaje'), /faltan fechas/); paso('disponibilidad: valida periodos incompletos');
  const f = page.locator('#filas > div').first();
  await f.locator('input').nth(0).fill(fecha(70)); await f.locator('input').nth(1).fill(fecha(75)); await page.click('#guardar');
  await page.waitForSelector('#mensaje .exito'); paso('disponibilidad: guarda periodo');

  assert.deepEqual(errores.filter((e) => !/favicon|401|404|422|409/.test(e)), [], 'errores de consola');
  paso('sin errores de JavaScript en consola');
  await navegador.close(); srv.close();
  console.log('== E2E frontend: TODO OK');
})().catch((e) => { console.error('FALLO', e); process.exit(1); });
