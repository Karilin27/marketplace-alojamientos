-- =====================================================================================
-- Solo desarrollo y pruebas (no es una migración). Prepara datos para CP-16-01, CP-16-04, CP-22-01 y CP-22-02:
-- una reserva COMPLETADA del huésped indicado en un alojamiento ACTIVO (por defecto, el primero que exista).
-- Cambie el correo antes de ejecutar. En producción las reservas pasan a COMPLETADA solas (TareasProgramadas).
-- =====================================================================================
INSERT INTO reservas.reserva (id, id_alojamiento, id_huesped, fecha_llegada, fecha_salida, huespedes, estado,
                              subtotal, descuento, tarifa_limpieza, tarifa_servicio, total,
                              politica_nombre, politica_dias, politica_pct, llave_idempotencia, expira_en)
SELECT gen_random_uuid(), a.id, u.id, current_date - 10, current_date - 7, 2, 'COMPLETADA',
       a.precio_base * 3, 0, a.tarifa_limpieza, round(a.precio_base * 3 * 0.10, 2),
       a.precio_base * 3 + a.tarifa_limpieza + round(a.precio_base * 3 * 0.10, 2),
       p.nombre, p.dias_antelacion, p.porcentaje_reembolso, 'seed-resena-' || gen_random_uuid(), now()
  FROM identidad.usuario u
 CROSS JOIN LATERAL (SELECT * FROM catalogo.alojamiento WHERE estado_publicacion = 'ACTIVA'
                      ORDER BY fecha_creacion LIMIT 1) a
  JOIN catalogo.politica_cancelacion p ON p.id = a.id_politica
 WHERE u.correo = 'huesped@test.com'          -- ← cambie el correo del huésped de prueba
RETURNING id AS id_reserva, id_alojamiento;
