/* S5 — Validaciones de reseñas del lado del cliente (las mismas del backend). Se prueban con node --test. */
(function (raiz) {
  'use strict';
  /** HU-017 CA-05: calificación de 1 a 5 y comentario no vacío (máximo 1000). Devuelve { campo: mensaje }. */
  function validarResena(calificacion, comentario) {
    var e = {};
    var c = Number(calificacion);
    if (!(c >= 1 && c <= 5) || c % 1 !== 0) e.calificacion = 'Elige una calificación de 1 a 5 estrellas.';
    var t = String(comentario || '').trim();
    if (!t) e.comentario = 'Escribe un comentario sobre tu estancia.';
    else if (t.length > 1000) e.comentario = 'El comentario puede tener máximo 1000 caracteres.';
    return e;
  }
  /** HU-025 CA-01: ocultar exige motivo. */
  function validarOcultar(motivo) {
    var t = String(motivo || '').trim();
    if (!t) return 'Indica el motivo para ocultar la reseña.';
    if (t.length > 500) return 'El motivo puede tener máximo 500 caracteres.';
    return '';
  }
  var api = { validarResena: validarResena, validarOcultar: validarOcultar };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else raiz.ResenasLogica = api;
})(typeof window !== 'undefined' ? window : this);
