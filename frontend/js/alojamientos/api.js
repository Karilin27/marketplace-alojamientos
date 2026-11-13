/* S2 — Cliente HTTP y utilidades comunes de las pantallas de alojamientos (HU-005, 006, 007, 009, 010, 023, 024).
   Usa el mismo backend (window.APP_CONFIG.API_BASE, js/config.js) y el mismo token que el login ("token"). */
(function () {
  'use strict';
  var TOKEN_KEY = 'token';
  var LOGIN_URL = '../login.html';

  function base() { return (window.APP_CONFIG && window.APP_CONFIG.API_BASE) || 'http://localhost:8080'; }
  function token() { try { return localStorage.getItem(TOKEN_KEY); } catch (e) { return null; } }
  function usuario() { try { return JSON.parse(localStorage.getItem('usuario') || 'null'); } catch (e) { return null; } }

  function mensajeError(datos, status) {
    if (status === 0) return 'No se pudo conectar con el servidor. Verifica que el backend esté en ejecución.';
    if (datos && datos.mensaje) {
      return datos.mensaje + (Array.isArray(datos.detalles) && datos.detalles.length ? ' ' + datos.detalles.join(' · ') : '');
    }
    if (status === 403) return 'Tu rol no permite realizar esta operación.';
    if (status === 404) return 'No se encontró la publicación.';
    return 'Ocurrió un error inesperado. Intenta de nuevo.';
  }

  async function llamar(metodo, ruta, cuerpo, conToken) {
    var h = { Accept: 'application/json' };
    var t = token();
    if (conToken !== false && t) h.Authorization = 'Bearer ' + t;
    if (cuerpo !== undefined) h['Content-Type'] = 'application/json';
    var resp;
    try {
      resp = await fetch(base() + ruta, { method: metodo, headers: h, body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) });
    } catch (e) {
      throw { status: 0, mensaje: mensajeError(null, 0) };
    }
    var datos = null;
    try { datos = await resp.json(); } catch (e) { /* sin cuerpo */ }
    if (resp.status === 401 && conToken !== false) {
      try { localStorage.removeItem(TOKEN_KEY); } catch (e) { /* ignorar */ }
      window.location.href = LOGIN_URL + '?volver=' + encodeURIComponent(window.location.pathname + window.location.search);
      throw { status: 401, mensaje: 'Tu sesión venció. Inicia sesión de nuevo.' };
    }
    if (!resp.ok) throw { status: resp.status, codigo: datos && datos.codigo, mensaje: mensajeError(datos, resp.status), detalles: (datos && datos.detalles) || [] };
    return datos;
  }

  window.AlojamientosApi = {
    usuario: usuario,
    haySesion: function () { return !!token(); },
    // HU-009 y HU-010 (públicos)
    buscar: function (filtros) {
      var q = Object.keys(filtros).filter(function (k) { return filtros[k] !== '' && filtros[k] != null; })
        .map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(filtros[k]); });
      return llamar('GET', '/api/accommodations' + (q.length ? '?' + q.join('&') : ''), undefined, false);
    },
    detalle: function (id) { return llamar('GET', '/api/accommodations/' + encodeURIComponent(id), undefined, false); },
    politicas: function () { return llamar('GET', '/api/cancellation-policies', undefined, false); },
    // HU-005, HU-006, HU-007, HU-024 (anfitrión)
    crear: function (cuerpo) { return llamar('POST', '/api/accommodations', cuerpo); },
    editar: function (id, cuerpo) { return llamar('PUT', '/api/accommodations/' + encodeURIComponent(id), cuerpo); },
    mias: function () { return llamar('GET', '/api/accommodations/mine'); },
    mia: function (id) { return llamar('GET', '/api/accommodations/mine/' + encodeURIComponent(id)); },
    cambiarEstado: function (id, estado) { return llamar('PATCH', '/api/accommodations/' + encodeURIComponent(id) + '/status', { estado: estado }); },
    tarifas: function (id, cuerpo) { return llamar('PUT', '/api/accommodations/' + encodeURIComponent(id) + '/pricing', cuerpo); },
  };

  // ---------------------------------------------------------------- utilidades de pantalla
  window.el = window.el || function (tag, atributos, hijos) {
    var n = document.createElement(tag);
    Object.keys(atributos || {}).forEach(function (k) {
      if (k === 'texto') n.textContent = atributos[k]; else if (k === 'clase') n.className = atributos[k]; else n.setAttribute(k, atributos[k]);
    });
    [].concat(hijos || []).forEach(function (h) { if (h) n.appendChild(typeof h === 'string' ? document.createTextNode(h) : h); });
    return n;
  };
  window.param = window.param || function (nombre) { return new URLSearchParams(window.location.search).get(nombre); };
  window.mostrarMensaje = function (texto, tipo) {
    var c = document.getElementById('mensaje'); if (!c) return; c.textContent = '';
    if (!texto) return;
    c.appendChild(window.el('div', { clase: 'aviso ' + (tipo || 'info'), texto: texto }));
  };
  window.formatearCOP = function (v) {
    var n = Number(v || 0);
    return '$' + n.toLocaleString('es-CO', { maximumFractionDigits: 0 });
  };
  window.ESTADOS = { BORRADOR: 'Borrador', PENDIENTE_REVISION: 'En revisión', ACTIVA: 'Activa', PAUSADA: 'Pausada', BLOQUEADA: 'Bloqueada' };
  window.TIPOS = { APARTAMENTO: 'Apartamento', CASA: 'Casa', HABITACION: 'Habitación', CABANA: 'Cabaña', OTRO: 'Otro' };

  // Barra de navegación del módulo
  document.addEventListener('DOMContentLoaded', function () {
    var barra = document.getElementById('barra'); if (!barra) return;
    var actual = barra.getAttribute('data-actual');
    var u = usuario();
    var enlaces = [['buscar', 'Buscar alojamientos']];
    if (u && u.rol === 'ANFITRION') enlaces.push(['mis-publicaciones', 'Mis publicaciones'], ['publicar', 'Publicar']);
    if (token()) enlaces.push(['../reservas/mis-reservas', 'Mis reservas']);
    else enlaces.push(['../login', 'Iniciar sesión']);
    barra.appendChild(window.el('span', { clase: 'marca', texto: 'Marketplace · Alojamientos' }));
    enlaces.forEach(function (e) {
      var a = window.el('a', { href: e[0] + '.html', texto: e[1] });
      if (e[0] === actual) a.setAttribute('aria-current', 'page');
      barra.appendChild(a);
    });
  });
})();
