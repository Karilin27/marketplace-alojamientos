/* HU-022 — El administrador consulta usuarios y restringe (con motivo) o reactiva cuentas. */
(function () {
  'use strict';
  if (!window.exigirAdmin()) return;
  var A = window.AdminApi, el = window.el, $ = function (id) { return document.getElementById(id); };
  var yo = (function () { try { return JSON.parse(localStorage.getItem('usuario') || '{}').id; } catch (e) { return null; } })();

  function acciones(u) {
    var c = el('div', { clase: 'acciones-fila' });
    if (u.id === yo || u.rol === 'ADMINISTRADOR') return c;
    if (u.estado === 'RESTRINGIDO') {
      var b = el('button', { type: 'button', clase: 'secundario', texto: 'Reactivar' });
      b.addEventListener('click', async function () {
        b.disabled = true;
        try { await A.estadoUsuario(u.id, 'ACTIVO'); window.mostrarMensaje(u.nombre + ' fue reactivado.', 'exito'); cargar(); }
        catch (e) { window.mostrarMensaje(e.mensaje, 'error'); b.disabled = false; }
      });
      c.appendChild(b);
    } else {
      var motivo = el('input', { placeholder: 'Motivo (obligatorio)', 'aria-label': 'Motivo de la restricción de ' + u.nombre });
      var r = el('button', { type: 'button', clase: 'peligro', texto: 'Restringir' });
      r.addEventListener('click', async function () {
        if (!motivo.value.trim()) { window.mostrarMensaje('Indica el motivo para restringir a ' + u.nombre + '.', 'error'); motivo.focus(); return; }
        r.disabled = true;
        try { await A.estadoUsuario(u.id, 'RESTRINGIDO', motivo.value.trim()); window.mostrarMensaje(u.nombre + ' fue restringido. Ya no podrá iniciar sesión.', 'exito'); cargar(); }
        catch (e) { window.mostrarMensaje(e.mensaje, 'error'); r.disabled = false; }
      });
      c.appendChild(motivo); c.appendChild(r);
    }
    return c;
  }

  async function cargar() {
    try {
      var lista = await A.usuarios({ buscar: $('buscar').value.trim(), rol: $('rol').value, estado: $('estado').value });
      var t = $('tabla'); t.textContent = '';
      t.appendChild(el('tr', {}, ['Nombre', 'Correo', 'Rol', 'Estado', 'Registro', 'Acciones'].map(function (h) { return el('th', { texto: h }); })));
      if (!lista.length) t.appendChild(el('tr', {}, [el('td', { colspan: '6', clase: 'suave', texto: 'No hay usuarios con esos filtros.' })]));
      lista.forEach(function (u) {
        t.appendChild(el('tr', {}, [
          el('td', { texto: u.nombre }), el('td', { texto: u.correo }), el('td', { texto: u.rol }),
          el('td', {}, [el('span', { clase: 'estado ' + u.estado, texto: u.estado }), u.motivoRestriccion ? el('div', { clase: 'suave', texto: u.motivoRestriccion }) : null]),
          el('td', { texto: new Date(u.fechaRegistro).toLocaleDateString('es-CO') }), el('td', {}, [acciones(u)]),
        ]));
      });
    } catch (e) { window.mostrarMensaje(e.mensaje, 'error'); }
  }
  $('filtros').addEventListener('submit', function (ev) { ev.preventDefault(); window.mostrarMensaje(''); cargar(); });
  cargar();
})();
