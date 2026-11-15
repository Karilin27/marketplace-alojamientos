/* HU-020 (indicadores) y HU-021 (exportar reportes a PDF o Excel). */
(function () {
  'use strict';
  if (!window.exigirAdmin()) return;
  var A = window.AdminApi, el = window.el, $ = function (id) { return document.getElementById(id); };
  var ESTADOS = { PENDIENTE_PAGO: 'Pendientes de pago', CONFIRMADA: 'Confirmadas', RECHAZADA: 'Rechazadas', CANCELADA: 'Canceladas', COMPLETADA: 'Completadas' };
  function kpi(valor, etiqueta) { return el('div', { clase: 'kpi' }, [el('span', { clase: 'valor', texto: valor }), el('span', { clase: 'etiqueta', texto: etiqueta })]); }
  function fila(a, b) { return el('tr', {}, [el('td', { texto: a }), el('td', { texto: String(b) })]); }

  async function cargar() {
    try {
      var d = await A.dashboard();
      var k = $('kpis'); k.textContent = '';
      k.appendChild(kpi(d.usuarios, 'Usuarios registrados'));
      Object.keys(d.usuariosPorRol).forEach(function (r) { k.appendChild(kpi(d.usuariosPorRol[r], ({ ADMINISTRADOR: 'Administradores', ANFITRION: 'Anfitriones', HUESPED: 'Huéspedes' })[r] || r)); });
      k.appendChild(kpi(d.publicacionesActivas, 'Publicaciones activas'));
      k.appendChild(kpi(window.cop(d.ingresosEstimados), 'Ingresos estimados (confirmadas y completadas)'));
      var t = $('por-estado'); t.textContent = '';
      Object.keys(ESTADOS).forEach(function (e) { t.appendChild(fila(ESTADOS[e], d.reservasPorEstado[e] || 0)); });
      var c = $('ciudades'); c.textContent = '';
      if (!d.ciudadesMayorDemanda.length) c.appendChild(fila('Sin reservas todavía', ''));
      d.ciudadesMayorDemanda.forEach(function (x) { c.appendChild(fila(x.ciudad, x.reservas + ' reservas')); });
      $('calculado').textContent = 'Calculado el ' + window.fechaHora(d.calculadoEn);
    } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); }
  }

  $('form-reporte').addEventListener('submit', async function (ev) {
    ev.preventDefault(); window.mostrarMensaje(''); $('btn-exportar').disabled = true;
    try {
      var r = await A.exportar($('tipo').value, $('formato').value);
      var nombre = (r.disposicion.match(/filename="?([^";]+)"?/) || [])[1] || ('reporte.' + ($('formato').value === 'PDF' ? 'pdf' : 'xlsx'));
      var url = URL.createObjectURL(r.blob);
      var a = el('a', { href: url, download: nombre }); document.body.appendChild(a); a.click(); a.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 2000);
      window.mostrarMensaje('Reporte generado: ' + nombre + '. La exportación quedó registrada en la auditoría.', 'exito');
    } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); } finally { $('btn-exportar').disabled = false; }
  });
  $('btn-actualizar').addEventListener('click', cargar);
  cargar();
})();
