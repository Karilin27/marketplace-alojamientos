/* HU-005 (crear), HU-006 (editar), HU-023 (política de cancelación) y HU-024 (tarifas y servicios adicionales). */
(function () {
  'use strict';
  var A = window.AlojamientosApi, L = window.AlojamientosLogica, el = window.el;
  var $ = function (id) { return document.getElementById(id); };
  var id = window.param('id');
  var u = A.usuario();

  if (!A.haySesion()) { window.location.href = '../login.html?volver=' + encodeURIComponent(location.pathname + location.search); return; }
  if (!u || (u.rol !== 'ANFITRION' && u.rol !== 'ADMINISTRADOR')) {                  // HU-005 CA-05
    window.mostrarMensaje('Solo los anfitriones pueden publicar alojamientos.', 'error');
    $('form').classList.add('oculto'); return;
  }

  L.SERVICIOS.forEach(function (s) { $('servicios').appendChild(el('label', {}, [el('input', { type: 'checkbox', value: s }), s])); });

  function filaAdicional(s) {
    var fila = el('div', { clase: 'adicional' }, [
      el('div', {}, [el('label', { texto: 'Nombre' }), el('input', { clase: 'ad-nombre', value: s ? s.nombre : '', placeholder: 'Desayuno' })]),
      el('div', {}, [el('label', { texto: 'Costo (COP)' }), el('input', { clase: 'ad-costo', type: 'number', min: '0', step: '1000', value: s ? s.costo : '' })]),
      el('button', { type: 'button', clase: 'secundario', texto: 'Quitar' }),
    ]);
    fila.querySelector('button').addEventListener('click', function () { fila.remove(); });
    $('adicionales').appendChild(fila);
  }
  $('btn-agregar').addEventListener('click', function () { filaAdicional(null); });

  function limpiar(campos) { campos.forEach(function (c) { var n = $('e-' + c); if (n) n.textContent = ''; }); }
  function marcar(errores) { Object.keys(errores).forEach(function (c) { var n = $('e-' + c); if (n) n.textContent = errores[c]; }); }

  function mostrarTarifas(a) {
    $('form-tarifas').classList.remove('oculto'); $('siguiente').classList.remove('oculto');
    $('enlace-calendario').href = '../reservas/disponibilidad.html?alojamiento=' + encodeURIComponent(a.id);
    $('tarifaLimpieza').value = a.tarifaLimpieza || 0;
    $('descuentoPct').value = a.descuentoPct || 0;
    $('descuentoMinNoches').value = a.descuentoMinNoches || 0;
    $('adicionales').textContent = '';
    (a.serviciosAdicionales || []).filter(function (s) { return s.activo; }).forEach(filaAdicional);
  }

  function llenar(a) {
    ['titulo', 'descripcion', 'tipo', 'ciudad', 'direccion', 'capacidad', 'precioPorNoche', 'reglas'].forEach(function (c) { $(c).value = a[c] == null ? '' : a[c]; });
    $('imagenes').value = (a.imagenes || []).join('\n');
    $('politica').value = a.politicaCancelacion ? a.politicaCancelacion.id : '';
    [].slice.call(document.querySelectorAll('#servicios input')).forEach(function (c) {
      c.checked = (a.servicios || []).some(function (s) { return s.toLowerCase() === c.value.toLowerCase(); });
    });
    $('encabezado').textContent = 'Editar publicación';
    $('btn-guardar').textContent = 'Guardar cambios';
    if (a.estado === 'ACTIVA') {
      window.mostrarMensaje('Esta publicación está activa. Si cambias el título, la descripción, la ubicación o el tipo, quedará en revisión hasta que un administrador la apruebe.', 'info');
    }
    mostrarTarifas(a);
  }

  async function iniciar() {
    try {
      var politicas = await A.politicas();                                            // HU-023 CA-01
      politicas.forEach(function (p) {
        $('politica').appendChild(el('option', { value: p.id, texto: p.nombre + ' — ' + p.porcentajeDevolucion + ' % de devolución con ' + p.diasAntelacion + ' días de antelación' }));
      });
      if (id) llenar(await A.mia(id));
    } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); }
  }

  $('form').addEventListener('submit', async function (ev) {
    ev.preventDefault();
    var campos = ['titulo', 'descripcion', 'tipo', 'ciudad', 'direccion', 'capacidad', 'precioPorNoche', 'imagenes'];
    limpiar(campos); window.mostrarMensaje('');
    var cuerpo = {
      titulo: $('titulo').value, descripcion: $('descripcion').value, tipo: $('tipo').value, ciudad: $('ciudad').value,
      direccion: $('direccion').value, capacidad: $('capacidad').value === '' ? null : Number($('capacidad').value),
      precioPorNoche: $('precioPorNoche').value === '' ? null : Number($('precioPorNoche').value),
      servicios: [].slice.call(document.querySelectorAll('#servicios input:checked')).map(function (c) { return c.value; }),
      reglas: $('reglas').value, imagenes: L.lineas($('imagenes').value), idPoliticaCancelacion: $('politica').value || null,
    };
    var errores = L.validarPublicacion(cuerpo);                                       // HU-005 CA-02
    marcar(errores);
    if (Object.keys(errores).length) return;
    $('btn-guardar').disabled = true;
    try {
      var a = id ? await A.editar(id, cuerpo) : await A.crear(cuerpo);
      if (!id) { id = a.id; history.replaceState(null, '', '?id=' + encodeURIComponent(id)); }
      llenar(a);
      var texto = a.estado === 'PENDIENTE_REVISION'
        ? 'Cambios guardados. La publicación quedó en revisión hasta que un administrador la apruebe.'
        : 'Publicación guardada (' + (window.ESTADOS[a.estado] || a.estado) + '). Ahora configura las tarifas.';
      window.mostrarMensaje(texto, 'exito');
    } catch (e) {
      window.mostrarMensaje(e.mensaje, 'error');
    } finally { $('btn-guardar').disabled = false; }
  });

  $('form-tarifas').addEventListener('submit', async function (ev) {
    ev.preventDefault();
    limpiar(['tarifaLimpieza', 'descuentoPct', 'descuentoMinNoches', 'serviciosAdicionales']); window.mostrarMensaje('');
    var cuerpo = {
      tarifaLimpieza: Number($('tarifaLimpieza').value || 0), descuentoPct: Number($('descuentoPct').value || 0),
      descuentoMinNoches: Number($('descuentoMinNoches').value || 0),
      serviciosAdicionales: [].slice.call(document.querySelectorAll('#adicionales .adicional')).map(function (f) {
        return { nombre: f.querySelector('.ad-nombre').value.trim(), costo: f.querySelector('.ad-costo').value === '' ? null : Number(f.querySelector('.ad-costo').value), activo: true };
      }),
    };
    var errores = L.validarTarifas(cuerpo);                                           // HU-024 CA-05
    marcar(errores);
    if (Object.keys(errores).length) return;
    $('btn-tarifas').disabled = true;
    try {
      mostrarTarifas(await A.tarifas(id, cuerpo));
      window.mostrarMensaje('Tarifas guardadas. Se usarán en las próximas cotizaciones.', 'exito');
    } catch (e) {
      window.mostrarMensaje(e.mensaje, 'error');
    } finally { $('btn-tarifas').disabled = false; }
  });

  iniciar();
})();
