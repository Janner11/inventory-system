import Keycloak from 'keycloak-js';
import { VITE_KEYCLOAK_URL, VITE_KEYCLOAK_REALM, VITE_KEYCLOAK_CLIENT_ID } from '../config/env';

const keycloak = new Keycloak({
  url: VITE_KEYCLOAK_URL,
  realm: VITE_KEYCLOAK_REALM,
  clientId: VITE_KEYCLOAK_CLIENT_ID,
});

export default keycloak;
