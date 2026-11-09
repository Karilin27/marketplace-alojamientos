/* API simulada mínima (solo para probar el frontend sin backend). Replica fórmula y estados de S3/S4/S5. */
const http = require('http');
const fs = require('fs');
const path = require('path');

const RAIZ = path.resolve(__dirname, '../..');
const ALOJ = '00000000-0000-0000-0000-0000000a0001';
const reservas = []; const notifs = []; let periodos = [];
let ahora = () => Date.now();

function cotizar(b) {
  const noches = Math.round((Date.parse(b.fechaSalida) - Date.parse(b.fechaLlegada)) / 86400000);
  const subtotal = 200000 * noches, descuento = noches >= 7 ? subtotal * 0.1 : 0;
  const servicio = Math.round((subtotal - descuento) * 0.1);
  const extras = (b.idsServiciosAdicionales || []).map(() => ({ id: 's1', nombre: 'Desayuno', costo: 30000 }));
  const total = subtotal - descuento + 50000 + servicio + extras.reduce((a, e) => a + e.costo, 0);
  return { noches, precioPorNoche: 200000, subtotal, descuento, tarifaLimpieza: 50000, tarifaServicio: servicio, serviciosAdicionales: extras, total };
}
const vista = (r) => ({ id: r.id, idAlojamiento: ALOJ, idHuesped: 'u1', fechaLlegada: r.llegada, fechaSalida: r.salida, huespedes: r.huespedes, estado: r.estado,
  desglose: { subtotal: 0, descuento: 0, tarifaLimpieza: 0, tarifaServicio: 0, serviciosAdicionales: [], total: r.total },
  politicaAplicada: { nombre: 'MODERADA', diasAntelacion: 7, porcentajeDevolucion: 100 }, venceEn: r.estado === 'PENDIENTE_PAGO' ? new Date(r.vence).toISOString() : null,
  revisionManual: false, montoReembolso: r.reembolso ?? null, creadaEn: new Date().toISOString() });

