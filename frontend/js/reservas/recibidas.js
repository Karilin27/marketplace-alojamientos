(function () {
  'use strict';
  var L = window.ReservasLogica, A = window.ReservasApi, el = window.el;
  var $ = function (i) { return document.getElementById(i); };
  var pagina = 0;
  async function cargar(reiniciar) {
    if (reiniciar) { pagina = 0; $('lista').textContent = ''; }
    try {
      var p = await A.recibidas(pagina);
      if (reiniciar) $('lista').textContent = '';
      if (!p.contenido.length && pagina === 0) $('lista').appendChild(el('li', { clase: 'suave', texto: 'Todavía no has recibido reservas.' }));
      p.contenido.forEach(function (r) {
        var e = L.etiquetaEstado(r.estado);
        $('lista').appendChild(el('li', {}, [
          el('div', {}, [el('strong', { texto: r.fechaLlegada + ' → ' + r.fechaSalida }), ' ', el('span', { clase: 'etiqueta ' + e.clase, texto: e.texto })]),
          el('div', { clase: 'suave', texto: r.huespedes + ' huésped(es) · Total ' + L.formatearCOP(r.desglose.total) }),
          r.revisionManual ? el('div', { clase: 'aviso info', texto: 'En revisión manual.' }) : null,
        ]));
      });
      $('mas').classList.toggle('oculto', (pagina + 1) * p.size >= p.total);
    } catch (e) { $('lista').textContent = ''; window.mostrarMensaje(e.status === 403 ? 'Esta sección es solo para anfitriones.' : e.mensaje, 'error'); }
  }
  $('mas').addEventListener('click', function () { pagina += 1; cargar(false); });
  cargar(true);
})();
