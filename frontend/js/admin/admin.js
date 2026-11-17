/* S6 — Cliente HTTP y utilidades comunes del panel de administración (HU-018 a HU-022). Solo ADMINISTRADOR. */
(function () {
  'use strict';
  function base() { return (window.APP_CONFIG && window.APP_CONFIG.API_BASE) || 'http://localhost:8080'; }
  function token() { try { return localStorage.getItem('token'); } catch (e) { return null; } }
  function usuario() { try { return JSON.parse(localStorage.getItem('usuario') || 'null'); } catch (e) { return null; } }

  async function llamar(metodo, ruta, cuerpo, binario) {
    var h = { Accept: binario ? '*/*' : 'application/json', Authorization: 'Bearer ' + token() };
    if (cuerpo !== undefined) h['Content-Type'] = 'application/json';
    var r;
    try { r = await fetch(base() + ruta, { method: metodo, headers: h, body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) }); }
    catch (e) { throw { status: 0, mensaje: 'No se pudo conectar con el servidor. Verifica que el backend esté en ejecución.' }; }
    if (r.status === 401) { window.location.href = '../login.html?volver=' + encodeURIComponent(location.pathname); throw { status: 401, mensaje: 'Sesión vencida.' }; }
    if (r.ok && binario) return { blob: await r.blob(), disposicion: r.headers.get('Content-Disposition') || '' };
    var d = null; try { d = await r.json(); } catch (e) { /* sin cuerpo */ }
    if (!r.ok) {
      var m = d && d.mensaje ? d.mensaje + (d.detalles && d.detalles.length ? ' ' + d.detalles.join(' · ') : '') : (r.status === 403 ? 'Solo un administrador puede hacer esto.' : 'Ocurrió un error inesperado.');
      throw { status: r.status, mensaje: m };
    }
    return d;
  }
  function q(obj) {
    var p = Object.keys(obj).filter(function (k) { return obj[k] !== '' && obj[k] != null; })
      .map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(obj[k]); });
    return p.length ? '?' + p.join('&') : '';
  }

  window.AdminApi = {
    dashboard: function () { return llamar('GET', '/api/admin/dashboard'); },
    exportar: function (tipo, formato) { return llamar('POST', '/api/admin/reports/export', { tipo: tipo, formato: formato }, true); },
    usuarios: function (f) { return llamar('GET', '/api/admin/users' + q(f)); },
    estadoUsuario: function (id, estado, motivo) { return llamar('PATCH', '/api/admin/users/' + id + '/status', { estado: estado, motivo: motivo || null }); },
    publicaciones: function (estado) { return llamar('GET', '/api/admin/accommodations' + q({ estado: estado })); },
    moderar: function (id, accion, motivo) { return llamar('PATCH', '/api/admin/accommodations/' + id + '/moderation', { accion: accion, motivo: motivo || null }); },
    revisiones: function () { return llamar('GET', '/api/admin/bookings/manual-review'); },
    cerrarRevision: function (id, decision) { return llamar('PATCH', '/api/admin/bookings/' + id + '/manual-review', { decision: decision }); },
    auditoria: function (f) { return llamar('GET', '/api/admin/audit' + q(f)); },
    tarifaServicio: function () { return llamar('GET', '/api/admin/settings/service-fee'); },
    guardarTarifaServicio: function (porcentaje) { return llamar('PUT', '/api/admin/settings/service-fee', { porcentaje: porcentaje }); },
  };

  window.el = window.el || function (tag, at, hijos) {
    var n = document.createElement(tag);
    Object.keys(at || {}).forEach(function (k) { if (k === 'texto') n.textContent = at[k]; else if (k === 'clase') n.className = at[k]; else n.setAttribute(k, at[k]); });
    [].concat(hijos || []).forEach(function (h) { if (h != null) n.appendChild(typeof h === 'string' ? document.createTextNode(h) : h); });
    return n;
  };
  window.mostrarMensaje = function (texto, tipo) {
    var c = document.getElementById('mensaje'); if (!c) return; c.textContent = '';
    if (texto) c.appendChild(window.el('div', { clase: 'aviso ' + (tipo || 'info'), texto: texto }));
  };
  window.cop = function (v) { return '$' + Number(v || 0).toLocaleString('es-CO', { maximumFractionDigits: 0 }); };
  window.fechaHora = function (v) { return v ? new Date(v).toLocaleString('es-CO') : ''; };

  // Control de acceso y barra de navegación (CA de restricción por rol de HU-018 a HU-022)
  window.exigirAdmin = function () {
    var u = usuario();
    if (!token()) { window.location.href = '../login.html?volver=' + encodeURIComponent(location.pathname); return false; }
    if (!u || u.rol !== 'ADMINISTRADOR') {
      window.mostrarMensaje('Solo un administrador puede ver el panel de administración.', 'error');
      var main = document.querySelector('[data-contenido]'); if (main) main.classList.add('oculto');
      return false;
    }
    return true;
  };
  document.addEventListener('DOMContentLoaded', function () {
    var barra = document.getElementById('barra'); if (!barra) return;
    var actual = barra.getAttribute('data-actual');
    barra.appendChild(window.el('a', { clase: 'marca', href: '../../index.html', texto: 'Marketplace · Administración' }));
    [['dashboard', 'Indicadores y reportes'], ['usuarios', 'Usuarios'], ['publicaciones', 'Publicaciones'],
      ['auditoria', 'Auditoría'], ['../resenas/moderar', 'Reseñas'], ['../reservas/perfil', 'Mi perfil']].forEach(function (e) {
      var a = window.el('a', { href: e[0] + '.html', texto: e[1] });
      if (e[0] === actual) a.setAttribute('aria-current', 'page');
      barra.appendChild(a);
    });
    var salir = window.el('a', { href: '../../index.html', texto: 'Salir' });
    salir.addEventListener('click', function () { try { localStorage.removeItem('token'); localStorage.removeItem('usuario'); } catch (e) { /* nada */ } });
    barra.appendChild(salir);
  });
})();
