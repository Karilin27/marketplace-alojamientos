-- SOLO DESARROLLO / PRUEBAS: datos A-01..A-03 para probar S3/S4/S5 sin esperar a que S1 y S2 tengan datos.
-- Requiere haber ejecutado V1 y V2 (crean identidad.usuario y las tablas de catalogo). No ejecutar en la base compartida.
-- Datos inferidos de la página 19 (CAL-01..06). El hash de la contraseña es un valor de relleno: ese usuario no puede iniciar sesión.
INSERT INTO identidad.usuario (id, nombre, correo, contrasena_hash, rol)
VALUES ('00000000-0000-0000-0000-00000000a001', 'Anfitrión de pruebas', 'anfitrion.pruebas@example.com', 'NO-INICIA-SESION', 'ANFITRION')
ON CONFLICT DO NOTHING;

INSERT INTO catalogo.alojamiento
  (id, id_anfitrion, id_politica, titulo, descripcion, tipo, ciudad, direccion, capacidad, precio_base,
   estado_publicacion, tarifa_limpieza, descuento_pct, descuento_min_noches)
SELECT v.id::uuid, '00000000-0000-0000-0000-00000000a001', p.id, v.titulo, 'Alojamiento de prueba', 'APARTAMENTO',
       'Bogotá', 'Calle 1 # 2-3', 4, 200000, 'ACTIVA', 50000, 10, 7
  FROM (VALUES
        ('00000000-0000-0000-0000-0000000a0001', 'A-01 (MODERADA)', 'MODERADA'),
        ('00000000-0000-0000-0000-0000000a0002', 'A-02 (FLEXIBLE)', 'FLEXIBLE'),
        ('00000000-0000-0000-0000-0000000a0003', 'A-03 (ESTRICTA)', 'ESTRICTA')) AS v(id, titulo, politica)
  JOIN catalogo.politica_cancelacion p ON p.nombre = v.politica
ON CONFLICT DO NOTHING;

INSERT INTO catalogo.servicio_adicional (id, id_alojamiento, nombre, costo, activo)
VALUES ('00000000-0000-0000-0000-0000000b0001', '00000000-0000-0000-0000-0000000a0001', 'Desayuno', 30000, true)
ON CONFLICT DO NOTHING;
