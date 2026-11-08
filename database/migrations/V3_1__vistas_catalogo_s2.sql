-- =====================================================================================
-- V3.1 — Vistas de lectura sobre las tablas de S2 (catálogo). AJUSTAR NOMBRES si en el esquema real de S2
-- se llaman distinto: S3 solo lee estas vistas, así que es el único lugar que hay que tocar.
--
-- SUPUESTOS (confirmar con quien implementa S2 — página 12 de Confluence):
--   esquema "catalogo"; tablas alojamiento, politica_cancelacion, servicio_adicional, parametro_sistema
--   alojamiento(id uuid, id_anfitrion uuid, estado_publicacion text, precio_base, tarifa_limpieza, descuento_pct,
--               descuento_min_noches, capacidad, id_politica)
--   politica_cancelacion(id, nombre, dias_antelacion, porcentaje_reembolso)
--   servicio_adicional(id uuid, id_alojamiento uuid, nombre, costo, activo)
--   parametro_sistema(clave, valor)
-- Si S2 todavía no existe en la base, ejecutar primero db/dev/seed_s2_minimo.sql (solo desarrollo y pruebas).
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
