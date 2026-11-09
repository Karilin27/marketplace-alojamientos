-- Reversión de V2 y V1 (catálogo e identidad). Se ejecuta DESPUÉS de V4 y V3, porque sus tablas dependen de estas.
-- DESTRUCTIVO: borra usuarios y alojamientos. Hacer pg_dump antes.
DROP SCHEMA IF EXISTS catalogo CASCADE;
DROP SCHEMA IF EXISTS identidad CASCADE;
