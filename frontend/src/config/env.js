// Lee configuración runtime (window.__ENV__, inyectada por docker-entrypoint.sh al
// arrancar el contenedor — ver frontend/docker-entrypoint.sh) con fallback a
// import.meta.env (Vite, usado en "npm run dev" y en cualquier build que sí las
// hornee). Esto permite que la MISMA imagen de Docker sirva para staging, production
// o cualquier dominio futuro con solo cambiar las variables de entorno del contenedor,
// sin reconstruir la imagen.
function readEnv(key) {
  if (typeof window !== 'undefined' && window.__ENV__ && window.__ENV__[key]) {
    return window.__ENV__[key];
  }
  return import.meta.env[key];
}

export const VITE_API_BASE_URL = readEnv('VITE_API_BASE_URL');
export const VITE_KEYCLOAK_URL = readEnv('VITE_KEYCLOAK_URL');
export const VITE_KEYCLOAK_REALM = readEnv('VITE_KEYCLOAK_REALM');
export const VITE_KEYCLOAK_CLIENT_ID = readEnv('VITE_KEYCLOAK_CLIENT_ID');
