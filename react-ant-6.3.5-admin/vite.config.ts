import path from 'path';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const publicHost = env.VITE_DEV_PUBLIC_HOST?.trim();
  const publicProtocol = (env.VITE_DEV_PUBLIC_PROTOCOL || 'https').trim().toLowerCase();
  const publicPortRaw = env.VITE_DEV_PUBLIC_PORT?.trim();
  const publicPort = publicPortRaw ? Number(publicPortRaw) : publicProtocol === 'https' ? 443 : 80;
  const appBase = env.VITE_APP_BASE?.trim() || '/';

  return {
    base: appBase,
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5174,
      strictPort: true,
      allowedHosts: ['youyou.tminos.com'],
      hmr: publicHost
        ? {
            host: publicHost,
            protocol: publicProtocol === 'https' ? 'wss' : 'ws',
            clientPort: publicPort,
          }
        : undefined,
      proxy: {
        '/api': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
        '/uploads': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
  };
});
