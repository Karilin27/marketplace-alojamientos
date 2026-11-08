(function () {
  'use strict';
  var A = window.ReservasApi, el = window.el;
  var $ = function (i) { return document.getElementById(i); };
  var pagina = 0;
  var ICONOS = { PAGO_APROBADO: '✔', PAGO_RECHAZADO: '✖', RESERVA_CANCELADA: '⊘', REEMBOLSO: '↩', RESERVA_EXPIRADA: '⏱', RESERVA_RECIBIDA: '★' };

  function item(n) {
    var li = el('li', { clase: n.leida ? '' : 'no-leida' }, [
      el('div', { texto: (ICONOS[n.tipo] || '•') + ' ' + n.mensaje }),
      el('div', { clase: 'suave', texto: new Date(n.fecha).toLocaleString('es-CO') }),
    ]);
    if (!n.leida) {
      var b = el('button', { clase: 'secundario', type: 'button', texto: 'Marcar como leída' });
      b.addEventListener('click', async function () {
        b.disabled = true;
        try { await A.marcarLeida(n.id); li.classList.remove('no-leida'); li.removeChild(b); }
        catch (e) { window.mostrarMensaje(e.mensaje, 'error'); b.disabled = false; }
      });
      li.appendChild(b);
    }
    return li;
  }

  async function cargar(reiniciar) {
    if (reiniciar) { pagina = 0; }
    try {
      var p = await A.notificaciones($('solo').checked, pagina);
      if (reiniciar) $('lista').textContent = '';
      if (!p.contenido.length && pagina === 0) $('lista').appendChild(el('li', { clase: 'suave', texto: 'No tienes notificaciones.' }));
      p.contenido.forEach(function (n) { $('lista').appendChild(item(n)); });
      $('mas').classList.toggle('oculto', (pagina + 1) * p.size >= p.total);
    } catch (e) { $('lista').textContent = ''; window.mostrarMensaje(e.mensaje, 'error'); }
  }
  $('solo').addEventListener('change', function () { cargar(true); });
  $('mas').addEventListener('click', function () { pagina += 1; cargar(false); });
  cargar(true);
})();
