/* HU-025 — El administrador oculta (con motivo) o restablece reseñas. Las ocultas se conservan. */
(function () {
  'use strict';
  var A = window.AlojamientosApi, L = window.ResenasLogica, el = window.el;
  var $ = function (id) { return document.getElementById(id); };
  var u = A.usuario();

  if (!A.haySesion()) { window.location.href = '../login.html?volver=' + encodeURIComponent(location.pathname); return; }
  if (!u || u.rol !== 'ADMINISTRADOR') {                                            // CA-04
    window.mostrarMensaje('Solo un administrador puede moderar reseñas.', 'error'); $('filtro').classList.add('oculto'); return;
  }
  var idInicial = window.param('alojamiento'); if (idInicial) $('alojamiento').value = idInicial;

  function item(r) {
    var acciones = el('div', { clase: 'acciones', style: 'align-items:end' });
    if (r.visible) {
      var motivo = el('input', { placeholder: 'Motivo (obligatorio)', 'aria-label': 'Motivo para ocultar', style: 'max-width:320px' });
      var err = el('p', { clase: 'error-campo' });
      var b = el('button', { type: 'button', clase: 'peligro', texto: 'Ocultar' });
      b.addEventListener('click', async function () {
        err.textContent = L.validarOcultar(motivo.value);                           // CA-01
        if (err.textContent) return;
        b.disabled = true;
        try {
          var res = await A.visibilidad(r.id, false, motivo.value.trim());
          window.mostrarMensaje('Reseña oculta. Nuevo promedio de «' + r.alojamiento + '»: ' + (res.calificacionPromedio == null ? 'sin reseñas visibles' : '★ ' + res.calificacionPromedio), 'exito');
          cargar();
        } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); b.disabled = false; }
      });
      acciones.appendChild(el('div', {}, [motivo, err])); acciones.appendChild(b);
    } else {
      var b2 = el('button', { type: 'button', clase: 'secundario', texto: 'Restablecer' });
      b2.addEventListener('click', async function () {                              // CA-03
        b2.disabled = true;
        try {
          var res = await A.visibilidad(r.id, true, null);
          window.mostrarMensaje('Reseña restablecida. Nuevo promedio de «' + r.alojamiento + '»: ★ ' + res.calificacionPromedio, 'exito');
          cargar();
        } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); b2.disabled = false; }
      });
      acciones.appendChild(b2);
    }
    return el('li', {}, [
      el('div', {}, [el('strong', { texto: r.alojamiento }), ' ', el('span', { clase: 'estado ' + (r.visible ? 'ACTIVA' : 'BLOQUEADA'), texto: r.visible ? 'Visible' : 'Oculta' })]),
      el('div', { clase: 'suave', texto: '★'.repeat(r.calificacion) + '☆'.repeat(5 - r.calificacion) + ' · ' + r.autor + ' · ' + new Date(r.fecha).toLocaleString('es-CO') }),
      el('p', { style: 'margin:4px 0', texto: r.comentario || '' }),
      !r.visible && r.motivoOculta ? el('p', { clase: 'aviso info', texto: 'Motivo: ' + r.motivoOculta }) : null,
      acciones,
    ]);
  }

  async function cargar() {
    try {
      var lista = await A.resenasAdmin($('alojamiento').value.trim() || null);
      var c = $('lista'); c.textContent = '';
      if (!lista.length) c.appendChild(el('li', { clase: 'suave', texto: 'No hay reseñas para mostrar.' }));
      lista.forEach(function (r) { c.appendChild(item(r)); });
    } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); }
  }
  $('filtro').addEventListener('submit', function (ev) { ev.preventDefault(); window.mostrarMensaje(''); cargar(); });
  cargar();
})();
