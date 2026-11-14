/* HU-017 — El huésped califica una estancia COMPLETADA (se llega desde «Mis reservas»). */
(function () {
  'use strict';
  var A = window.AlojamientosApi, L = window.ResenasLogica, el = window.el;
  var $ = function (id) { return document.getElementById(id); };
  var idReserva = window.param('reserva');

  if (!A.haySesion()) { window.location.href = '../login.html?volver=' + encodeURIComponent(location.pathname + location.search); return; }
  if (!idReserva) { window.mostrarMensaje('Falta la reserva. Abre esta página desde «Mis reservas».', 'error'); $('form').classList.add('oculto'); return; }

  for (var i = 1; i <= 5; i++) {
    $('estrellas').appendChild(el('label', {}, [el('input', { type: 'radio', name: 'calificacion', value: String(i) }), i + ' ' + '★'.repeat(i)]));
  }
  $('comentario').addEventListener('input', function () { $('contador').textContent = $('comentario').value.length; });

  $('form').addEventListener('submit', async function (ev) {
    ev.preventDefault();
    $('e-calificacion').textContent = ''; $('e-comentario').textContent = ''; window.mostrarMensaje('');
    var marcada = document.querySelector('input[name=calificacion]:checked');
    var calificacion = marcada ? Number(marcada.value) : null;
    var errores = L.validarResena(calificacion, $('comentario').value);              // CA-05
    Object.keys(errores).forEach(function (c) { $('e-' + c).textContent = errores[c]; });
    if (Object.keys(errores).length) return;
    $('btn-enviar').disabled = true;
    try {
      var r = await A.resenar({ idReserva: idReserva, calificacion: calificacion, comentario: $('comentario').value.trim() });
      $('form').classList.add('oculto'); $('hecho').classList.remove('oculto');
      $('nuevo-promedio').textContent = 'La calificación del alojamiento ahora es ★ ' + r.calificacionPromedio + ' de 5 (' + r.total + (r.total === 1 ? ' reseña).' : ' reseñas).');
      $('ver-alojamiento').href = '../alojamientos/detalle.html?id=' + encodeURIComponent(r.idAlojamiento);
    } catch (e) {
      window.mostrarMensaje(e.mensaje, 'error');                                     // CA-02, CA-03
    } finally { $('btn-enviar').disabled = false; }
  });
})();
