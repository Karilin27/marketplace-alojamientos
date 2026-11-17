/* API simulada mínima (solo para probar el frontend sin backend). Replica fórmula y estados de S3/S4/S5. */
const http = require('http');
const fs = require('fs');
const path = require('path');

const RAIZ = path.resolve(__dirname, '../..');
const ALOJ = '00000000-0000-0000-0000-0000000a0001';
const reservas = []; const notifs = []; let periodos = [];
let ahora = () => Date.now();
const perfil = { id: 'u1', nombre: 'Ana Pérez', correo: 'ana@correo.com', telefono: null, documento: null, pais: 'Colombia', fotoUrl: null, rol: 'HUESPED', estado: 'ACTIVO' };
const confirmarReservaDePrueba = () => reservas.push({ id: 'rx', llave: 'k', llegada: '2030-01-01', salida: '2030-01-03', huespedes: 1, estado: 'CONFIRMADA', total: 1, vence: 0 });

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
  if (p === '/api/users/me' && m === 'GET') return json(200, perfil);
  if (p === '/api/users/me' && m === 'PUT') {
    const b = JSON.parse(cuerpo), det = [];
    if (!b.nombre || b.nombre.length < 2) det.push('nombre: debe tener entre 2 y 100 caracteres.');
    if (b.telefono && !/^\+?[0-9]{7,15}$/.test(b.telefono.replace(/[ -]/g, ''))) det.push('telefono: use entre 7 y 15 dígitos, con + opcional al inicio.');
    if (b.nombre === 'Falla Servidor') return err(500, 'ERROR_INTERNO', 'Ocurrió un error inesperado.');
    if (det.length) return json(400, { codigo: 'PERFIL_INVALIDO', mensaje: 'Hay datos del perfil que debe corregir.', detalles: det });
    Object.assign(perfil, { nombre: b.nombre, telefono: b.telefono ?? null, documento: b.documento ?? null, pais: b.pais ?? null, fotoUrl: b.fotoUrl ?? null });
    return json(200, perfil);
  }
  if (p === '/api/users/me/status' && m === 'PATCH') {
    if (JSON.parse(cuerpo).estado !== 'DESACTIVADO') return err(400, 'ESTADO_INVALIDO', 'El estado debe ser ACTIVO o DESACTIVADO.');
    const futuras = reservas.filter((r) => r.estado === 'CONFIRMADA').length;
    if (futuras) return json(422, { codigo: 'RESERVAS_CONFIRMADAS_PENDIENTES', mensaje: 'No puede desactivar su cuenta mientras tenga reservas confirmadas futuras.', detalles: ['Reservas confirmadas futuras: ' + futuras] });
    perfil.estado = 'DESACTIVADO';
    return json(200, { estado: 'DESACTIVADO', sesionCerrada: true, publicacionesPausadas: 2 });
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
const vaciarReservas = () => { reservas.length = 0; };
module.exports = { iniciar, ALOJ, confirmarReservaDePrueba, vaciarReservas };
