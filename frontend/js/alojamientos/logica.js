/* S2 — Validaciones del lado del cliente (las mismas reglas que valida el backend).
   Funciones puras: se prueban con `node --test frontend/test/alojamientos.test.js`. */
(function (raiz) {
  'use strict';

  var SERVICIOS = ['WiFi', 'Cocina', 'Parqueadero', 'Aire acondicionado', 'Televisión', 'Lavadora', 'Piscina', 'Admite mascotas'];

  function vacio(v) { return v == null || String(v).trim() === ''; }

  /** HU-009 CA-04: filtros de búsqueda. Devuelve un texto de error o '' si son válidos. */
  function validarFiltros(f, hoyISO) {
    if (vacio(f.llegada) !== vacio(f.salida)) return 'Indica la fecha de llegada y la de salida, o ninguna.';
    if (!vacio(f.llegada)) {
      if (f.salida <= f.llegada) return 'La fecha de salida debe ser posterior a la de llegada.';
      if (hoyISO && f.llegada < hoyISO) return 'La fecha de llegada no puede ser una fecha pasada.';
    }
    if (!vacio(f.huespedes) && Number(f.huespedes) < 1) return 'El número de huéspedes debe ser al menos 1.';
    if (!vacio(f.precioMin) && Number(f.precioMin) < 0) return 'El precio mínimo no puede ser negativo.';
    if (!vacio(f.precioMin) && !vacio(f.precioMax) && Number(f.precioMin) > Number(f.precioMax)) {
      return 'El precio mínimo no puede ser mayor que el máximo.';
    }
    return '';
  }

  /** HU-005 CA-01, CA-02: datos obligatorios de la publicación. Devuelve { campo: mensaje }. */
  function validarPublicacion(p) {
    var e = {};
    ['titulo', 'descripcion', 'ciudad', 'direccion', 'tipo'].forEach(function (c) { if (vacio(p[c])) e[c] = 'Este campo es obligatorio.'; });
    if (vacio(p.capacidad) || !(Number(p.capacidad) > 0) || Number(p.capacidad) % 1 !== 0) e.capacidad = 'La capacidad debe ser un número entero mayor que cero.';
    if (vacio(p.precioPorNoche) || !(Number(p.precioPorNoche) > 0)) e.precioPorNoche = 'El precio por noche debe ser mayor que cero.';
    (p.imagenes || []).forEach(function (u) { if (!/^https?:\/\//i.test(u)) e.imagenes = 'Cada imagen debe ser una URL que empiece por http:// o https://.'; });
    if ((p.imagenes || []).length > 10) e.imagenes = 'Máximo 10 imágenes.';
    return e;
  }

  /** HU-024 CA-05: tarifas no negativas, descuento entre 0 y 100 y servicios adicionales sin repetir. */
  function validarTarifas(t) {
    var e = {};
    if (Number(t.tarifaLimpieza) < 0) e.tarifaLimpieza = 'La tarifa de limpieza no puede ser negativa.';
    var pct = Number(t.descuentoPct || 0);
    if (pct < 0 || pct > 100) e.descuentoPct = 'El descuento debe estar entre 0 y 100 %.';
    if (Number(t.descuentoMinNoches) < 0) e.descuentoMinNoches = 'No puede ser negativo.';
    if (pct > 0 && !(Number(t.descuentoMinNoches) >= 1)) e.descuentoMinNoches = 'Indica desde cuántas noches aplica el descuento.';
    var vistos = {};
    (t.serviciosAdicionales || []).forEach(function (s) {
      var n = String(s.nombre || '').trim().toLowerCase();
      if (!n) e.serviciosAdicionales = 'Cada servicio adicional necesita un nombre.';
      else if (vistos[n]) e.serviciosAdicionales = 'El servicio «' + s.nombre + '» está repetido.';
      vistos[n] = true;
      if (vacio(s.costo) || Number(s.costo) < 0) e.serviciosAdicionales = 'El costo de cada servicio adicional no puede ser negativo.';
    });
    return e;
  }

  /** Convierte el texto de imágenes (una URL por línea) en lista. */
  function lineas(texto) {
    return String(texto || '').split(/\r?\n/).map(function (s) { return s.trim(); }).filter(Boolean);
  }

  var api = { SERVICIOS: SERVICIOS, validarFiltros: validarFiltros, validarPublicacion: validarPublicacion, validarTarifas: validarTarifas, lineas: lineas };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else raiz.AlojamientosLogica = api;
})(typeof window !== 'undefined' ? window : this);
