// Configuración del frontend de reservas. Ajustar API_BASE al URL de Render del servicio S3/S4/S5.
window.RESERVAS_CONFIG = {
  API_BASE: 'http://localhost:8080',
  // Clave de localStorage donde la pantalla de login (S1) guarda el JWT. Confirmar con quien hizo el login.
  TOKEN_KEY: 'token',
  // Página de login y de detalle de alojamiento del resto del equipo
  LOGIN_URL: '../login.html',
};
