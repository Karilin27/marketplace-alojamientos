-- =====================================================================================
-- V3.1 — Vistas de lectura sobre las tablas de S2 (esquema "catalogo", creado en V2).
-- S3 solo lee estas vistas y nunca escribe en tablas ajenas (excepción controlada D-13).
-- Requiere V2. Si S2 cambia el nombre de alguna columna, este es el único archivo que hay que tocar.
-- =====================================================================================

CREATE OR REPLACE VIEW reservas.v_alojamiento AS
SELECT a.id,
       a.id_anfitrion,
       a.estado_publicacion,
       a.precio_base,
       a.tarifa_limpieza,
       COALESCE(a.descuento_pct, 0)        AS descuento_pct,
       COALESCE(a.descuento_min_noches, 0) AS descuento_min_noches,
       a.capacidad,
       p.nombre                            AS politica_nombre,
       p.dias_antelacion                   AS politica_dias,
       p.porcentaje_reembolso              AS politica_pct
  FROM catalogo.alojamiento a
  JOIN catalogo.politica_cancelacion p ON p.id = a.id_politica;

CREATE OR REPLACE VIEW reservas.v_servicio_adicional AS
SELECT id, id_alojamiento, nombre, costo, activo FROM catalogo.servicio_adicional;

CREATE OR REPLACE VIEW reservas.v_parametro_sistema AS
SELECT clave, valor FROM catalogo.parametro_sistema;
