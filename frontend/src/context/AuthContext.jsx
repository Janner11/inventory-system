import { createContext, useEffect, useState } from 'react';
import keycloak from '../services/keycloak';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);

  useEffect(() => {
    keycloak
      .init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
      })
      .then((authenticated) => {
        setIsAuthenticated(authenticated);
        if (authenticated) {
          setToken(keycloak.token);
          setUser(keycloak.tokenParsed);
        }
      })
      .finally(() => setIsLoading(false));

    keycloak.onAuthSuccess = () => {
      setIsAuthenticated(true);
      setToken(keycloak.token);
      setUser(keycloak.tokenParsed);
    };

    keycloak.onAuthLogout = () => {
      setIsAuthenticated(false);
      setToken(null);
      setUser(null);
    };

    keycloak.onTokenExpired = () => {
      keycloak
        .updateToken(30)
        .then(() => setToken(keycloak.token))
        .catch(() => keycloak.login());
    };
  }, []);

  const login = () => keycloak.login();
  const logout = () => keycloak.logout({ redirectUri: window.location.origin });

  const value = { isAuthenticated, isLoading, user, token, login, logout };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
