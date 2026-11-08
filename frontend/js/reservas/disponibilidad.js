(function () {
  'use strict';
  var L = window.ReservasLogica, A = window.ReservasApi, el = window.el;
  var $ = function (i) { return document.getElementById(i); };
  var id = window.param('alojamiento');
  if (!id) { window.mostrarMensaje('Falta el alojamiento.', 'error'); return; }

  function fila(p) {
    var ini = el('input', { type: 'date', 'aria-label': 'Inicio' }); ini.value = p.fechaInicio || '';
    var fin = el('input', { type: 'date', 'aria-label': 'Fin (no incluida)' }); fin.value = p.fechaFin || '';
    var tipo = el('select', { 'aria-label': 'Tipo' }, [el('option', { value: 'NO_DISPONIBLE', texto: 'No disponible' }), el('option', { value: 'DISPONIBLE', texto: 'Disponible' })]);
    tipo.value = p.tipo || 'NO_DISPONIBLE';
    var quitar = el('button', { type: 'button', clase: 'secundario', texto: 'Quitar' });
    var f = el('div', { clase: 'fila', style: 'align-items:end;margin-bottom:8px' }, [ini, fin, tipo, quitar]);
    quitar.addEventListener('click', function () { f.remove(); });
    f.leer = function () { return { fechaInicio: ini.value, fechaFin: fin.value, tipo: tipo.value }; };
    return f;
  }

  async function cargar() {
    try {
      var d = await A.disponibilidad(id);
      $('filas').textContent = ''; $('reservados').textContent = '';
      var res = d.periodos.filter(function (p) { return p.tipo === 'RESERVADO'; });
      d.periodos.filter(function (p) { return p.tipo !== 'RESERVADO'; }).forEach(function (p) { $('filas').appendChild(fila(p)); });
      if (!res.length) $('reservados').appendChild(el('li', { clase: 'suave', texto: 'Sin reservas activas en el próximo año.' }));
      res.forEach(function (p) { $('reservados').appendChild(el('li', { texto: p.fechaInicio + ' → ' + p.fechaFin })); });
    } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); }
  }

  $('agregar').addEventListener('click', function () { $('filas').appendChild(fila({})); });
  $('guardar').addEventListener('click', async function () {
    var periodos = [].slice.call($('filas').children).map(function (f) { return f.leer(); });
    var errores = L.validarPeriodos(periodos, L.hoyISO());
    if (errores.length) { window.mostrarMensaje(errores.join(' '), 'error'); return; }
    $('guardar').disabled = true;
    try { await A.guardarDisponibilidad(id, periodos); window.mostrarMensaje('Disponibilidad guardada.', 'exito'); cargar(); }
    catch (e) { window.mostrarMensaje(e.mensaje + (e.detalles && e.detalles.length ? ' ' + e.detalles.join(', ') : ''), 'error'); }
    finally { $('guardar').disabled = false; }
  });
  cargar();
})();
