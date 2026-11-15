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

// D-21 — subida de fotos desde el computador
test('D-21: validarArchivoImagen acepta JPG, PNG y WEBP de hasta 5 MB', () => {
  assert.equal(L.validarArchivoImagen({ name: 'a.jpg', type: 'image/jpeg', size: 1000 }), '');
  assert.equal(L.validarArchivoImagen({ name: 'a.png', type: 'image/png', size: L.MAX_BYTES_IMAGEN }), '');
  assert.equal(L.validarArchivoImagen({ name: 'a.webp', type: 'IMAGE/WEBP', size: 10 }), '');
  assert.match(L.validarArchivoImagen({ name: 'a.gif', type: 'image/gif', size: 10 }), /JPG, PNG ni WEBP/);
  assert.match(L.validarArchivoImagen({ name: 'a.pdf', type: '', size: 10 }), /no es JPG/);
  assert.match(L.validarArchivoImagen({ name: 'g.jpg', type: 'image/jpeg', size: L.MAX_BYTES_IMAGEN + 1 }), /5 MB/);
  assert.ok(L.validarArchivoImagen(null));
});

test('D-21: cupo de imágenes y agregar URL sin repetir', () => {
  assert.equal(L.cupoImagenes(''), 10);
  assert.equal(L.cupoImagenes('https://a\nhttps://b\n\n'), 8);
  assert.equal(L.cupoImagenes(Array(12).fill('https://x').join('\n')), 0);
  assert.equal(L.agregarUrl('', 'https://a'), 'https://a');
  assert.equal(L.agregarUrl('https://a\n', 'https://b'), 'https://a\nhttps://b');
  assert.equal(L.agregarUrl('https://a', 'https://a'), 'https://a');
});
