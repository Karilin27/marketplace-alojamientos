(function () {
  'use strict';
  var L = window.ReservasLogica, A = window.ReservasApi, el = window.el;
  var $ = function (i) { return document.getElementById(i); };
  var pagina = 0;
  var titulos = null;   // promesa: id del alojamiento -> título, con las publicaciones del anfitrión

  function titulosAlojamientos() {
    if (!titulos) {
      titulos = A.misAlojamientos().then(function (lista) {
        var m = {}; lista.forEach(function (a) { m[a.id] = a.titulo + ' · ' + a.ciudad; }); return m;
      }, function () { return {}; });
    }
    return titulos;
  }
  async function cargar(reiniciar) {
    if (reiniciar) { pagina = 0; $('lista').textContent = ''; }
    try {
      var p = await A.recibidas(pagina);
      if (reiniciar) $('lista').textContent = '';
      if (!p.contenido.length && pagina === 0) $('lista').appendChild(el('li', { clase: 'suave', texto: 'Todavía no has recibido reservas.' }));
      var m = await titulosAlojamientos();
      p.contenido.forEach(function (r) {
        var e = L.etiquetaEstado(r.estado);
        $('lista').appendChild(el('li', {}, [
          el('div', {}, [el('a', { href: '../alojamientos/publicar.html?id=' + encodeURIComponent(r.idAlojamiento), texto: m[r.idAlojamiento] || 'Alojamiento' })]),
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
