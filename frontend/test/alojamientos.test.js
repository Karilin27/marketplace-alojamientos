// Pruebas de la lógica del frontend de S2: node --test frontend/test/alojamientos.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/alojamientos/logica.js');

test('HU-009 CA-04: fechas y precios inválidos', () => {
  assert.match(L.validarFiltros({ llegada: '2026-12-10', salida: '2026-12-10' }, '2026-11-13'), /posterior/);
  assert.match(L.validarFiltros({ llegada: '2026-12-10' }, '2026-11-13'), /ninguna/);
  assert.match(L.validarFiltros({ precioMin: '300000', precioMax: '100000' }, '2026-11-13'), /mayor/);
  assert.match(L.validarFiltros({ llegada: '2026-11-01', salida: '2026-11-03' }, '2026-11-13'), /pasada/);
  assert.equal(L.validarFiltros({ ciudad: 'Bogotá', llegada: '2026-12-13', salida: '2026-12-16', huespedes: '2' }, '2026-11-13'), '');
});

test('HU-005 CA-02: capacidad y precio deben ser mayores que cero (CP-05-02)', () => {
  const e = L.validarPublicacion({ titulo: 'Casa', descripcion: 'd', ciudad: 'Bogotá', direccion: 'Cra 1', tipo: 'CASA', capacidad: 0, precioPorNoche: -100 });
  assert.ok(e.capacidad); assert.ok(e.precioPorNoche); assert.equal(Object.keys(e).length, 2);
});

test('HU-005 CA-01: publicación A-01 completa es válida (CP-05-01)', () => {
  const e = L.validarPublicacion({ titulo: 'Apartamento Chapinero', descripcion: 'd', ciudad: 'Bogotá', direccion: 'Cl 60', tipo: 'APARTAMENTO',
    capacidad: 4, precioPorNoche: 200000, imagenes: ['https://img/1.jpg'] });
  assert.deepEqual(e, {});
});

test('HU-024 CA-05: tarifa negativa y servicio repetido (CP-05-05)', () => {
  const e = L.validarTarifas({ tarifaLimpieza: -50000, descuentoPct: 10, descuentoMinNoches: 7,
    serviciosAdicionales: [{ nombre: 'Desayuno', costo: 30000 }, { nombre: 'desayuno', costo: 1 }] });
  assert.ok(e.tarifaLimpieza); assert.match(e.serviciosAdicionales, /repetido/);
  assert.deepEqual(L.validarTarifas({ tarifaLimpieza: 50000, descuentoPct: 10, descuentoMinNoches: 7, serviciosAdicionales: [{ nombre: 'Desayuno', costo: 30000 }] }), {});
});

test('imágenes: una URL por línea', () => {
  assert.deepEqual(L.lineas(' https://a/1.jpg \n\nhttps://a/2.jpg'), ['https://a/1.jpg', 'https://a/2.jpg']);
});
