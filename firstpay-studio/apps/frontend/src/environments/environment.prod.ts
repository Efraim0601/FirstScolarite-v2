export const environment = {
  production: true,
  // Préfixe vide : les services appellent déjà /api/v1/... (nginx/Caddy proxy /api/ → gateway).
  apiUrl: '',
  showDemoAccounts: false,
};
