/* HU-010 — Consultar el detalle de un alojamiento (público). Desde aquí se cotiza (CU-10, pantallas de Kevin). */
(function () {
  'use strict';
  var A = window.AlojamientosApi, el = window.el;
  var $ = function (id) { return document.getElementById(id); };
  var id = window.param('id');

  function fila(t, v) { return el('tr', {}, [el('td', { texto: t }), el('td', { texto: v })]); }

  async function cargar() {
    if (!id) { window.mostrarMensaje('Falta el alojamiento. Ábrelo desde la búsqueda.', 'error'); return; }
    try {
      var a = await A.detalle(id);                                                   // CA-01
      document.title = a.titulo + ' · Marketplace de Alojamientos';
      $('titulo').textContent = a.titulo;
      $('subtitulo').textContent = (window.TIPOS[a.tipo] || a.tipo) + ' · ' + a.ciudad;
      $('descripcion').textContent = a.descripcion;
      $('direccion').textContent = a.direccion;
      $('capacidad').textContent = 'Hasta ' + a.capacidad + ' huéspedes';
      $('reglas').textContent = a.reglas || 'El anfitrión no indicó reglas adicionales.';
      (a.imagenes || []).forEach(function (u, i) { $('galeria').appendChild(el('img', { src: u, alt: 'Foto ' + (i + 1) + ' de ' + a.titulo })); });
      (a.servicios || []).forEach(function (s) { $('servicios').appendChild(el('li', { texto: s })); });
      if (!(a.servicios || []).length) $('servicios').appendChild(el('li', { texto: 'Sin servicios registrados' }));

      var p = $('precios');
      p.appendChild(fila('Precio por noche', window.formatearCOP(a.precioPorNoche)));
      if (Number(a.tarifaLimpieza) > 0) p.appendChild(fila('Tarifa de limpieza (una vez)', window.formatearCOP(a.tarifaLimpieza)));
      if (Number(a.descuentoPct) > 0) p.appendChild(fila('Descuento por estancia', Number(a.descuentoPct) + ' % desde ' + a.descuentoMinNoches + ' noches'));
      (a.serviciosAdicionales || []).forEach(function (s) { p.appendChild(fila(s.nombre + ' (opcional)', window.formatearCOP(s.costo))); });

      var pol = a.politicaCancelacion;                                               // HU-023 CA-05
      $('politica').textContent = pol
        ? 'Política de cancelación ' + pol.nombre + ': devolución del ' + pol.porcentajeDevolucion + ' % si cancelas con al menos ' + pol.diasAntelacion + ' días de antelación.'
        : 'Sin política de cancelación registrada.';

      $('calificacion').textContent = a.totalResenas > 0                             // CA-02
        ? '★ ' + a.calificacionPromedio + ' de 5 · ' + a.totalResenas + (a.totalResenas === 1 ? ' reseña' : ' reseñas')
        : 'Este alojamiento aún no tiene reseñas.';

      var q = 'alojamiento=' + encodeURIComponent(a.id);                             // CA-04 → CU-10
      $('cotizar').href = '../reservas/reservar.html?' + q;
      $('calendario').href = '../reservas/disponibilidad.html?' + q;
      $('detalle').classList.remove('oculto');
    } catch (e) {
      window.mostrarMensaje(e.status === 404 ? 'No es posible mostrar esta publicación: no existe o no está activa.' : e.mensaje, 'error');  // CA-03
    }
  }
  cargar();
})();
