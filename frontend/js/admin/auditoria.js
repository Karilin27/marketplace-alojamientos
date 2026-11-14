/* HU-019 — Consultar auditoría en solo lectura, con filtros y paginación. */
(function () {
  'use strict';
  if (!window.exigirAdmin()) return;
  var A = window.AdminApi, el = window.el, $ = function (id) { return document.getElementById(id); };
  var pagina = 0, TAM = 50;
  async function cargar() {
    if ($('desde').value && $('hasta').value && $('hasta').value < $('desde').value) { window.mostrarMensaje('La fecha «hasta» no puede ser anterior a «desde».', 'error'); return; }
    try {
      var r = await A.auditoria({ entidad: $('entidad').value, accion: $('accion').value.trim(), desde: $('desde').value, hasta: $('hasta').value, page: pagina, size: TAM });
      var t = $('tabla'); t.textContent = '';
      t.appendChild(el('tr', {}, ['Fecha', 'Usuario', 'Entidad', 'Identificador', 'Acción', 'Resultado'].map(function (h) { return el('th', { texto: h }); })));
      if (!r.items.length) t.appendChild(el('tr', {}, [el('td', { colspan: '6', clase: 'suave', texto: 'Ningún evento cumple los filtros.' })]));
      r.items.forEach(function (x) {
        t.appendChild(el('tr', {}, [el('td', { texto: window.fechaHora(x.fecha) }), el('td', { texto: x.usuario || '—' }), el('td', { texto: x.entidad }),
          el('td', { clase: 'suave', texto: x.idEntidad || '' }), el('td', { texto: x.accion }), el('td', { texto: x.resultado })]));
      });
      $('resumen').textContent = r.total + ' eventos';
      var paginas = Math.max(1, Math.ceil(r.total / TAM));
      $('paginador').classList.toggle('oculto', paginas <= 1);
      $('pagina').textContent = 'Página ' + (pagina + 1) + ' de ' + paginas;
      $('anterior').disabled = pagina === 0; $('siguiente').disabled = pagina + 1 >= paginas;
    } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); }
  }
  $('filtros').addEventListener('submit', function (ev) { ev.preventDefault(); pagina = 0; window.mostrarMensaje(''); cargar(); });
  $('anterior').addEventListener('click', function () { pagina = Math.max(0, pagina - 1); cargar(); });
  $('siguiente').addEventListener('click', function () { pagina += 1; cargar(); });
  cargar();
})();
