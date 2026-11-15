# Despliegue: backend en Render y frontend en GitHub Pages

Backend: `render.yaml` + `backend-java/Dockerfile`. Frontend: `.github/workflows/pages.yml` publica la carpeta `frontend/`.

Variables del servicio en Render: DATABASE_URL (jdbc:postgresql://<host>:5432/postgres?sslmode=require), DATABASE_USER,
DATABASE_PASSWORD, JWT_SECRET (32+ caracteres), PASARELA=simulada, CORS_ORIGENES=https://karilin27.github.io,
COTIZACION_SECRET (la genera Render). Para subir fotos desde el computador (D-21): SUPABASE_URL, SUPABASE_SERVICE_KEY
(llave service_role, secreta) y SUPABASE_BUCKET=alojamientos (bucket público creado en Supabase → Storage).

Verificacion: GET <url-render>/actuator/health -> {"status":"UP"}; luego api/prueba-verificacion.ps1 con -BaseUrl <url-render>.
Nota: en el plan gratuito el servicio se duerme tras 15 minutos sin uso y la primera peticion tarda cerca de un minuto.
