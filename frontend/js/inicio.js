/* Página de inicio: saludo según la sesión, accesos por rol y alojamientos destacados. */
(function () {
  'use strict';
  var TIPOS = { APARTAMENTO: 'Apartamento', CASA: 'Casa', HABITACION: 'Habitación', CABANA: 'Cabaña', OTRO: 'Otro' };
  var $ = function (id) { return document.getElementById(id); };

  function el(tag, atributos, hijos) {
    var n = document.createElement(tag);
    Object.keys(atributos || {}).forEach(function (k) {
      if (k === 'texto') n.textContent = atributos[k]; else if (k === 'clase') n.className = atributos[k]; else n.setAttribute(k, atributos[k]);
    });
    (hijos || []).forEach(function (h) { if (h) n.appendChild(typeof h === 'string' ? document.createTextNode(h) : h); });
    return n;
  }
  function cop(v) {
    return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(Number(v) || 0);
  }
  function leer(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }

  // ---- Sesión: si ya inició sesión, se cambian los botones por accesos de su rol ----
  var usuario = null;
  try { usuario = JSON.parse(leer('usuario') || 'null'); } catch (e) { usuario = null; }
  if (leer('token') && usuario) {
    var accesos = {
      HUESPED: [['pages/reservas/mis-reservas.html', 'Mis reservas'], ['pages/reservas/notificaciones.html', 'Notificaciones']],
      ANFITRION: [['pages/alojamientos/mis-publicaciones.html', 'Mis publicaciones'], ['pages/alojamientos/publicar.html', 'Publicar alojamiento'], ['pages/reservas/recibidas.html', 'Reservas recibidas']],
      ADMINISTRADOR: [['pages/admin/dashboard.html', 'Panel de administración'], ['pages/admin/usuarios.html', 'Usuarios'], ['pages/admin/publicaciones.html', 'Publicaciones']],
    }[usuario.rol] || [['pages/reservas/mis-reservas.html', 'Mis reservas']];

    $('saludo').textContent = 'Hola, ' + (usuario.nombre || 'de nuevo');
    var nav = $('nav-sesion'); nav.textContent = '';
    nav.appendChild(el('a', { href: accesos[0][0], texto: accesos[0][1] }));
    var salir = el('a', { href: 'index.html', texto: 'Salir' });
    salir.addEventListener('click', function () { try { localStorage.removeItem('token'); localStorage.removeItem('usuario'); } catch (e) { /* nada */ } });
    nav.appendChild(salir);

    var acciones = $('acciones-portada'); acciones.textContent = '';
    accesos.concat([['pages/reservas/perfil.html', 'Mi perfil']]).forEach(function (a, i) { acciones.appendChild(el('a', { clase: 'boton' + (i ? ' secundario' : ''), href: a[0], texto: a[1] })); });
  }

  // ---- Alojamientos destacados (también despierta el servidor de Render) ----
  var base = (window.APP_CONFIG && window.APP_CONFIG.API_BASE) || '';
  var cont = $('destacados'), estado = $('estado-destacados');
  fetch(base + '/api/accommodations?page=0&size=6')
    .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
    .then(function (p) {
      var items = (p && p.items) || [];
      if (!items.length) { estado.textContent = 'Aún no hay alojamientos publicados. ¡Sé el primer anfitrión!'; return; }
      estado.textContent = '';
      items.forEach(function (a) {
        var imagen = a.imagen ? el('img', { src: a.imagen, alt: 'Foto de ' + a.titulo, loading: 'lazy' }) : el('div', { clase: 'sin-imagen', texto: 'Sin foto' });
        cont.appendChild(el('article', { clase: 'tarjeta' }, [
          imagen,
          el('div', { clase: 'cuerpo' }, [
            el('h3', { style: 'font-size:1.05rem;margin:0' }, [el('a', { href: 'pages/alojamientos/detalle.html?id=' + encodeURIComponent(a.id), texto: a.titulo })]),
            el('span', { clase: 'suave', texto: (TIPOS[a.tipo] || a.tipo) + ' · ' + a.ciudad }),
            el('span', { clase: 'suave', texto: 'Hasta ' + a.capacidad + ' huéspedes' }),
            el('span', { clase: 'precio', texto: cop(a.precioPorNoche) + ' / noche' }),
          ]),
        ]));
      });
    })
    .catch(function () {
      estado.textContent = 'No pudimos cargar los alojamientos en este momento. Puedes intentarlo desde "Buscar alojamientos".';
    });
})();
