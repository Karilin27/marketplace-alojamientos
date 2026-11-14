/* HU-018 — Moderar publicaciones (aprobar, pausar, bloquear con motivo, desbloquear) y revisión manual (CU-17 A3). */
(function () {
  'use strict';
  if (!window.exigirAdmin()) return;
  var A = window.AdminApi, el = window.el, $ = function (id) { return document.getElementById(id); };
  var NOMBRES = { BORRADOR: 'Borrador', PENDIENTE_REVISION: 'En revisión', ACTIVA: 'Activa', PAUSADA: 'Pausada', BLOQUEADA: 'Bloqueada' };
  var PERMITIDAS = {
    PENDIENTE_REVISION: ['APROBAR', 'PAUSAR', 'BLOQUEAR'], BORRADOR: ['APROBAR', 'BLOQUEAR'], ACTIVA: ['PAUSAR', 'BLOQUEAR'],
    PAUSADA: ['APROBAR', 'BLOQUEAR'], BLOQUEADA: ['DESBLOQUEAR'],
  };
  var TEXTO = { APROBAR: 'Aprobar', PAUSAR: 'Pausar', BLOQUEAR: 'Bloquear', DESBLOQUEAR: 'Desbloquear' };

  function acciones(p) {
    var c = el('div', { clase: 'acciones-fila' });
    var motivo = el('input', { placeholder: 'Motivo (obligatorio para bloquear)', 'aria-label': 'Motivo para ' + p.titulo });
    c.appendChild(motivo);
    (PERMITIDAS[p.estado] || []).forEach(function (accion) {
      var b = el('button', { type: 'button', clase: accion === 'BLOQUEAR' ? 'peligro' : (accion === 'APROBAR' ? '' : 'secundario'), texto: TEXTO[accion] });
      b.addEventListener('click', async function () {
        if (accion === 'BLOQUEAR' && !motivo.value.trim()) { window.mostrarMensaje('Indica el motivo para bloquear «' + p.titulo + '».', 'error'); motivo.focus(); return; }
        b.disabled = true;
        try {
          var r = await A.moderar(p.id, accion, motivo.value.trim());
          window.mostrarMensaje('«' + p.titulo + '» pasó a ' + NOMBRES[r.estadoNuevo] + '. Se notificó al anfitrión' +
            (r.reservasEnRevision ? ' y ' + r.reservasEnRevision + ' reserva(s) futura(s) quedaron en revisión manual.' : '.'), 'exito');
          cargar(); cargarRevisiones();
        } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); b.disabled = false; }
      });
      c.appendChild(b);
    });
    return c;
  }

  async function cargar() {
    try {
      var lista = await A.publicaciones($('estado').value);
      var t = $('tabla'); t.textContent = '';
      t.appendChild(el('tr', {}, ['Publicación', 'Anfitrión', 'Estado', 'Reservas futuras', 'Acciones'].map(function (h) { return el('th', { texto: h }); })));
      if (!lista.length) t.appendChild(el('tr', {}, [el('td', { colspan: '5', clase: 'suave', texto: 'No hay publicaciones en ese estado.' })]));
      lista.forEach(function (p) {
        t.appendChild(el('tr', {}, [
          el('td', {}, [el('a', { href: '../alojamientos/detalle.html?id=' + p.id, texto: p.titulo }), el('div', { clase: 'suave', texto: p.tipo + ' · ' + p.ciudad })]),
          el('td', {}, [p.anfitrion, el('div', { clase: 'suave', texto: p.correoAnfitrion })]),
          el('td', {}, [el('span', { clase: 'estado ' + p.estado, texto: NOMBRES[p.estado] || p.estado })]),
          el('td', { texto: String(p.reservasFuturas) }), el('td', {}, [acciones(p)]),
        ]));
      });
    } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); }
  }

  async function cargarRevisiones() {
    try {
      var lista = await A.revisiones();
      var t = $('revisiones'); t.textContent = '';
      t.appendChild(el('tr', {}, ['Alojamiento', 'Huésped', 'Fechas', 'Total', 'Decisión'].map(function (h) { return el('th', { texto: h }); })));
      if (!lista.length) t.appendChild(el('tr', {}, [el('td', { colspan: '5', clase: 'suave', texto: 'No hay reservas en revisión manual.' })]));
      lista.forEach(function (r) {
        var decision = el('input', { placeholder: 'Decisión tomada', 'aria-label': 'Decisión para la reserva de ' + r.huesped });
        var b = el('button', { type: 'button', texto: 'Registrar' });
        b.addEventListener('click', async function () {
          if (!decision.value.trim()) { window.mostrarMensaje('Escribe la decisión tomada.', 'error'); return; }
          b.disabled = true;
          try { await A.cerrarRevision(r.id, decision.value.trim()); window.mostrarMensaje('Decisión registrada en la auditoría.', 'exito'); cargarRevisiones(); }
          catch (e) { window.mostrarMensaje(e.mensaje, 'error'); b.disabled = false; }
        });
        t.appendChild(el('tr', {}, [el('td', { texto: r.alojamiento }), el('td', { texto: r.huesped }),
          el('td', { texto: r.fechaLlegada + ' → ' + r.fechaSalida }), el('td', { texto: window.cop(r.total) }),
          el('td', {}, [el('div', { clase: 'acciones-fila' }, [decision, b])])]));
      });
    } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); }
  }
  $('filtros').addEventListener('submit', function (ev) { ev.preventDefault(); window.mostrarMensaje(''); cargar(); });
  cargar(); cargarRevisiones();
})();
