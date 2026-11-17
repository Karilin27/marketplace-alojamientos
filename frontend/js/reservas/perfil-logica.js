/* Lógica pura (sin DOM) de la pantalla de perfil (HU-004). Replica las reglas del servidor para avisar antes de enviar.
   Se prueba con Node: node --test frontend/test */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.PerfilLogica = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var TELEFONO = /^\+?[0-9]{7,15}$/;
  var DOCUMENTO = /^[A-Za-z0-9-]{5,20}$/;
  var PAIS = /^[A-Za-zÀ-ÿñÑ][A-Za-zÀ-ÿñÑ .'-]{1,59}$/;

  var ROLES = { HUESPED: 'Huésped', ANFITRION: 'Anfitrión', ADMINISTRADOR: 'Administrador' };
  var ESTADOS = {
    ACTIVO: { texto: 'Cuenta activa', clase: 'ok' },
    DESACTIVADO: { texto: 'Cuenta desactivada', clase: 'muted' },
    RESTRINGIDO: { texto: 'Cuenta restringida', clase: 'bad' },
  };

  function recortar(v) { return v == null ? '' : String(v).trim(); }

  /** Deja los datos como los espera el servidor: sin espacios sobrantes y con los opcionales vacíos como null. */
  function normalizar(f) {
    f = f || {};
    function opcional(v) { var r = recortar(v); return r === '' ? null : r; }
    return { nombre: recortar(f.nombre), telefono: opcional(f.telefono), documento: opcional(f.documento), pais: opcional(f.pais), fotoUrl: opcional(f.fotoUrl) };
  }

  function urlValida(texto) {
    if (texto.length > 500) return false;
    var m = /^(https?):\/\/([^\s/?#]+)([/?#]\S*)?$/i.exec(texto);
    return !!m;
  }

  /** Devuelve { campo: mensaje } con lo que hay que corregir; objeto vacío si todo está bien (CA-03). */
  function validarPerfil(f) {
    var d = normalizar(f), e = {};
    if (!d.nombre) e.nombre = 'El nombre es obligatorio.';
    else if (d.nombre.length < 2 || d.nombre.length > 100) e.nombre = 'El nombre debe tener entre 2 y 100 caracteres.';
    if (d.telefono !== null && !TELEFONO.test(d.telefono.replace(/[ -]/g, ''))) e.telefono = 'Usa entre 7 y 15 dígitos, con + opcional al inicio.';
    if (d.documento !== null && !DOCUMENTO.test(d.documento)) e.documento = 'Usa entre 5 y 20 letras, números o guiones.';
    if (d.pais !== null && !PAIS.test(d.pais)) e.pais = 'Usa solo letras, entre 2 y 60 caracteres.';
    if (d.fotoUrl !== null && !urlValida(d.fotoUrl)) e.fotoUrl = 'Debe ser una dirección http o https de hasta 500 caracteres.';
    return e;
  }

  /** El servidor responde detalles como "campo: mensaje"; se reparten por campo para mostrarlos junto a cada casilla. */
  function erroresPorCampo(detalles) {
    var salida = {};
    (detalles || []).forEach(function (t) {
      var i = String(t).indexOf(':');
      if (i > 0) salida[String(t).slice(0, i).trim()] = String(t).slice(i + 1).trim();
    });
    return salida;
  }

  function etiquetaRol(rol) { return ROLES[rol] || rol || '—'; }
  function etiquetaEstado(estado) { return ESTADOS[estado] || { texto: estado || '—', clase: 'muted' }; }

  /** Compara lo escrito con lo guardado, para habilitar "Guardar" solo cuando hay cambios. */
  function hayCambios(actual, guardado) {
    var a = normalizar(actual), g = normalizar(guardado);
    return ['nombre', 'telefono', 'documento', 'pais', 'fotoUrl'].some(function (k) { return a[k] !== g[k]; });
  }

  function mensajeError(datos, status) {
    if (status === 0) return 'No se pudo conectar con el servidor. Verifica que el backend esté en ejecución.';
    if (datos && datos.mensaje) return datos.mensaje;
    if (status === 403) return 'No tienes permiso para realizar esta operación.';
    return 'Ocurrió un error inesperado. Intenta de nuevo.';
  }

  /** Texto del resultado de desactivar la cuenta (CA-05). */
  function textoDesactivada(r) {
    var n = r && r.publicacionesPausadas ? Number(r.publicacionesPausadas) : 0;
    var base = 'Tu cuenta fue desactivada y tu sesión se cerró.';
    if (n > 0) base += ' Se pausaron ' + n + (n === 1 ? ' publicación.' : ' publicaciones.');
    return base + ' Para reactivarla, inicia sesión y elige "Reactivar mi cuenta".';
  }

  return {
    normalizar: normalizar, validarPerfil: validarPerfil, erroresPorCampo: erroresPorCampo, etiquetaRol: etiquetaRol,
    etiquetaEstado: etiquetaEstado, hayCambios: hayCambios, mensajeError: mensajeError, textoDesactivada: textoDesactivada,
  };
});
