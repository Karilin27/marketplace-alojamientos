/* Pantalla de perfil (HU-004): consultar y actualizar los datos propios, y desactivar la cuenta. */
(function () {
  'use strict';
  var cfg = window.RESERVAS_CONFIG, P = window.PerfilLogica;
  var $ = function (id) { return document.getElementById(id); };
  var CAMPOS = ['nombre', 'telefono', 'documento', 'pais', 'fotoUrl'];
  var guardado = null;      // últimos datos que confirmó el servidor
  var cuenta = null;

  function token() { try { return localStorage.getItem(cfg.TOKEN_KEY); } catch (e) { return null; } }

  async function llamar(metodo, ruta, cuerpo) {
    var h = { Accept: 'application/json' }, t = token();
    if (t) h.Authorization = 'Bearer ' + t;
    if (cuerpo !== undefined) h['Content-Type'] = 'application/json';
    var resp;
    try {
      resp = await fetch(cfg.API_BASE + ruta, { method: metodo, headers: h, body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) });
    } catch (e) { throw { status: 0, mensaje: P.mensajeError(null, 0), detalles: [] }; }
    if (resp.status === 401) {
      try { localStorage.removeItem(cfg.TOKEN_KEY); localStorage.removeItem('usuario'); } catch (e) { /* sin almacenamiento */ }
      window.location.href = cfg.LOGIN_URL + '?volver=' + encodeURIComponent(window.location.pathname + window.location.search);
      throw { status: 401, mensaje: 'Tu sesión venció. Inicia sesión de nuevo.', detalles: [] };
    }
    var datos = null;
    try { datos = await resp.json(); } catch (e) { datos = null; }
    if (!resp.ok) throw { status: resp.status, codigo: datos && datos.codigo, mensaje: P.mensajeError(datos, resp.status), detalles: (datos && datos.detalles) || [] };
    return datos;
  }

  function valoresFormulario() {
    var v = {}; CAMPOS.forEach(function (c) { v[c] = $(c).value; }); return v;
  }
  function mostrarErrores(errores) {
    CAMPOS.forEach(function (c) {
      $('e-' + c).textContent = errores[c] || '';
      if (errores[c]) $(c).setAttribute('aria-invalid', 'true'); else $(c).removeAttribute('aria-invalid');
    });
  }
  function rellenar(p) {
    cuenta = p;
    guardado = { nombre: p.nombre, telefono: p.telefono, documento: p.documento, pais: p.pais, fotoUrl: p.fotoUrl };
    $('correo').value = p.correo || '';
    $('rol').value = P.etiquetaRol(p.rol);
    CAMPOS.forEach(function (c) { $(c).value = p[c] == null ? '' : p[c]; });
    var e = P.etiquetaEstado(p.estado);
    $('estado').textContent = e.texto; $('estado').className = 'etiqueta ' + e.clase;
    $('resumen').textContent = 'Sesión iniciada como ' + (p.correo || '') + '.';
    $('form-perfil').classList.remove('oculto');
    mostrarErrores({});
    actualizarBotones();
    // Una cuenta restringida por un administrador no puede cambiar su estado (el servidor también lo impide).
    $('desactivar').disabled = p.estado !== 'ACTIVO';
  }
  function actualizarBotones() {
    $('guardar').disabled = !guardado || !P.hayCambios(valoresFormulario(), guardado);
  }

  async function cargar() {
    try { rellenar(await llamar('GET', '/api/users/me')); }
    catch (e) {
      if (e.status === 401) return;
      $('resumen').textContent = '';
      window.mostrarMensaje(e.mensaje, 'error');
    }
  }

  async function guardar(ev) {
    ev.preventDefault();
    window.mostrarMensaje('');
    var errores = P.validarPerfil(valoresFormulario());
    mostrarErrores(errores);
    var primero = CAMPOS.filter(function (c) { return errores[c]; })[0];
    if (primero) { $(primero).focus(); return; }
    $('guardar').disabled = true;
    try {
      rellenar(await llamar('PUT', '/api/users/me', P.normalizar(valoresFormulario())));
      try {                                   // mantiene al día el nombre que se muestra en el inicio
        var u = JSON.parse(localStorage.getItem('usuario') || 'null');
        if (u) { u.nombre = cuenta.nombre; localStorage.setItem('usuario', JSON.stringify(u)); }
      } catch (e) { /* sin almacenamiento */ }
      window.mostrarMensaje('Tus datos se guardaron correctamente.', 'exito');
    } catch (e) {
      if (e.status === 401) return;
      var porCampo = P.erroresPorCampo(e.detalles);
      mostrarErrores(porCampo);
      window.mostrarMensaje(Object.keys(porCampo).length ? 'Corrige los datos marcados e intenta de nuevo.' : e.mensaje, 'error');
      actualizarBotones();
    }
  }

  async function desactivar() {
    $('dlg-ok').disabled = true;
    try {
      var r = await llamar('PATCH', '/api/users/me/status', { estado: 'DESACTIVADO' });
      try { localStorage.removeItem(cfg.TOKEN_KEY); localStorage.removeItem('usuario'); } catch (e) { /* sin almacenamiento */ }
      $('dlg-desactivar').close();
      $('form-perfil').classList.add('oculto');
      $('seccion-cuenta').classList.add('oculto');
      $('barra').classList.add('oculto');
      $('resumen').textContent = '';
      window.mostrarMensaje(P.textoDesactivada(r), 'exito');
      var volver = window.el('a', { clase: 'boton', href: cfg.LOGIN_URL, texto: 'Ir a iniciar sesión' });
      $('mensaje').appendChild(window.el('p', {}, [volver]));
    } catch (e) {
      $('dlg-desactivar').close();
      if (e.status === 401) return;
      var detalle = e.codigo === 'RESERVAS_CONFIRMADAS_PENDIENTES' && e.detalles.length ? ' ' + e.detalles.join(' ') : '';
      window.mostrarMensaje(e.mensaje + detalle, 'error');
    } finally { $('dlg-ok').disabled = false; }
  }

  CAMPOS.forEach(function (c) { $(c).addEventListener('input', actualizarBotones); });
  $('form-perfil').addEventListener('submit', guardar);
  $('descartar').addEventListener('click', function () { if (cuenta) { rellenar(cuenta); window.mostrarMensaje(''); } });
  $('desactivar').addEventListener('click', function () { window.mostrarMensaje(''); $('dlg-desactivar').showModal(); });
  $('dlg-no').addEventListener('click', function () { $('dlg-desactivar').close(); });
  $('dlg-ok').addEventListener('click', desactivar);

  if (!token()) { window.location.replace(cfg.LOGIN_URL + '?volver=' + encodeURIComponent(window.location.pathname)); return; }
  cargar();
})();
