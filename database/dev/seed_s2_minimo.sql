-- SOLO DESARROLLO / PRUEBAS: tablas mínimas de S2 y datos A-01..A-03 para poder probar S3/S4/S5 sin S2.
-- Datos de prueba inferidos de la página 19 (CAL-01..06). No ejecutar en la base compartida del equipo.
CREATE SCHEMA IF NOT EXISTS catalogo;
CREATE TABLE IF NOT EXISTS catalogo.politica_cancelacion (
    id uuid PRIMARY KEY, nombre text NOT NULL, dias_antelacion int NOT NULL, porcentaje_reembolso int NOT NULL);
CREATE TABLE IF NOT EXISTS catalogo.alojamiento (
    id uuid PRIMARY KEY, id_anfitrion uuid NOT NULL, estado_publicacion text NOT NULL,
    precio_base numeric(12,2) NOT NULL, tarifa_limpieza numeric(12,2) NOT NULL DEFAULT 0,
    descuento_pct numeric(5,2), descuento_min_noches int, capacidad int NOT NULL, id_politica uuid NOT NULL);
CREATE TABLE IF NOT EXISTS catalogo.servicio_adicional (
    id uuid PRIMARY KEY, id_alojamiento uuid NOT NULL, nombre text NOT NULL, costo numeric(12,2) NOT NULL,
    activo boolean NOT NULL DEFAULT true);
CREATE TABLE IF NOT EXISTS catalogo.parametro_sistema (clave text PRIMARY KEY, valor text NOT NULL);

INSERT INTO catalogo.politica_cancelacion VALUES
 ('00000000-0000-0000-0000-0000000000f1', 'FLEXIBLE', 2, 100),
 ('00000000-0000-0000-0000-0000000000f2', 'MODERADA', 7, 100),
 ('00000000-0000-0000-0000-0000000000f3', 'ESTRICTA', 14, 50)
ON CONFLICT DO NOTHING;
-- Anfitrión de pruebas: 00000000-0000-0000-0000-00000000a001
INSERT INTO catalogo.alojamiento VALUES
 ('00000000-0000-0000-0000-0000000a0001', '00000000-0000-0000-0000-00000000a001', 'PUBLICADO',
  200000, 50000, 10, 7, 4, '00000000-0000-0000-0000-0000000000f2'),   -- A-01 MODERADA
 ('00000000-0000-0000-0000-0000000a0002', '00000000-0000-0000-0000-00000000a001', 'PUBLICADO',
  200000, 50000, 10, 7, 4, '00000000-0000-0000-0000-0000000000f1'),   -- A-02 FLEXIBLE
 ('00000000-0000-0000-0000-0000000a0003', '00000000-0000-0000-0000-00000000a001', 'PUBLICADO',
  200000, 50000, 10, 7, 4, '00000000-0000-0000-0000-0000000000f3')    -- A-03 ESTRICTA
ON CONFLICT DO NOTHING;
INSERT INTO catalogo.servicio_adicional VALUES
 ('00000000-0000-0000-0000-0000000b0001', '00000000-0000-0000-0000-0000000a0001', 'Desayuno', 30000, true)
ON CONFLICT DO NOTHING;
INSERT INTO catalogo.parametro_sistema VALUES ('tarifa_servicio_pct', '10') ON CONFLICT DO NOTHING;
