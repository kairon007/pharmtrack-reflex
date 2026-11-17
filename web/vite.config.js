import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';

// The marketing site is a static multi-page build: the landing page plus the
// five prose pages it links to. No framework, no runtime dependencies —
// `npm run build` copies, fingerprints and minifies, and `dist/` is what
// Netlify publishes.
export default defineConfig({
  build: {
    rollupOptions: {
      input: {
        main: fileURLToPath(new URL('./index.html', import.meta.url)),
        privacy: fileURLToPath(new URL('./privacy.html', import.meta.url)),
        terms: fileURLToPath(new URL('./terms.html', import.meta.url)),
        eula: fileURLToPath(new URL('./eula.html', import.meta.url)),
        support: fileURLToPath(new URL('./support.html', import.meta.url)),
        deleteAccount: fileURLToPath(new URL('./delete-account.html', import.meta.url)),
      },
    },
  },
});
