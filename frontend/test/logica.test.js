const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/reservas/logica.js');

test('nochesEntre cuenta noches y rechaza fechas inexistentes', () => {
  assert.equal(L.nochesEntre('2026-12-12', '2026-12-15'), 3);
  assert.equal(L.nochesEntre('2026-12-31', '2027-01-02'), 2);
  assert.equal(L.nochesEntre('2026-02-31', '2026-03-02'), 0);
  assert.equal(L.nochesEntre('', '2026-03-02'), 0);
});

test('validarCotizacion: fechas, pasado y capacidad (RN-05, RN-16)', () => {
  const hoy = '2026-11-10';
  assert.deepEqual(L.validarCotizacion({ llegada: '2026-12-12', salida: '2026-12-15', huespedes: 2, capacidad: 4 }, hoy), {});
  assert.ok(L.validarCotizacion({ llegada: '', salida: '2026-12-15', huespedes: 2 }, hoy).llegada);
  assert.ok(L.validarCotizacion({ llegada: '2026-11-09', salida: '2026-12-15', huespedes: 2 }, hoy).llegada);
  assert.ok(L.validarCotizacion({ llegada: '2026-12-15', salida: '2026-12-15', huespedes: 2 }, hoy).salida);
  assert.ok(L.validarCotizacion({ llegada: '2026-12-15', salida: '2026-12-12', huespedes: 2 }, hoy).salida);
  assert.ok(L.validarCotizacion({ llegada: '2026-12-12', salida: '2026-12-15', huespedes: 0 }, hoy).huespedes);
  assert.ok(L.validarCotizacion({ llegada: '2026-12-12', salida: '2026-12-15', huespedes: 5, capacidad: 4 }, hoy).huespedes);
  assert.ok(L.validarCotizacion({ llegada: '2026-12-12', salida: '2026-12-15', huespedes: 1.5 }, hoy).huespedes);
});

test('validarPeriodos detecta pasado, fin<=inicio y superposición', () => {
  const hoy = '2026-11-10';
  assert.deepEqual(L.validarPeriodos([{ fechaInicio: '2026-11-20', fechaFin: '2026-11-25' }, { fechaInicio: '2026-11-25', fechaFin: '2026-11-28' }], hoy), []);
  assert.equal(L.validarPeriodos([{ fechaInicio: '2026-11-20', fechaFin: '2026-11-25' }, { fechaInicio: '2026-11-24', fechaFin: '2026-11-28' }], hoy).length, 1);
  assert.equal(L.validarPeriodos([{ fechaInicio: '2026-11-01', fechaFin: '2026-11-05' }], hoy).length, 1);
  assert.equal(L.validarPeriodos([{ fechaInicio: '2026-11-20', fechaFin: '2026-11-20' }], hoy).length, 1);
  assert.equal(L.validarPeriodos([{ fechaInicio: '', fechaFin: '2026-11-20' }], hoy).length, 1);
});

test('devolucionEstimada replica CAL-04..06', () => {
  const moderada = { nombre: 'MODERADA', diasAntelacion: 7, porcentajeDevolucion: 100 };
  const estricta = { nombre: 'ESTRICTA', diasAntelacion: 14, porcentajeDevolucion: 50 };
  assert.equal(L.devolucionEstimada(710000, moderada, '2026-11-30', '2026-11-20'), 710000); // 10 días
  assert.equal(L.devolucionEstimada(710000, moderada, '2026-11-23', '2026-11-20'), 0);      // 3 días
  assert.equal(L.devolucionEstimada(700000, estricta, '2026-12-10', '2026-11-20'), 350000); // 20 días
  assert.equal(L.devolucionEstimada(700000, estricta, '2026-11-19', '2026-11-20'), null);   // ya pasó
});

test('puedeCancelar solo CONFIRMADA con llegada vigente', () => {
  assert.equal(L.puedeCancelar({ estado: 'CONFIRMADA', fechaLlegada: '2026-12-01' }, '2026-11-20'), true);
  assert.equal(L.puedeCancelar({ estado: 'CONFIRMADA', fechaLlegada: '2026-11-01' }, '2026-11-20'), false);
  assert.equal(L.puedeCancelar({ estado: 'PENDIENTE_PAGO', fechaLlegada: '2026-12-01' }, '2026-11-20'), false);
  assert.equal(L.puedeCancelar({ estado: 'CANCELADA', fechaLlegada: '2026-12-01' }, '2026-11-20'), false);
});

test('cuenta regresiva de 15 minutos', () => {
  const ahora = Date.parse('2026-11-12T15:00:00Z');
  assert.equal(L.segundosRestantes('2026-11-12T15:15:00Z', ahora), 900);
  assert.equal(L.formatearCuenta(900), '15:00');
  assert.equal(L.formatearCuenta(65), '01:05');
  assert.equal(L.segundosRestantes('2026-11-12T14:59:00Z', ahora), 0);
  assert.equal(L.segundosRestantes('no-fecha', ahora), 0);
});

test('mensajeError prioriza el código y nunca expone undefined', () => {
  assert.match(L.mensajeError({ codigo: 'FECHAS_NO_DISPONIBLES', mensaje: 'x' }, 409), /ya no están disponibles/);
  assert.equal(L.mensajeError({ codigo: 'OTRO', mensaje: 'Falla', detalles: ['a', 'b'] }, 422), 'Falla (a; b)');
  assert.match(L.mensajeError(null, 0), /conexión/);
  assert.match(L.mensajeError(null, 500), /500/);
});

test('formatearCOP y etiquetas', () => {
  assert.equal(L.formatearCOP(710000).replace(/\s/g, ''), '$710.000');
  assert.equal(L.formatearCOP('abc'), '—');
  assert.equal(L.etiquetaEstado('CONFIRMADA').clase, 'ok');
  assert.equal(L.etiquetaEstado('XYZ').texto, 'XYZ');
  assert.equal(L.textoPolitica({ nombre: 'MODERADA', diasAntelacion: 7, porcentajeDevolucion: 100 }).includes('7 días'), true);
});

test('nuevaLlave produce valores distintos', () => {
  assert.notEqual(L.nuevaLlave(), L.nuevaLlave());
});
