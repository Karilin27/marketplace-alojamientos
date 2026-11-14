/* Recorrido de punta a punta de las pantallas de S2 contra una API simulada en memoria.
   Requiere playwright:  node frontend/test/e2e/e2e-alojamientos.js
   Flujo: publicar (HU-005) → tarifas (HU-024) → activar (HU-007) → buscar (HU-009) → detalle (HU-010) → cotizar (enlace a CU-10). */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const RAIZ = path.join(__dirname, '..', '..');
const POLITICAS = [
  { id: crypto.randomUUID(), nombre: 'FLEXIBLE', diasAntelacion: 2, porcentajeDevolucion: 100 },
  { id: crypto.randomUUID(), nombre: 'MODERADA', diasAntelacion: 7, porcentajeDevolucion: 100 },
  { id: crypto.randomUUID(), nombre: 'ESTRICTA', diasAntelacion: 14, porcentajeDevolucion: 50 },
];
const db = new Map();
const ANFITRION = crypto.randomUUID();

function json(res, status, cuerpo) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*' });
  res.end(cuerpo === undefined ? '' : JSON.stringify(cuerpo));
}
function leer(req) { return new Promise((ok) => { let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => ok(b ? JSON.parse(b) : null)); }); }
function vista(a) { return Object.assign({}, a, { politicaCancelacion: POLITICAS.find((p) => p.id === a.idPoliticaCancelacion) || null, calificacionPromedio: null, totalResenas: 0 }); }

async function api(req, res, url) {
  const p = url.pathname, m = req.method;
  if (m === 'OPTIONS') return json(res, 204);
  if (p === '/api/cancellation-policies') return json(res, 200, POLITICAS);
  if (p === '/api/accommodations' && m === 'POST') {
    const b = await leer(req);
    if (!(b.capacidad > 0) || !(b.precioPorNoche > 0)) return json(res, 400, { codigo: 'ALOJAMIENTO_INVALIDO', mensaje: 'Datos inválidos', detalles: [] });
    const a = Object.assign({ id: crypto.randomUUID(), idAnfitrion: ANFITRION, estado: 'BORRADOR', tarifaLimpieza: 0, descuentoPct: 0, descuentoMinNoches: 0, serviciosAdicionales: [] }, b);
    db.set(a.id, a); return json(res, 201, vista(a));
  }
  if (p === '/api/accommodations' && m === 'GET') {
    const ciudad = (url.searchParams.get('ciudad') || '').toLowerCase();
    const items = [...db.values()].filter((a) => a.estado === 'ACTIVA' && (!ciudad || a.ciudad.toLowerCase() === ciudad))
      .map((a) => ({ id: a.id, titulo: a.titulo, imagen: (a.imagenes || [])[0] || null, ciudad: a.ciudad, tipo: a.tipo, capacidad: a.capacidad, precioPorNoche: a.precioPorNoche }));
    return json(res, 200, { items, page: 0, size: 12, total: items.length });
  }
  if (p === '/api/accommodations/mine') return json(res, 200, [...db.values()].map(vista));
  let r;
  if ((r = p.match(/^\/api\/accommodations\/mine\/(.+)$/))) return json(res, 200, vista(db.get(r[1])));
  if ((r = p.match(/^\/api\/accommodations\/([^/]+)\/status$/))) {
    const a = db.get(r[1]); const b = await leer(req);
    if (b.estado === 'ACTIVA' && (!(a.imagenes || []).length || !a.idPoliticaCancelacion)) {
      return json(res, 422, { codigo: 'INFORMACION_INCOMPLETA', mensaje: 'Complete la información antes de activar la publicación.', detalles: ['imagenes (al menos una)', 'politicaCancelacion'] });
    }
    const ant = a.estado; a.estado = b.estado; return json(res, 200, { id: a.id, estadoAnterior: ant, estadoNuevo: a.estado, fecha: new Date().toISOString() });
  }
  if ((r = p.match(/^\/api\/accommodations\/([^/]+)\/pricing$/))) {
    const a = db.get(r[1]); const b = await leer(req);
    Object.assign(a, { tarifaLimpieza: b.tarifaLimpieza, descuentoPct: b.descuentoPct, descuentoMinNoches: b.descuentoMinNoches,
      serviciosAdicionales: b.serviciosAdicionales.map((s) => Object.assign({ id: crypto.randomUUID() }, s)) });
    return json(res, 200, vista(a));
  }
  if ((r = p.match(/^\/api\/accommodations\/([^/]+)$/))) {
    const a = db.get(r[1]);
    if (m === 'PUT') { Object.assign(a, await leer(req)); return json(res, 200, vista(a)); }
    if (!a || a.estado !== 'ACTIVA') return json(res, 404, { codigo: 'NO_ENCONTRADO', mensaje: 'No es posible mostrar esta publicación.' });
    return json(res, 200, vista(a));
  }
  return json(res, 404, { codigo: 'NO_ENCONTRADO', mensaje: 'Ruta no simulada: ' + p });
}

