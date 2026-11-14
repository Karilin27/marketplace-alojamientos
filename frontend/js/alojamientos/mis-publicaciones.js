/* HU-007 — El anfitrión consulta sus publicaciones (CA-09) y las activa o pausa (CA-01, CA-03). */
(function () {
  'use strict';
  var A = window.AlojamientosApi, el = window.el;
  var $ = function (id) { return document.getElementById(id); };

  if (!A.haySesion()) { window.location.href = '../login.html?volver=' + encodeURIComponent(location.pathname); return; }

  function boton(texto, clase, accion) {
    var b = el('button', { type: 'button', clase: clase || '', texto: texto });
    b.addEventListener('click', async function () {
      b.disabled = true; window.mostrarMensaje('');
      try { await accion(); await cargar(); } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); } finally { b.disabled = false; }
    });
    return b;
  }

  function tarjeta(a) {
    var acciones = [el('a', { clase: 'boton secundario', href: 'publicar.html?id=' + encodeURIComponent(a.id), texto: 'Editar y tarifas',
      style: 'background:transparent;color:var(--primario);border:1px solid var(--primario)' })];
    if (a.estado === 'BORRADOR' || a.estado === 'PAUSADA') {
      acciones.push(boton('Activar', '', async function () {
        var r = await A.cambiarEstado(a.id, 'ACTIVA');
        window.mostrarMensaje('«' + a.titulo + '» ahora está activa y aparece en las búsquedas.', 'exito');
        return r;
      }));
    }
    if (a.estado === 'ACTIVA') {
      acciones.push(boton('Pausar', 'secundario', async function () {
        await A.cambiarEstado(a.id, 'PAUSADA');
        window.mostrarMensaje('«' + a.titulo + '» quedó pausada: ya no aparece en búsquedas. Las reservas confirmadas siguen vigentes.', 'exito');
      }));
    }
    if (a.estado === 'ACTIVA') acciones.push(el('a', { href: 'detalle.html?id=' + encodeURIComponent(a.id), texto: 'Ver como huésped' }));
    var nota = a.estado === 'BLOQUEADA' ? 'Bloqueada por un administrador: solo él puede cambiar su estado.'
      : a.estado === 'PENDIENTE_REVISION' ? 'En revisión: espera la aprobación de un administrador.' : '';
    return el('article', { clase: 'card' }, [
      el('div', { style: 'display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap' }, [
        el('h2', { style: 'margin:0', texto: a.titulo }),
        el('span', { clase: 'estado ' + a.estado, texto: window.ESTADOS[a.estado] || a.estado }),
      ]),
      el('p', { clase: 'suave', texto: (window.TIPOS[a.tipo] || a.tipo) + ' · ' + a.ciudad + ' · ' + window.formatearCOP(a.precioPorNoche) + ' / noche · hasta ' + a.capacidad + ' huéspedes' }),
      nota ? el('p', { clase: 'aviso info', texto: nota }) : null,
      el('div', { clase: 'acciones', style: 'align-items:center' }, acciones),
    ]);
  }

  async function cargar() {
    try {
      var lista = await A.mias();
      var c = $('lista'); c.textContent = '';
      if (!lista.length) { c.appendChild(el('p', { clase: 'suave', texto: 'Aún no tienes publicaciones.' })); return; }
      lista.forEach(function (a) { c.appendChild(tarjeta(a)); });
    } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); }
  }
  cargar();
})();
