const test = require('node:test');
const assert = require('node:assert/strict');
const P = require('../js/reservas/perfil-logica.js');

const base = { nombre: 'Ana Pérez', telefono: '+573001234567', documento: 'CC-123456', pais: 'Colombia', fotoUrl: 'https://ejemplo.com/foto.png' };

test('normalizar recorta espacios y deja los opcionales vacíos como null', () => {
  assert.deepEqual(P.normalizar({ nombre: '  Ana  ', telefono: ' ', documento: '', pais: undefined, fotoUrl: null }),
    { nombre: 'Ana', telefono: null, documento: null, pais: null, fotoUrl: null });
});

test('validarPerfil acepta datos correctos y opcionales vacíos (CA-02)', () => {
  assert.deepEqual(P.validarPerfil(base), {});
  assert.deepEqual(P.validarPerfil({ nombre: 'Ana' }), {});
  assert.deepEqual(P.validarPerfil({ nombre: 'Ana', telefono: '300 123-4567' }), {});
});

test('validarPerfil: el nombre es obligatorio y tiene entre 2 y 100 caracteres (CA-03)', () => {
  assert.ok(P.validarPerfil({ nombre: '' }).nombre);
  assert.ok(P.validarPerfil({ nombre: '   ' }).nombre);
  assert.ok(P.validarPerfil({ nombre: 'A' }).nombre);
  assert.ok(P.validarPerfil({ nombre: 'a'.repeat(101) }).nombre);
  assert.deepEqual(P.validarPerfil({ nombre: 'a'.repeat(100) }), {});
});

test('validarPerfil: teléfono, documento, país y foto con formato inválido (CA-03)', () => {
  const e = P.validarPerfil({ nombre: 'Ana', telefono: '12ab', documento: 'ab', pais: '123', fotoUrl: 'ftp://x.com/a.png' });
  assert.ok(e.telefono && e.documento && e.pais && e.fotoUrl);
  assert.ok(P.validarPerfil({ nombre: 'Ana', telefono: '123456' }).telefono);
  assert.ok(P.validarPerfil({ nombre: 'Ana', telefono: '1'.repeat(16) }).telefono);
  assert.ok(P.validarPerfil({ nombre: 'Ana', documento: 'a'.repeat(21) }).documento);
  assert.ok(P.validarPerfil({ nombre: 'Ana', fotoUrl: 'https://' + 'a'.repeat(500) + '.com' }).fotoUrl);
  assert.ok(P.validarPerfil({ nombre: 'Ana', fotoUrl: 'javascript:alert(1)' }).fotoUrl);
  assert.deepEqual(P.validarPerfil({ nombre: 'Ana', pais: "Côte d'Ivoire" }), {});
});

test('erroresPorCampo reparte los detalles "campo: mensaje" del servidor', () => {
  assert.deepEqual(P.erroresPorCampo(['nombre: es obligatorio.', 'telefono: use entre 7 y 15 dígitos.']),
    { nombre: 'es obligatorio.', telefono: 'use entre 7 y 15 dígitos.' });
  assert.deepEqual(P.erroresPorCampo(undefined), {});
  assert.deepEqual(P.erroresPorCampo(['sin separador']), {});
});

test('hayCambios solo detecta diferencias reales', () => {
  assert.equal(P.hayCambios(base, base), false);
  assert.equal(P.hayCambios({ ...base, nombre: ' Ana Pérez ' }, base), false);
  assert.equal(P.hayCambios({ ...base, pais: '' }, base), true);
  assert.equal(P.hayCambios({ ...base, telefono: '' }, { ...base, telefono: null }), false);
});

test('etiquetas y mensajes', () => {
  assert.equal(P.etiquetaRol('ANFITRION'), 'Anfitrión');
  assert.equal(P.etiquetaEstado('ACTIVO').clase, 'ok');
  assert.equal(P.etiquetaEstado('RESTRINGIDO').clase, 'bad');
  assert.match(P.mensajeError(null, 0), /No se pudo conectar/);
  assert.equal(P.mensajeError({ mensaje: 'X' }, 422), 'X');
  assert.match(P.textoDesactivada({ publicacionesPausadas: 2 }), /2 publicaciones/);
  assert.match(P.textoDesactivada({ publicacionesPausadas: 1 }), /1 publicación\./);
  assert.doesNotMatch(P.textoDesactivada({ publicacionesPausadas: 0 }), /pausaron/);
});
