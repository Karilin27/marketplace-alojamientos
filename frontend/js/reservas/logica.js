/* Lógica pura (sin DOM) del frontend de reservas. Se prueba con Node: node --test frontend/test */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.ReservasLogica = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var ETIQUETAS = {
    PENDIENTE_PAGO: { texto: 'Pendiente de pago', clase: 'warn' },
    CONFIRMADA: { texto: 'Confirmada', clase: 'ok' },
    RECHAZADA: { texto: 'Rechazada', clase: 'bad' },
    CANCELADA: { texto: 'Cancelada', clase: 'muted' },
    COMPLETADA: { texto: 'Completada', clase: 'info' },
  };

  function formatearCOP(valor) {
    var n = Number(valor);
    if (!isFinite(n)) return '—';
    return '$' + n.toLocaleString('es-CO', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  }

  function aFecha(iso) {
    var p = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || '');
    if (!p) return null;
    var d = new Date(Date.UTC(+p[1], +p[2] - 1, +p[3]));
    return d.getUTCMonth() === +p[2] - 1 ? d : null; // descarta 2026-02-31
  }

  function hoyISO(ahora) {
    var d = ahora || new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function nochesEntre(llegada, salida) {
    var a = aFecha(llegada), b = aFecha(salida);
    if (!a || !b) return 0;
    return Math.round((b - a) / 86400000);
  }

  /** Validación previa al envío (la regla definitiva la aplica el backend: RN-05, RN-16). */
  function validarCotizacion(f, hoy) {
    var errores = {};
    if (!aFecha(f.llegada)) errores.llegada = 'Selecciona la fecha de llegada.';
    if (!aFecha(f.salida)) errores.salida = 'Selecciona la fecha de salida.';
    if (!errores.llegada && f.llegada < hoy) errores.llegada = 'La llegada no puede estar en el pasado.';
    if (!errores.llegada && !errores.salida && nochesEntre(f.llegada, f.salida) < 1)
      errores.salida = 'La salida debe ser posterior a la llegada.';
    var h = Number(f.huespedes);
    if (!Number.isInteger(h) || h < 1) errores.huespedes = 'Indica al menos 1 huésped.';
    else if (f.capacidad && h > f.capacidad) errores.huespedes = 'Máximo ' + f.capacidad + ' huéspedes.';
    return errores;
  }

  /** Validación de periodos de disponibilidad del anfitrión (HU-008 CA-03). */
  function validarPeriodos(periodos, hoy) {
    var errores = [];
    var validos = [];
    periodos.forEach(function (p, i) {
      if (!aFecha(p.fechaInicio) || !aFecha(p.fechaFin)) return errores.push('Periodo ' + (i + 1) + ': faltan fechas.');
      if (nochesEntre(p.fechaInicio, p.fechaFin) < 1) return errores.push('Periodo ' + (i + 1) + ': el fin debe ser posterior al inicio.');
      if (p.fechaInicio < hoy) return errores.push('Periodo ' + (i + 1) + ': no puede estar en el pasado.');
      validos.push(p);
    });
    var orden = validos.slice().sort(function (a, b) { return a.fechaInicio < b.fechaInicio ? -1 : 1; });
    for (var i = 1; i < orden.length; i++) {
      if (orden[i].fechaInicio < orden[i - 1].fechaFin) errores.push('Los periodos del ' + orden[i - 1].fechaInicio + ' y ' + orden[i].fechaInicio + ' se superponen.');
    }
    return errores;
  }

  function etiquetaEstado(estado) {
    return ETIQUETAS[estado] || { texto: estado || '—', clase: 'muted' };
  }

  function segundosRestantes(venceEnISO, ahoraMs) {
    var t = Date.parse(venceEnISO);
    if (isNaN(t)) return 0;
    return Math.max(0, Math.floor((t - (ahoraMs === undefined ? Date.now() : ahoraMs)) / 1000));
  }

  function formatearCuenta(seg) {
    var m = Math.floor(seg / 60), s = seg % 60;
    return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
  }

  function textoPolitica(p) {
    if (!p) return '';
    var dev = p.porcentajeDevolucion + '% de devolución';
    return p.nombre + ': ' + dev + ' si cancelas con al menos ' + p.diasAntelacion + ' días de antelación; menos que eso, sin devolución.';
  }

  /** Previsualiza la devolución con la misma regla del backend (el valor oficial lo calcula el servidor). */
  function devolucionEstimada(total, politica, llegada, hoy) {
    var dias = nochesEntre(hoy, llegada);
    if (dias < 0) return null;
    if (dias < politica.diasAntelacion) return 0;
    return Math.round(Number(total) * politica.porcentajeDevolucion) / 100;
  }

  function puedeCancelar(reserva, hoy) {
    return reserva.estado === 'CONFIRMADA' && reserva.fechaLlegada >= hoy;
  }

  function nuevaLlave() {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
    return 'k-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
  }

  var MENSAJES = {
    FECHAS_NO_DISPONIBLES: 'Esas fechas ya no están disponibles. Elige otras.',
    COTIZACION_VENCIDA: 'La cotización venció (30 min). Vuelve a cotizar.',
    COTIZACION_DESACTUALIZADA: 'Los precios cambiaron. Vuelve a cotizar.',
    CAPACIDAD_EXCEDIDA: 'Superaste la capacidad del alojamiento.',
    RETENCION_VENCIDA: 'Se acabó el tiempo para pagar. La reserva se liberó.',
    PAGO_RECHAZADO: 'El pago fue rechazado. La reserva no se realizó.',
    RESERVA_NO_CANCELABLE: 'Esta reserva ya no se puede cancelar.',
    CANCELACION_NO_PERMITIDA: 'No se puede cancelar después de la fecha de llegada.',
    PERIODO_CON_RESERVAS: 'El periodo se cruza con reservas existentes.',
    RESERVA_PROPIA: 'No puedes reservar tu propio alojamiento.',
    NO_AUTENTICADO: 'Tu sesión expiró. Inicia sesión de nuevo.',
  };

  function mensajeError(cuerpo, status) {
    if (cuerpo && cuerpo.codigo && MENSAJES[cuerpo.codigo]) return MENSAJES[cuerpo.codigo];
    if (cuerpo && cuerpo.mensaje) return cuerpo.mensaje + (cuerpo.detalles && cuerpo.detalles.length ? ' (' + cuerpo.detalles.join('; ') + ')' : '');
    if (status === 0) return 'No hay conexión con el servidor.';
    return 'Ocurrió un error inesperado (' + status + ').';
  }

  return {
    formatearCOP: formatearCOP, aFecha: aFecha, hoyISO: hoyISO, nochesEntre: nochesEntre,
    validarCotizacion: validarCotizacion, validarPeriodos: validarPeriodos, etiquetaEstado: etiquetaEstado,
    segundosRestantes: segundosRestantes, formatearCuenta: formatearCuenta, textoPolitica: textoPolitica,
    devolucionEstimada: devolucionEstimada, puedeCancelar: puedeCancelar, nuevaLlave: nuevaLlave, mensajeError: mensajeError,
  };
});
