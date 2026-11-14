// Pruebas de la lógica de reseñas (S5): node --test frontend/test/resenas.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/resenas/logica.js');

test('HU-017 CA-05: calificación fuera de rango y comentario vacío (CP-16-05)', () => {
  const e = L.validarResena(6, '   ');
  assert.ok(e.calificacion); assert.ok(e.comentario);
  assert.ok(L.validarResena(null, 'ok').calificacion);
  assert.ok(L.validarResena(2.5, 'ok').calificacion);
});

test('HU-017 CA-01: reseña válida (CP-16-01)', () => {
  assert.deepEqual(L.validarResena(5, 'Excelente ubicación'), {});
});

test('HU-025 CA-01: ocultar exige motivo (CP-22-03)', () => {
  assert.match(L.validarOcultar(' '), /motivo/);
  assert.equal(L.validarOcultar('Lenguaje ofensivo'), '');
});
