(function () {
  'use strict';
  var L = window.ReservasLogica, A = window.ReservasApi, el = window.el, cfg = window.RESERVAS_CONFIG;
  var idAloj = window.param('alojamiento');
  var capacidad = null, cotizacion = null;
  var $ = function (id) { return document.getElementById(id); };

  if (!idAloj) { window.mostrarMensaje('Falta el alojamiento. Abre esta página desde el detalle del alojamiento.', 'error'); $('form').classList.add('oculto'); return; }
  $('llegada').min = L.hoyISO();

  // Datos del alojamiento (S2: GET /api/accommodations/{id}). Si el formato difiere, el formulario sigue funcionando sin servicios.
  fetch(cfg.API_BASE + '/api/accommodations/' + encodeURIComponent(idAloj)).then(function (r) { return r.ok ? r.json() : null; }).then(function (a) {
    if (!a) return;
    capacidad = a.capacidad || null;
    if (capacidad) $('huespedes').max = capacidad;
    var lista = a.serviciosAdicionales || a.additionalServices || [];
    if (!lista.length) return;
    var fs = $('servicios'); fs.classList.remove('oculto');
    lista.filter(function (s) { return s.activo !== false; }).forEach(function (s) {
      var cb = el('input', { type: 'checkbox', value: s.id, style: 'width:auto' });
      fs.appendChild(el('label', { style: 'display:flex;gap:8px;align-items:center;font-weight:400' }, [cb, s.nombre + ' (' + L.formatearCOP(s.costo) + ')']));
    });
  }).catch(function () { /* opcional */ });

  function limpiarErrores() { ['llegada', 'salida', 'huespedes'].forEach(function (c) { $('e-' + c).textContent = ''; }); }

  $('form').addEventListener('submit', async function (ev) {
    ev.preventDefault(); limpiarErrores(); window.mostrarMensaje('');
    var datos = { llegada: $('llegada').value, salida: $('salida').value, huespedes: $('huespedes').value, capacidad: capacidad };
    var errores = L.validarCotizacion(datos, L.hoyISO());
    Object.keys(errores).forEach(function (c) { $('e-' + c).textContent = errores[c]; });
    if (Object.keys(errores).length) return;
    var ids = [].slice.call(document.querySelectorAll('#servicios input:checked')).map(function (c) { return c.value; });
    $('btn-cotizar').disabled = true;
    try {
      cotizacion = await A.cotizar({ idAlojamiento: idAloj, fechaLlegada: datos.llegada, fechaSalida: datos.salida, huespedes: Number(datos.huespedes), idsServiciosAdicionales: ids });
      mostrar(cotizacion);
    } catch (e) {
      $('cotizacion').classList.add('oculto'); cotizacion = null;
      window.mostrarMensaje(e.mensaje, 'error');
    } finally { $('btn-cotizar').disabled = false; }
  });

  function fila(texto, valor, clase) { return el('tr', clase ? { clase: clase } : {}, [el('td', { texto: texto }), el('td', { texto: valor })]); }

  function mostrar(c) {
    var d = c.desglose, t = $('desglose'); t.textContent = '';
    t.appendChild(fila(L.formatearCOP(d.precioPorNoche) + ' × ' + d.noches + ' noche(s)', L.formatearCOP(d.subtotal)));
    if (Number(d.descuento) > 0) t.appendChild(fila('Descuento por estancia larga', '− ' + L.formatearCOP(d.descuento)));
    t.appendChild(fila('Tarifa de limpieza', L.formatearCOP(d.tarifaLimpieza)));
    t.appendChild(fila('Tarifa de servicio', L.formatearCOP(d.tarifaServicio)));
    d.serviciosAdicionales.forEach(function (s) { t.appendChild(fila(s.nombre, L.formatearCOP(s.costo))); });
    t.appendChild(fila('Total', L.formatearCOP(d.total), 'total'));
    $('vigencia').textContent = 'Esta cotización es válida por 30 minutos.';
    $('politica').textContent = c.politicaAplicada ? 'Política de cancelación — ' + L.textoPolitica(c.politicaAplicada) + ' Queda fijada al reservar.' : '';
    $('cotizacion').classList.remove('oculto');
    $('cotizacion').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  $('btn-reservar').addEventListener('click', async function () {
    if (!cotizacion) return;
    var btn = $('btn-reservar'); btn.disabled = true;
    var nombre = 'reserva-' + cotizacion.tokenCotizacion.slice(-24);
    try {
      var r = await A.reservar(cotizacion.tokenCotizacion, A.llaveEstable(nombre));
      A.olvidarLlave(nombre);
      window.location.href = 'pago.html?reserva=' + encodeURIComponent(r.id);
    } catch (e) {
      if (e.status !== 0) A.olvidarLlave(nombre); // en un corte de red se conserva la llave para reintentar sin duplicar
      window.mostrarMensaje(e.mensaje, 'error');
      if (['FECHAS_NO_DISPONIBLES', 'COTIZACION_VENCIDA', 'COTIZACION_INVALIDA', 'COTIZACION_DESACTUALIZADA'].indexOf(e.codigo) >= 0) { $('cotizacion').classList.add('oculto'); cotizacion = null; }
      btn.disabled = false;
    }
  });
})();
