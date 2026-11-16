(function () {
  'use strict';
  var L = window.ReservasLogica, A = window.ReservasApi, el = window.el;
  var $ = function (i) { return document.getElementById(i); };
  var pagina = 0, objetivo = null;
  var nombres = {};   // id del alojamiento -> promesa con { titulo, ciudad }

  // Muestra a qué alojamiento corresponde la reserva; si la publicación ya no está activa, se indica así.
  function nombreAlojamiento(id) {
    if (!nombres[id]) {
      nombres[id] = A.alojamientoPublico(id).then(function (a) { return { titulo: a.titulo, ciudad: a.ciudad, activo: true }; },
        function () { return { titulo: 'Alojamiento no disponible actualmente', ciudad: '', activo: false }; });
    }
    return nombres[id];
  }
  function encabezado(r) {
    var cont = el('div', { clase: 'alojamiento-reserva' }, [el('span', { clase: 'suave', texto: 'Cargando alojamiento…' })]);
    nombreAlojamiento(r.idAlojamiento).then(function (a) {
      cont.textContent = '';
      cont.appendChild(a.activo
        ? el('a', { href: '../alojamientos/detalle.html?id=' + encodeURIComponent(r.idAlojamiento), texto: a.titulo })
        : el('span', { clase: 'suave', texto: a.titulo }));
      if (a.ciudad) cont.appendChild(el('span', { clase: 'suave', texto: ' · ' + a.ciudad }));
    });
    return cont;
  }

  function item(r) {
    var e = L.etiquetaEstado(r.estado);
    var acciones = el('div', { clase: 'acciones' });
    if (r.estado === 'PENDIENTE_PAGO') acciones.appendChild(el('a', { clase: 'boton', href: 'pago.html?reserva=' + r.id, texto: 'Pagar' }));
    if (L.puedeCancelar(r, L.hoyISO())) {
      var b = el('button', { clase: 'secundario', type: 'button', texto: 'Cancelar reserva' });
      b.addEventListener('click', function () { abrirCancelar(r); });
      acciones.appendChild(b);
    }
    if (r.estado === 'COMPLETADA') {                         // S5 HU-017: reseñar la estancia completada
      acciones.appendChild(el('a', { clase: 'boton', href: '../resenas/resenar.html?reserva=' + encodeURIComponent(r.id), texto: 'Reseñar estancia' }));
    }
    var li = el('li', {}, [
      encabezado(r),
      el('div', {}, [el('strong', { texto: r.fechaLlegada + ' → ' + r.fechaSalida }), ' ', el('span', { clase: 'etiqueta ' + e.clase, texto: e.texto })]),
      el('div', { clase: 'suave', texto: r.huespedes + ' huésped(es) · Total ' + L.formatearCOP(r.desglose.total) }),
      el('div', { clase: 'suave', texto: L.textoPolitica(r.politicaAplicada) }),
      r.montoReembolso != null ? el('div', { texto: 'Reembolso: ' + L.formatearCOP(r.montoReembolso) }) : null,
      r.revisionManual ? el('div', { clase: 'aviso info', texto: 'Esta reserva está en revisión manual por el equipo.' }) : null,
      acciones,
    ]);
    return li;
  }

  async function cargar(reiniciar) {
    if (reiniciar) { pagina = 0; $('lista').textContent = ''; }
    try {
      var p = await A.misReservas(pagina);
      if (!p.contenido.length && pagina === 0) { $('lista').appendChild(el('li', { clase: 'suave', texto: 'Aún no tienes reservas.' })); }
      p.contenido.forEach(function (r) { $('lista').appendChild(item(r)); });
      $('mas').classList.toggle('oculto', (pagina + 1) * p.size >= p.total);
    } catch (e) { $('lista').textContent = ''; window.mostrarMensaje(e.mensaje, 'error'); }
  }

  function abrirCancelar(r) {
    objetivo = r;
    var dev = L.devolucionEstimada(r.desglose.total, r.politicaAplicada, r.fechaLlegada, L.hoyISO());
    $('dlg-texto').textContent = 'Si cancelas hoy, la devolución estimada es ' + L.formatearCOP(dev) + ' según la política ' + r.politicaAplicada.nombre + '. El valor definitivo lo calcula el sistema.';
    $('motivo').value = '';
    $('dlg-cancelar').showModal();
  }
  $('dlg-no').addEventListener('click', function () { $('dlg-cancelar').close(); });
  $('dlg-ok').addEventListener('click', async function () {
    var btn = $('dlg-ok'); btn.disabled = true;
    var nombre = 'cancel-' + objetivo.id;
    try {
      await A.cancelar(objetivo.id, $('motivo').value.trim(), A.llaveEstable(nombre));
      A.olvidarLlave(nombre); $('dlg-cancelar').close();
      window.mostrarMensaje('Reserva cancelada. Revisa tus notificaciones para ver el reembolso.', 'exito');
      cargar(true);
    } catch (e) {
      if (e.status !== 0) A.olvidarLlave(nombre);
      $('dlg-cancelar').close(); window.mostrarMensaje(e.mensaje, 'error');
    } finally { btn.disabled = false; }
  });
  $('mas').addEventListener('click', function () { pagina += 1; cargar(false); });
  cargar(true);
})();
