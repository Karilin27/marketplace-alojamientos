/* Recorrido del panel de administración (HU-018 a HU-022) y de la recuperación de acceso (HU-003)
   contra una API simulada. Requiere playwright: node frontend/test/e2e/e2e-admin.js */
const http = require('node:http'); const fs = require('node:fs'); const path = require('node:path');
const crypto = require('node:crypto'); const assert = require('node:assert/strict'); const { chromium } = require('playwright');
const RAIZ = path.join(__dirname, '..', '..');
const ADMIN = crypto.randomUUID();
const usuarios = [
  { id: ADMIN, nombre: 'Admin', correo: 'admin@test.com', rol: 'ADMINISTRADOR', estado: 'ACTIVO', motivoRestriccion: null, fechaRegistro: new Date().toISOString() },
  { id: crypto.randomUUID(), nombre: 'Ana Pérez', correo: 'huesped@test.com', rol: 'HUESPED', estado: 'ACTIVO', motivoRestriccion: null, fechaRegistro: new Date().toISOString() },
];
const pubs = [{ id: crypto.randomUUID(), titulo: 'Apartamento Chapinero', ciudad: 'Bogotá', tipo: 'APARTAMENTO', estado: 'PENDIENTE_REVISION', anfitrion: 'Carlos', correoAnfitrion: 'anfitrion@test.com', reservasFuturas: 1 }];
const revisiones = []; const auditoria = []; let tokens = {};
const json = (res, s, b) => { res.writeHead(s, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*', 'Access-Control-Expose-Headers': 'Content-Disposition' }); res.end(b === undefined ? '' : JSON.stringify(b)); };
const leer = (req) => new Promise((ok) => { let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => ok(b ? JSON.parse(b) : null)); });
const audit = (accion, entidad) => auditoria.unshift({ id: crypto.randomUUID(), usuario: 'Admin', entidad, idEntidad: '', accion, resultado: 'EXITO', fecha: new Date().toISOString() });
async function api(req, res, url) {
  const p = url.pathname, m = req.method; let r;
  if (m === 'OPTIONS') return json(res, 204);
  if (p === '/api/auth/password-recovery') { const b = await leer(req); const u = usuarios.find((x) => x.correo === b.correo);
    const t = u ? crypto.randomBytes(8).toString('hex') : null; if (u) tokens[t] = u.id;
    return json(res, 200, { mensaje: 'Si el correo está registrado, se generó un enlace de recuperación válido por 30 minutos.', tokenSimulado: t, expiraEn: null }); }
  if (p === '/api/auth/password-reset') { const b = await leer(req); if (!tokens[b.token]) return json(res, 422, { codigo: 'TOKEN_INVALIDO', mensaje: 'El enlace no es válido, ya se usó o venció. Solicite uno nuevo.' });
    delete tokens[b.token]; return json(res, 200, { mensaje: 'Su contraseña fue actualizada. Ya puede iniciar sesión.' }); }
  if (p === '/api/admin/dashboard') return json(res, 200, { usuarios: 2, usuariosPorRol: { ADMINISTRADOR: 1, HUESPED: 1 }, publicacionesActivas: pubs.filter((x) => x.estado === 'ACTIVA').length,
    reservasPorEstado: { CONFIRMADA: 1, COMPLETADA: 1, CANCELADA: 1, RECHAZADA: 1, PENDIENTE_PAGO: 0 }, ingresosEstimados: 1450000, ciudadesMayorDemanda: [{ ciudad: 'Bogotá', reservas: 2 }], calculadoEn: new Date().toISOString() });
  if (p === '/api/admin/reports/export') { const b = await leer(req); audit('REPORTE_EXPORTADO', 'reporte');
    res.writeHead(200, { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="reporte-' + b.tipo.toLowerCase() + '.pdf"', 'Access-Control-Allow-Origin': '*', 'Access-Control-Expose-Headers': 'Content-Disposition' }); return res.end('%PDF-1.4 prueba'); }
  if (p === '/api/admin/users') return json(res, 200, usuarios);
  if ((r = p.match(/^\/api\/admin\/users\/([^/]+)\/status$/))) { const b = await leer(req); const u = usuarios.find((x) => x.id === r[1]);
    if (b.estado === 'RESTRINGIDO' && !b.motivo) return json(res, 400, { mensaje: 'motivo obligatorio' });
    u.estado = b.estado; u.motivoRestriccion = b.motivo; audit('USUARIO_' + (b.estado === 'RESTRINGIDO' ? 'RESTRINGIDO' : 'REACTIVADO'), 'usuario'); return json(res, 200, u); }
  if (p === '/api/admin/accommodations') { const e = url.searchParams.get('estado'); return json(res, 200, pubs.filter((x) => !e || x.estado === e)); }
  if ((r = p.match(/^\/api\/admin\/accommodations\/([^/]+)\/moderation$/))) { const b = await leer(req); const x = pubs.find((y) => y.id === r[1]); const ant = x.estado;
    const destino = { APROBAR: 'ACTIVA', PAUSAR: 'PAUSADA', BLOQUEAR: 'BLOQUEADA', DESBLOQUEAR: 'PAUSADA' }[b.accion]; x.estado = destino;
    let n = 0; if (b.accion === 'BLOQUEAR') { n = 1; revisiones.push({ id: crypto.randomUUID(), alojamiento: x.titulo, huesped: 'Ana Pérez', fechaLlegada: '2026-12-10', fechaSalida: '2026-12-13', total: 710000, estado: 'CONFIRMADA' }); }
    audit('PUBLICACION_' + b.accion, 'alojamiento'); return json(res, 200, { id: x.id, estadoAnterior: ant, estadoNuevo: destino, reservasEnRevision: n, fecha: new Date().toISOString() }); }
  if (p === '/api/admin/bookings/manual-review') return json(res, 200, revisiones);
  if ((r = p.match(/^\/api\/admin\/bookings\/([^/]+)\/manual-review$/))) { revisiones.splice(revisiones.findIndex((x) => x.id === r[1]), 1); audit('REVISION_MANUAL_CERRADA', 'reserva'); return json(res, 200, { mensaje: 'ok' }); }
  if (p === '/api/admin/audit') { const e = url.searchParams.get('entidad'); const items = auditoria.filter((x) => !e || x.entidad === e); return json(res, 200, { items, page: 0, size: 50, total: items.length }); }
  return json(res, 404, { mensaje: 'no simulada ' + p });
}
const servidor = (port) => new Promise((ok) => { const s = http.createServer(async (req, res) => { const url = new URL(req.url, 'http://x');
  if (url.pathname.startsWith('/api/')) return api(req, res, url);
  const f = path.join(RAIZ, url.pathname); fs.readFile(f, (e, d) => { if (e) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { 'Content-Type': ({ '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' }[path.extname(f)] || 'text/plain') + '; charset=utf-8' }); res.end(d); }); }).listen(port, () => ok(s)); });
(async () => {
  const port = 8097, base = 'http://127.0.0.1:' + port; const srv = await servidor(port);
  const nav = await chromium.launch(); const ctx = await nav.newContext({ acceptDownloads: true });
  await ctx.route('**/js/config.js', async (r) => { const orig = fs.readFileSync(path.join(RAIZ, 'js/config.js'), 'utf8'); r.fulfill({ contentType: 'text/javascript', body: orig.replace(/API_BASE:\s*"[^"]*"/, 'API_BASE: "' + base + '"') }); });
  const page = await ctx.newPage(); const errores = []; page.on('pageerror', (e) => errores.push(e.message)); const paso = (n) => console.log('OK  ' + n);
  // HU-003
  await page.goto(base + '/pages/recuperar.html'); await page.fill('#correo', 'huesped@test.com'); await page.click('#btnSolicitar');
  await page.waitForSelector('#formRestablecer:not([hidden])'); paso('HU-003 CP-03-01: se genera el código simulado');
  await page.fill('#nueva', 'abc12'); await page.fill('#confirmar', 'abc12'); await page.click('#btnRestablecer');
  assert.match(await page.textContent('#mensajeRestablecer'), /8 caracteres/); paso('HU-003 CP-03-06: contraseña corta');
  await page.fill('#nueva', 'Nueva2026*'); await page.fill('#confirmar', 'Nueva2026*'); await page.click('#btnRestablecer');
  await page.waitForFunction(() => /actualizada/.test(document.getElementById('mensajeRestablecer').textContent)); paso('HU-003 CP-03-02: contraseña actualizada');
  await page.goto(base + '/pages/recuperar.html'); await page.fill('#correo', 'noexiste@test.com'); await page.click('#btnSolicitar');
  await page.waitForFunction(() => /Si el correo/.test(document.getElementById('mensajeSolicitar').textContent));
  assert.equal(await page.isHidden('#formRestablecer'), true); paso('HU-003 CP-03-03: mismo mensaje y sin código para un correo no registrado');
  // Sin rol de administrador
  await page.evaluate(() => { localStorage.setItem('token', 't'); localStorage.setItem('usuario', JSON.stringify({ rol: 'HUESPED' })); });
  await page.goto(base + '/pages/admin/dashboard.html'); await page.waitForFunction(() => /Solo un administrador/.test(document.getElementById('mensaje').textContent));
  paso('HU-020 CP-20-04: un no administrador no ve el panel');
  await page.evaluate((id) => localStorage.setItem('usuario', JSON.stringify({ id, rol: 'ADMINISTRADOR', nombre: 'Admin' })), ADMIN);
  // HU-020 y HU-021
  await page.goto(base + '/pages/admin/dashboard.html'); await page.waitForSelector('.kpi');
  assert.match(await page.textContent('#kpis'), /1\.450\.000/); assert.match(await page.textContent('#ciudades'), /Bogotá/); paso('HU-020 CP-20-01: indicadores e ingresos estimados');
  await page.selectOption('#tipo', 'RESERVAS'); const [descarga] = await Promise.all([page.waitForEvent('download'), page.click('#btn-exportar')]);
  assert.equal(descarga.suggestedFilename(), 'reporte-reservas.pdf'); paso('HU-021 CP-21-01: descarga del reporte PDF');
  // HU-022
  await page.goto(base + '/pages/admin/usuarios.html'); await page.waitForSelector('#tabla tr:nth-child(3)');
  await page.click('#tabla button.peligro'); await page.waitForFunction(() => /motivo/.test(document.getElementById('mensaje').textContent)); paso('HU-022 CP-18-04: restringir sin motivo');
  await page.fill('#tabla input', 'Incumplimiento de políticas'); await page.click('#tabla button.peligro');
  await page.waitForFunction(() => /restringido/.test(document.getElementById('mensaje').textContent)); await page.waitForSelector('.estado.RESTRINGIDO'); paso('HU-022 CP-18-02: cuenta restringida');
  await page.click('text=Reactivar'); await page.waitForFunction(() => /reactivado/.test(document.getElementById('mensaje').textContent)); paso('HU-022 CP-18-03: cuenta reactivada');
  // HU-018
  await page.goto(base + '/pages/admin/publicaciones.html'); await page.waitForSelector('#tabla button');
  await page.click('text=Aprobar'); await page.waitForFunction(() => /pasó a Activa/.test(document.getElementById('mensaje').textContent)); paso('HU-018 CP-17-01: aprobar');
  await page.selectOption('#estado', ''); await page.click('#filtros button'); await page.waitForSelector('#tabla button.peligro');
  await page.click('#tabla button.peligro'); await page.waitForFunction(() => /motivo para bloquear/.test(document.getElementById('mensaje').textContent)); paso('HU-018 CP-17-05: bloquear sin motivo');
  await page.fill('#tabla input', 'Información engañosa'); await page.click('#tabla button.peligro');
  await page.waitForFunction(() => /revisión manual/.test(document.getElementById('mensaje').textContent)); await page.waitForSelector('#revisiones input'); paso('HU-018 CP-17-03: bloqueo con reservas futuras en revisión manual');
  await page.fill('#revisiones input', 'Se reubica al huésped'); await page.click('#revisiones button');
  await page.waitForFunction(() => /Decisión registrada/.test(document.getElementById('mensaje').textContent)); paso('HU-018 CP-17-04: decisión de revisión manual');
  // HU-019
  await page.goto(base + '/pages/admin/auditoria.html'); await page.waitForFunction(() => document.querySelectorAll('#tabla tr').length > 3);
  assert.match(await page.textContent('#tabla'), /PUBLICACION_BLOQUEAR/); paso('HU-019 CP-19-01: eventos de auditoría');
  await page.selectOption('#entidad', 'usuario'); await page.click('#filtros button'); await page.waitForFunction(() => !/PUBLICACION/.test(document.getElementById('tabla').textContent));
  paso('HU-019 CP-19-02: filtro por entidad');
  assert.deepEqual(errores, []); paso('sin errores de JavaScript');
  await nav.close(); srv.close();
})().catch((e) => { console.error('FALLÓ:', e.message); process.exit(1); });
