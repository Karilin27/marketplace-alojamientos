/* Prueba de punta a punta de la pantalla de perfil (HU-004) contra la API simulada. Requiere playwright: node test/e2e/e2e-perfil.js */
const { chromium } = require('playwright');
const { iniciar, confirmarReservaDePrueba, vaciarReservas } = require('./mock-api');
const assert = require('node:assert/strict');

(async () => {
  const puerto = 8097, base = 'http://127.0.0.1:' + puerto;
  const srv = await iniciar(puerto);
  const navegador = await chromium.launch();
  const ctx = await navegador.newContext();
  await ctx.addInitScript(() => {
    if (!localStorage.getItem('token') && !sessionStorage.getItem('cerrada')) {
      localStorage.setItem('token', 'jwt-de-prueba');
      localStorage.setItem('usuario', JSON.stringify({ nombre: 'Ana Pérez', rol: 'HUESPED' }));
    }
  });
  await ctx.route('**/js/reservas/config.js', (r) => r.fulfill({ contentType: 'text/javascript', body: `window.RESERVAS_CONFIG={API_BASE:'${base}',TOKEN_KEY:'token',LOGIN_URL:'/pages/login.html'};` }));
  const page = await ctx.newPage();
  const errores = []; page.on('pageerror', (e) => errores.push(e.message)); page.on('console', (m) => { if (m.type() === 'error' && !/status of 4|status of 5/.test(m.text())) errores.push(m.text()); });
  const paso = (n) => console.log('OK  ' + n);

  // CA-01: se muestran los datos propios; correo y rol no se editan
  await page.goto(`${base}/pages/reservas/perfil.html`);
  await page.waitForSelector('#form-perfil:not(.oculto)');
  assert.equal(await page.inputValue('#nombre'), 'Ana Pérez');
  assert.equal(await page.inputValue('#pais'), 'Colombia');
  assert.equal(await page.inputValue('#correo'), 'ana@correo.com');
  assert.equal(await page.getAttribute('#correo', 'readonly'), '');
  assert.equal(await page.inputValue('#rol'), 'Huésped');
  assert.match(await page.textContent('#estado'), /activa/i);
  assert.equal(await page.isDisabled('#guardar'), true);
  assert.match(await page.textContent('#barra'), /Mi perfil/);
  paso('CA-01: consulta de perfil, correo/rol de solo lectura');

  // CA-03: validación en el cliente, sin enviar al servidor
  await page.fill('#nombre', '');
  await page.click('#guardar');
  assert.match(await page.textContent('#e-nombre'), /obligatorio/);
  await page.fill('#nombre', 'Ana María Pérez'); await page.fill('#telefono', '12ab');
  await page.click('#guardar');
  assert.match(await page.textContent('#e-telefono'), /7 y 15/);
  paso('CA-03: validación de nombre y teléfono');

  // CA-02: actualización correcta
  await page.fill('#telefono', '+573001234567'); await page.fill('#documento', 'CC-123456');
  await page.click('#guardar');
  await page.waitForSelector('.aviso.exito');
  assert.match(await page.textContent('#mensaje'), /guardaron/);
  await page.reload(); await page.waitForSelector('#form-perfil:not(.oculto)');
  assert.equal(await page.inputValue('#nombre'), 'Ana María Pérez');
  assert.equal(await page.inputValue('#telefono'), '+573001234567');
  const u = await page.evaluate(() => JSON.parse(localStorage.getItem('usuario')));
  assert.equal(u.nombre, 'Ana María Pérez');
  paso('CA-02: los cambios se guardan y persisten');

  // Error del servidor: se informa sin perder lo escrito
  await page.fill('#nombre', 'Falla Servidor'); await page.click('#guardar');
  await page.waitForSelector('.aviso.error');
  assert.equal(await page.inputValue('#nombre'), 'Falla Servidor');
  await page.click('#descartar');
  assert.equal(await page.inputValue('#nombre'), 'Ana María Pérez');
  paso('error del servidor y descartar cambios');

  // CA-06: no se puede desactivar con reservas confirmadas futuras
  confirmarReservaDePrueba();
  await page.click('#desactivar'); await page.click('#dlg-ok');
  await page.waitForSelector('.aviso.error');
  assert.match(await page.textContent('#mensaje'), /reservas confirmadas futuras/i);
  assert.ok(await page.evaluate(() => localStorage.getItem('token')));
  paso('CA-06: desactivación rechazada con reservas confirmadas');

  // CA-05: desactivación correcta, se cierra la sesión
  vaciarReservas();
  await page.reload(); await page.waitForSelector('#form-perfil:not(.oculto)');
  await page.click('#desactivar'); await page.click('#dlg-ok');
  await page.waitForSelector('.aviso.exito');
  assert.match(await page.textContent('#mensaje'), /desactivada/);
  assert.match(await page.textContent('#mensaje'), /2 publicaciones/);
  assert.equal(await page.evaluate(() => localStorage.getItem('token')), null);
  assert.equal(await page.isVisible('#form-perfil'), false);
  paso('CA-05: cuenta desactivada y sesión cerrada');

  assert.deepEqual(errores, [], 'sin errores de consola: ' + errores.join(' | '));
  await navegador.close(); srv.close();
  console.log('Perfil: todas las pruebas pasaron');
})().catch((e) => { console.error(e); process.exit(1); });
