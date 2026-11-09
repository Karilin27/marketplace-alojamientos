(function () {
  'use strict';
  var L = window.ReservasLogica, A = window.ReservasApi, el = window.el;
  var id = window.param('reserva');
  var $ = function (i) { return document.getElementById(i); };
  var temporizador = null;
  if (!id) { window.mostrarMensaje('Falta la reserva a pagar.', 'error'); return; }

  function resumen(r) {
    var c = $('resumen'); c.textContent = '';
    var e = L.etiquetaEstado(r.estado);
    c.appendChild(el('p', {}, ['Estado: ', el('span', { clase: 'etiqueta ' + e.clase, texto: e.texto })]));
    c.appendChild(el('p', { texto: 'Estancia: ' + r.fechaLlegada + ' → ' + r.fechaSalida + ' · ' + r.huespedes + ' huésped(es)' }));
    c.appendChild(el('p', { texto: 'Total a pagar: ' + L.formatearCOP(r.desglose.total), style: 'font-size:1.25rem;font-weight:700' }));
    c.appendChild(el('p', { clase: 'suave', texto: L.textoPolitica(r.politicaAplicada) }));
  }

  function cuentaRegresiva(venceEn) {
    var caja = el('p', { clase: 'suave' }, ['Tiempo para pagar: ', el('span', { clase: 'cuenta', id: 'cuenta', role: 'timer' })]);
    $('resumen').appendChild(caja);
    function tick() {
      var s = L.segundosRestantes(venceEn);
      $('cuenta').textContent = L.formatearCuenta(s);
      if (s <= 0) {
        clearInterval(temporizador); $('form').classList.add('oculto');
        window.mostrarMensaje(L.mensajeError({ codigo: 'RETENCION_VENCIDA' }), 'error');
      }
    }
    tick(); temporizador = setInterval(tick, 1000);
  }

  async function cargar(conservarMensaje) {
    try {
      var r = await A.reserva(id);
      resumen(r);
      if (r.estado === 'PENDIENTE_PAGO') { $('form').classList.remove('oculto'); cuentaRegresiva(r.venceEn); }
      else if (conservarMensaje) { /* se deja visible el motivo del rechazo o del vencimiento */ }
      else if (r.estado === 'CONFIRMADA') window.mostrarMensaje('Esta reserva ya está pagada y confirmada.', 'exito');
      else window.mostrarMensaje('Esta reserva está ' + L.etiquetaEstado(r.estado).texto.toLowerCase() + ' y ya no admite pago.', 'info');
    } catch (e) { $('resumen').textContent = ''; window.mostrarMensaje(e.mensaje, 'error'); }
  }

  $('form').addEventListener('submit', async function (ev) {
    ev.preventDefault();
    var btn = $('btn-pagar'); btn.disabled = true; window.mostrarMensaje('Procesando el pago…', 'info');
    var nombre = 'pago-' + id;
    try {
      await A.pagar(id, $('metodo').value, A.llaveEstable(nombre));
      A.olvidarLlave(nombre); clearInterval(temporizador);
      $('form').classList.add('oculto');
      window.mostrarMensaje('¡Pago aprobado! Tu reserva quedó confirmada.', 'exito');
      cargar(true);
    } catch (e) {
      if (e.status !== 0) A.olvidarLlave(nombre);
      window.mostrarMensaje(e.mensaje, 'error');
      if (['PAGO_RECHAZADO', 'RETENCION_VENCIDA', 'RESERVA_NO_PAGABLE'].indexOf(e.codigo) >= 0) { clearInterval(temporizador); $('form').classList.add('oculto'); cargar(true); }
      else btn.disabled = false;
    }
  });
  cargar();
})();
