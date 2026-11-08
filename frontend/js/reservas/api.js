/* Cliente HTTP de S3/S4/S5. Cada operación crítica genera una Idempotency-Key y la reutiliza si se reintenta. */
(function () {
  'use strict';
  var cfg = window.RESERVAS_CONFIG;
  var L = window.ReservasLogica;

  function token() {
    try { return localStorage.getItem(cfg.TOKEN_KEY); } catch (e) { return null; }
  }

  // Llaves de idempotencia por operación: si el usuario pulsa dos veces o reintenta tras un corte, se reenvía la misma.
  function llaveEstable(nombre) {
    var k = 'idem:' + nombre;
    try {
      var v = sessionStorage.getItem(k);
      if (!v) { v = L.nuevaLlave(); sessionStorage.setItem(k, v); }
      return v;
    } catch (e) { return L.nuevaLlave(); }
  }
  function olvidarLlave(nombre) {
    try { sessionStorage.removeItem('idem:' + nombre); } catch (e) { /* sin almacenamiento */ }
  }

  async function llamar(metodo, ruta, cuerpo, cabeceras) {
    var h = Object.assign({ Accept: 'application/json' }, cabeceras || {});
    var t = token();
    if (t) h.Authorization = 'Bearer ' + t;
    if (cuerpo !== undefined) h['Content-Type'] = 'application/json';
    var resp;
    try {
      resp = await fetch(cfg.API_BASE + ruta, { method: metodo, headers: h, body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) });
    } catch (e) {
      throw { status: 0, mensaje: L.mensajeError(null, 0) };
    }
    var datos = null;
    try { datos = await resp.json(); } catch (e) { /* sin cuerpo */ }
    if (resp.status === 401) {
      try { localStorage.removeItem(cfg.TOKEN_KEY); } catch (e) { /* ignorar */ }
      window.location.href = cfg.LOGIN_URL + '?volver=' + encodeURIComponent(window.location.pathname + window.location.search);
      throw { status: 401, codigo: 'NO_AUTENTICADO', mensaje: L.mensajeError({ codigo: 'NO_AUTENTICADO' }, 401) };
    }
    if (!resp.ok) throw { status: resp.status, codigo: datos && datos.codigo, mensaje: L.mensajeError(datos, resp.status), detalles: datos && datos.detalles };
    return datos;
  }

  window.ReservasApi = {
    llaveEstable: llaveEstable,
    olvidarLlave: olvidarLlave,
    disponibilidad: function (id, desde, hasta) {
      var q = []; if (desde) q.push('desde=' + desde); if (hasta) q.push('hasta=' + hasta);
      return llamar('GET', '/api/availability/' + id + (q.length ? '?' + q.join('&') : ''));
    },
    guardarDisponibilidad: function (id, periodos) { return llamar('PUT', '/api/availability/' + id, { periodos: periodos }); },
    cotizar: function (cuerpo) { return llamar('POST', '/api/quotes', cuerpo); },
    reservar: function (tokenCotizacion, llave) { return llamar('POST', '/api/bookings', { tokenCotizacion: tokenCotizacion }, { 'Idempotency-Key': llave }); },
    misReservas: function (page) { return llamar('GET', '/api/bookings?page=' + (page || 0) + '&size=20'); },
    recibidas: function (page) { return llamar('GET', '/api/bookings/received?page=' + (page || 0) + '&size=20'); },
    reserva: function (id) { return llamar('GET', '/api/bookings/' + id); },
    cancelar: function (id, motivo, llave) { return llamar('POST', '/api/bookings/' + id + '/cancel', { motivo: motivo || null }, { 'Idempotency-Key': llave }); },
    pagar: function (idReserva, metodoPago, llave) { return llamar('POST', '/api/payments', { idReserva: idReserva, metodoPago: metodoPago }, { 'Idempotency-Key': llave }); },
    notificaciones: function (soloNoLeidas, page) { return llamar('GET', '/api/notifications?unread=' + !!soloNoLeidas + '&page=' + (page || 0) + '&size=20'); },
    marcarLeida: function (id) { return llamar('PATCH', '/api/notifications/' + id + '/read'); },
  };
})();
