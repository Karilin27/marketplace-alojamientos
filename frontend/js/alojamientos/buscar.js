/* HU-009 — Buscar alojamientos (público). */
(function () {
  'use strict';
  var A = window.AlojamientosApi, L = window.AlojamientosLogica, el = window.el;
  var $ = function (id) { return document.getElementById(id); };
  var pagina = 0, TAMANO = 12;

  L.SERVICIOS.forEach(function (s) {
    $('servicios').appendChild(el('label', {}, [el('input', { type: 'checkbox', value: s }), s]));
  });
  var hoy = new Date().toISOString().slice(0, 10);
  $('llegada').min = hoy; $('salida').min = hoy;

  function filtros() {
    var marcados = [].slice.call(document.querySelectorAll('#servicios input:checked')).map(function (c) { return c.value; });
    return {
      ciudad: $('ciudad').value.trim(), llegada: $('llegada').value, salida: $('salida').value, huespedes: $('huespedes').value,
      tipo: $('tipo').value, precioMin: $('precioMin').value, precioMax: $('precioMax').value,
      servicios: marcados.join(','), page: pagina, size: TAMANO,
    };
  }

  function tarjeta(r) {
    var enlace = 'detalle.html?id=' + encodeURIComponent(r.id);
    var llegada = $('llegada').value, salida = $('salida').value, h = $('huespedes').value;
    if (llegada && salida) enlace += '&llegada=' + llegada + '&salida=' + salida + (h ? '&huespedes=' + h : '');
    var imagen = r.imagen ? el('img', { src: r.imagen, alt: 'Foto de ' + r.titulo, loading: 'lazy' }) : el('div', { clase: 'sin-imagen', texto: 'Sin foto' });
    return el('article', { clase: 'tarjeta' }, [
      imagen,
      el('div', { clase: 'cuerpo' }, [
        el('h2', { style: 'font-size:1.05rem;margin:0' }, [el('a', { href: enlace, texto: r.titulo })]),
        el('span', { clase: 'suave', texto: (window.TIPOS[r.tipo] || r.tipo) + ' · ' + r.ciudad }),
        el('span', { clase: 'suave', texto: 'Hasta ' + r.capacidad + ' huéspedes' }),
        el('span', { clase: 'precio', texto: window.formatearCOP(r.precioPorNoche) + ' / noche' }),
      ]),
    ]);
  }

  async function buscar() {
    var f = filtros();
    $('e-filtros').textContent = L.validarFiltros(f, hoy);
    if ($('e-filtros').textContent) return;
    window.mostrarMensaje(''); $('btn-buscar').disabled = true;
    try {
      var r = await A.buscar(f);
      var cont = $('resultados'); cont.textContent = '';
      if (!r.items.length) {                                       // CA-05
        $('resumen').textContent = '';
        window.mostrarMensaje('No encontramos alojamientos con esos criterios. Prueba con otras fechas o menos filtros.', 'info');
      } else {
        $('resumen').textContent = r.total + (r.total === 1 ? ' alojamiento encontrado' : ' alojamientos encontrados');
        r.items.forEach(function (x) { cont.appendChild(tarjeta(x)); });
      }
      var paginas = Math.max(1, Math.ceil(r.total / TAMANO));
      $('paginador').classList.toggle('oculto', paginas <= 1);
      $('pagina').textContent = 'Página ' + (pagina + 1) + ' de ' + paginas;
      $('anterior').disabled = pagina === 0; $('siguiente').disabled = pagina + 1 >= paginas;
    } catch (e) {
      window.mostrarMensaje(e.mensaje, 'error');
    } finally { $('btn-buscar').disabled = false; }
  }

  $('filtros').addEventListener('submit', function (ev) { ev.preventDefault(); pagina = 0; buscar(); });
  $('anterior').addEventListener('click', function () { pagina = Math.max(0, pagina - 1); buscar(); });
  $('siguiente').addEventListener('click', function () { pagina += 1; buscar(); });
  buscar();                                                          // A1: sin filtros lista las ACTIVAS
})();