function manejar(req, res, cuerpo) {
  const url = new URL(req.url, 'http://x'); const p = url.pathname; const m = req.method;
  const json = (st, o) => { res.writeHead(st, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(o)); };
  const err = (st, codigo, mensaje) => json(st, { codigo, mensaje, detalles: [] });
  if (p === '/api/accommodations/' + ALOJ) return json(200, { id: ALOJ, capacidad: 4, serviciosAdicionales: [{ id: 's1', nombre: 'Desayuno', costo: 30000, activo: true }] });
  if (p === '/api/quotes' && m === 'POST') {
    const b = JSON.parse(cuerpo); if (b.huespedes > 4) return err(422, 'CAPACIDAD_EXCEDIDA', 'x');
    const c = cotizar(b); return json(200, { idAlojamiento: ALOJ, fechaLlegada: b.fechaLlegada, fechaSalida: b.fechaSalida, huespedes: b.huespedes, desglose: c, politicaAplicada: { nombre: 'MODERADA', diasAntelacion: 7, porcentajeDevolucion: 100 }, tokenCotizacion: 'tok.' + Buffer.from(JSON.stringify(b)).toString('base64url') + '.firmaFirmaFirmaFirma', venceEn: new Date(ahora() + 1800000).toISOString() });
  }
  if (p === '/api/bookings' && m === 'POST') {
    const llave = req.headers['idempotency-key']; if (!llave) return err(400, 'CABECERA_REQUERIDA', 'falta');
    let r = reservas.find((x) => x.llave === llave);
    if (!r) { const b = JSON.parse(Buffer.from(JSON.parse(cuerpo).tokenCotizacion.split('.')[1], 'base64url').toString()); const c = cotizar(b);
      r = { id: 'r' + (reservas.length + 1), llave, llegada: b.fechaLlegada, salida: b.fechaSalida, huespedes: b.huespedes, estado: 'PENDIENTE_PAGO', total: c.total, vence: ahora() + 900000 }; reservas.push(r);
      notifs.push({ id: 'n' + (notifs.length + 1), tipo: 'RESERVA_CREADA', mensaje: 'Reservaste. Tienes 15 minutos para pagar.', leida: false, fecha: new Date().toISOString() }); }
    return json(201, vista(r));
  }
  if (p === '/api/bookings' && m === 'GET') return json(200, { contenido: reservas.map(vista), page: 0, size: 20, total: reservas.length });
  let g;
  if ((g = /^\/api\/bookings\/([^/]+)$/.exec(p)) && m === 'GET') { const r = reservas.find((x) => x.id === g[1]); return r ? json(200, vista(r)) : err(404, 'NO_ENCONTRADO', 'no'); }
  if ((g = /^\/api\/bookings\/([^/]+)\/cancel$/.exec(p)) && m === 'POST') {
    const r = reservas.find((x) => x.id === g[1]); if (r.estado !== 'CONFIRMADA') return err(409, 'RESERVA_NO_CANCELABLE', 'x');
    if (r.estado === 'CONFIRMADA') { r.estado = 'CANCELADA'; r.reembolso = r.total; notifs.push({ id: 'n' + (notifs.length + 1), tipo: 'REEMBOLSO', mensaje: 'Se reembolsó ' + r.total, leida: false, fecha: new Date().toISOString() }); }
    return json(200, vista(r));
  }
  if (p === '/api/payments' && m === 'POST') {
    const b = JSON.parse(cuerpo); const r = reservas.find((x) => x.id === b.idReserva);
    if (b.metodoPago.includes('Declined')) { r.estado = 'RECHAZADA'; return err(422, 'PAGO_RECHAZADO', 'La tarjeta fue rechazada.'); }
    r.estado = 'CONFIRMADA'; notifs.push({ id: 'n' + (notifs.length + 1), tipo: 'PAGO_APROBADO', mensaje: 'Tu pago fue aprobado.', leida: false, fecha: new Date().toISOString() });
    return json(200, { id: 'p1', idReserva: r.id, estado: 'APROBADO', estadoReserva: 'CONFIRMADA' });
  }
  if (p === '/api/notifications' && m === 'GET') { const l = url.searchParams.get('unread') === 'true' ? notifs.filter((n) => !n.leida) : notifs; return json(200, { contenido: l, page: 0, size: 20, total: l.length }); }
  if ((g = /^\/api\/notifications\/([^/]+)\/read$/.exec(p)) && m === 'PATCH') { const n = notifs.find((x) => x.id === g[1]); n.leida = true; return json(200, n); }
  if (p === '/api/bookings/received') return json(200, { contenido: [], page: 0, size: 20, total: 0 });
  if ((g = /^\/api\/availability\/(.+)$/.exec(p))) {
    if (m === 'PUT') { periodos = JSON.parse(cuerpo).periodos; return json(200, { idAlojamiento: g[1], periodos }); }
    return json(200, { idAlojamiento: g[1], periodos: [...periodos, ...reservas.filter((r) => r.estado === 'CONFIRMADA').map((r) => ({ fechaInicio: r.llegada, fechaFin: r.salida, tipo: 'RESERVADO' }))] });
  }
  return null;
}

function iniciar(puerto) {
  return new Promise((ok) => {
    const s = http.createServer((req, res) => {
      let cuerpo = ''; req.on('data', (c) => (cuerpo += c)); req.on('end', () => {
        if (req.url.startsWith('/api/')) { if (manejar(req, res, cuerpo) === null) { res.writeHead(404); res.end('{}'); } return; }
        const archivo = path.join(RAIZ, decodeURIComponent(req.url.split('?')[0]));
        if (!archivo.startsWith(RAIZ) || !fs.existsSync(archivo) || fs.statSync(archivo).isDirectory()) { res.writeHead(404); return res.end(); }
        const tipos = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' };
        res.writeHead(200, { 'Content-Type': (tipos[path.extname(archivo)] || 'text/plain') + '; charset=utf-8' }); res.end(fs.readFileSync(archivo));
      });
    }).listen(puerto, '127.0.0.1', () => ok(s));
  });
}
module.exports = { iniciar, ALOJ };
