/* Barra de navegación común del módulo de reservas + contador de notificaciones no leídas. */
(function () {
  'use strict';
  var L = window.ReservasLogica, A = window.ReservasApi;
  var barra = document.getElementById('barra');
  var actual = barra.getAttribute('data-actual');
  // Cada rol ve solo las secciones que el servidor le permite (el anfitrión no reserva; el huésped no recibe reservas).
  var rol = null;
  try { rol = (JSON.parse(localStorage.getItem('usuario') || 'null') || {}).rol; } catch (e) { /* sin almacenamiento */ }
  var enlaces = [['../alojamientos/buscar', 'Buscar alojamientos']];
  if (rol === 'ANFITRION') enlaces.push(['../alojamientos/mis-publicaciones', 'Mis publicaciones'], ['recibidas', 'Reservas recibidas']);
  else if (rol === 'ADMINISTRADOR') enlaces.push(['../admin/dashboard', 'Panel de administración']);
  else enlaces.push(['mis-reservas', 'Mis reservas']);
  enlaces.push(['notificaciones', 'Notificaciones'], ['perfil', 'Mi perfil']);
  // Si alguien abre la sección del otro rol (por un enlace guardado), se le lleva a la suya.
  if (actual === 'mis-reservas' && rol === 'ANFITRION') { window.location.replace('recibidas.html'); return; }
  if (actual === 'recibidas' && rol === 'HUESPED') { window.location.replace('mis-reservas.html'); return; }
  if ((actual === 'mis-reservas' || actual === 'recibidas') && rol === 'ADMINISTRADOR') { window.location.replace('../admin/dashboard.html'); return; }
  var marca = document.createElement('a'); marca.className = 'marca'; marca.href = '../../index.html'; marca.textContent = 'Marketplace · Reservas';
  barra.appendChild(marca);
  enlaces.forEach(function (e) {
    var a = document.createElement('a'); a.href = e[0] + '.html'; a.textContent = e[1];
    if (e[0] === actual) a.setAttribute('aria-current', 'page');
    if (e[0] === 'notificaciones') { var s = document.createElement('span'); s.id = 'insignia'; s.className = 'insignia oculto'; a.appendChild(s); }
    barra.appendChild(a);
  });
  var salir = document.createElement('a'); salir.href = '../../index.html'; salir.textContent = 'Salir';
  salir.addEventListener('click', function () { try { localStorage.removeItem('token'); localStorage.removeItem('usuario'); } catch (e) { /* nada */ } });
  barra.appendChild(salir);
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
