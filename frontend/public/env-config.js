// Default vacío para "npm run dev" (Vite sirve public/ tal cual) y como stub dentro
// del build de producción (Vite copia public/ a dist/ sin cambios). En el contenedor
// Docker real, docker-entrypoint.sh SOBREESCRIBE este archivo al arrancar con los
// valores reales de las variables de entorno del contenedor — ver
// frontend/docker-entrypoint.sh y src/config/env.js.
window.__ENV__ = window.__ENV__ || {};