function servidor(puerto) {
  return new Promise((ok) => {
    const s = http.createServer(async (req, res) => {
      const url = new URL(req.url, 'http://x');
      if (url.pathname.startsWith('/api/')) return api(req, res, url);
      const archivo = path.join(RAIZ, url.pathname.startsWith('/pages/') || url.pathname.startsWith('/js/') || url.pathname.startsWith('/css/') ? url.pathname : '/pages' + url.pathname);
      fs.readFile(archivo, (e, d) => {
        if (e) { res.writeHead(404); return res.end(); }
        const tipo = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' }[path.extname(archivo)] || 'application/octet-stream';
        res.writeHead(200, { 'Content-Type': tipo + '; charset=utf-8' }); res.end(d);
      });
    }).listen(puerto, () => ok(s));
  });
}

(async () => {
  const puerto = 8098, base = 'http://127.0.0.1:' + puerto;
  const srv = await servidor(puerto);
  const nav = await chromium.launch();
  const ctx = await nav.newContext();
  await ctx.addInitScript((u) => { localStorage.setItem('token', 'jwt-de-prueba'); localStorage.setItem('usuario', JSON.stringify({ id: u, rol: 'ANFITRION', nombre: 'Anfitrión' })); }, ANFITRION);
  await ctx.route('**/js/config.js', (r) => r.fulfill({ contentType: 'text/javascript', body: `window.APP_CONFIG={API_BASE:'${base}'};` }));
  const page = await ctx.newPage();
  const errores = []; page.on('pageerror', (e) => errores.push(e.message));
  const paso = (n) => console.log('OK  ' + n);

  // HU-005: validación y creación (A-01)
  await page.goto(base + '/pages/alojamientos/publicar.html');
  await page.waitForSelector('#politica option:nth-child(4)', { state: 'attached' });
  await page.click('#btn-guardar');
  assert.match(await page.textContent('#e-titulo'), /obligatorio/); paso('HU-005 CA-02: campos obligatorios');
  await page.fill('#titulo', 'Apartamento Chapinero'); await page.fill('#descripcion', 'Cerca a la zona G');
  await page.selectOption('#tipo', 'APARTAMENTO'); await page.fill('#ciudad', 'Bogotá'); await page.fill('#direccion', 'Calle 60 # 5-20');
  await page.fill('#capacidad', '0'); await page.fill('#precioPorNoche', '200000'); await page.click('#btn-guardar');
  assert.match(await page.textContent('#e-capacidad'), /mayor que cero/); paso('HU-005 CP-05-02: capacidad inválida');
  await page.fill('#capacidad', '4'); await page.check('#servicios input[value="WiFi"]'); await page.click('#btn-guardar');
  await page.waitForSelector('#form-tarifas:not(.oculto)');
  assert.match(await page.textContent('#mensaje'), /Borrador/); paso('HU-005 CP-05-01: publicación creada en BORRADOR');

  // HU-024: tarifas con servicio adicional
  await page.fill('#tarifaLimpieza', '-1'); await page.click('#btn-tarifas');
  assert.match(await page.textContent('#e-tarifaLimpieza'), /negativa/); paso('HU-024 CP-05-05: tarifa negativa');
  await page.fill('#tarifaLimpieza', '50000'); await page.fill('#descuentoPct', '10'); await page.fill('#descuentoMinNoches', '7');
  await page.click('#btn-agregar'); await page.fill('.ad-nombre', 'Desayuno'); await page.fill('.ad-costo', '30000'); await page.click('#btn-tarifas');
  await page.waitForFunction(() => /Tarifas guardadas/.test(document.getElementById('mensaje').textContent));
  paso('HU-024 CP-05-04: tarifas y servicio adicional guardados');

  // HU-007: activar sin imágenes ni política falla; con ellas funciona
  await page.goto(base + '/pages/alojamientos/mis-publicaciones.html');
  await page.click('text=Activar');
  await page.waitForFunction(() => /Complete la información/.test(document.getElementById('mensaje').textContent));
  paso('HU-007 CP-06-02: activación rechazada por información incompleta');
  await page.click('text=Editar y tarifas'); await page.waitForSelector('#politica option:nth-child(4)', { state: 'attached' });
  await page.fill('#imagenes', 'https://img.ejemplo.com/1.jpg'); await page.selectOption('#politica', await page.$eval('#politica', (s) => [...s.options].find((o) => o.text.startsWith('MODERADA')).value));
  await page.click('#btn-guardar'); await page.waitForFunction(() => /guardada/.test(document.getElementById('mensaje').textContent));
  await page.goto(base + '/pages/alojamientos/mis-publicaciones.html'); await page.click('text=Activar');
  await page.waitForSelector('.estado.ACTIVA'); paso('HU-007 CP-06-01: publicación ACTIVA');

  // HU-009 y HU-010
  await page.goto(base + '/pages/alojamientos/buscar.html');
  await page.fill('#ciudad', 'Bogotá'); await page.fill('#llegada', '2026-12-10'); await page.fill('#salida', '2026-12-10'); await page.click('#btn-buscar');
  assert.match(await page.textContent('#e-filtros'), /posterior/); paso('HU-009 CP-08-04: fechas inválidas');
  await page.fill('#salida', '2026-12-13'); await page.click('#btn-buscar');
  await page.waitForSelector('.tarjeta'); paso('HU-009 CP-08-01: resultado con título, ciudad, tipo, capacidad y precio');
  await page.fill('#ciudad', 'Leticia'); await page.click('#btn-buscar');
  await page.waitForFunction(() => /No encontramos/.test(document.getElementById('mensaje').textContent)); paso('HU-009 CP-08-05: sin resultados');
  await page.fill('#ciudad', 'Bogotá'); await page.click('#btn-buscar'); await page.waitForSelector('.tarjeta a');
  await page.click('.tarjeta a'); await page.waitForSelector('#detalle:not(.oculto)');
  const precios = await page.textContent('#precios');
  assert.match(precios, /200\.000/); assert.match(precios, /Desayuno/);
  assert.match(await page.textContent('#politica'), /MODERADA/); paso('HU-010 CP-09-01: detalle con precios, servicio adicional y política');
  assert.match(await page.getAttribute('#cotizar', 'href'), /reservas\/reservar\.html\?alojamiento=/); paso('HU-010 CP-09-04: enlace a cotizar (CU-10)');
  await page.goto(base + '/pages/alojamientos/detalle.html?id=no-existe');
  await page.waitForFunction(() => /No es posible mostrar/.test(document.getElementById('mensaje').textContent)); paso('HU-010 CP-09-03: publicación inexistente');

  assert.deepEqual(errores, []); paso('sin errores de JavaScript');
  await nav.close(); srv.close();
})().catch((e) => { console.error('FALLÓ:', e.message); process.exit(1); });
