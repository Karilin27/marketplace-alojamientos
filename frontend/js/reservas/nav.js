/* Barra de navegación común del módulo de reservas + contador de notificaciones no leídas. */
(function () {
  'use strict';
  var L = window.ReservasLogica, A = window.ReservasApi;
  var barra = document.getElementById('barra');
  var actual = barra.getAttribute('data-actual');
  var enlaces = [
    ['mis-reservas', 'Mis reservas'], ['recibidas', 'Reservas recibidas'], ['notificaciones', 'Notificaciones'],
  ];
  var marca = document.createElement('span'); marca.className = 'marca'; marca.textContent = 'Marketplace · Reservas';
  barra.appendChild(marca);
  enlaces.forEach(function (e) {
    var a = document.createElement('a'); a.href = e[0] + '.html'; a.textContent = e[1];
    if (e[0] === actual) a.setAttribute('aria-current', 'page');
    if (e[0] === 'notificaciones') { var s = document.createElement('span'); s.id = 'insignia'; s.className = 'insignia oculto'; a.appendChild(s); }
    barra.appendChild(a);
  });
  if (actual !== 'notificaciones') {
    A.notificaciones(true, 0).then(function (p) {
      var s = document.getElementById('insignia');
      if (s && p.total > 0) { s.textContent = p.total; s.classList.remove('oculto'); s.setAttribute('aria-label', p.total + ' sin leer'); }
    }).catch(function () { /* la insignia es opcional */ });
  }

  window.mostrarMensaje = function (texto, tipo) {
    var c = document.getElementById('mensaje'); c.textContent = '';
    if (!texto) return;
    var d = document.createElement('div'); d.className = 'aviso ' + (tipo || 'info'); d.textContent = texto; c.appendChild(d);
  };
  window.param = function (nombre) { return new URLSearchParams(window.location.search).get(nombre); };
})();

/* Utilidad mínima para construir DOM sin innerHTML (evita inyección de HTML desde datos del servidor). */
window.el = function (tag, atributos, hijos) {
  var n = document.createElement(tag);
  Object.keys(atributos || {}).forEach(function (k) {
    if (k === 'texto') n.textContent = atributos[k]; else if (k === 'clase') n.className = atributos[k]; else n.setAttribute(k, atributos[k]);
  });
  [].concat(hijos || []).forEach(function (h) { if (h) n.appendChild(typeof h === 'string' ? document.createTextNode(h) : h); });
  return n;
};
