import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // sockjs-client (the Stage 3 live-feed WebSocket dependency) references
  // Node's `global`, which doesn't exist in a browser. Vite's dev server
  // (esbuild) tolerates this; the production build (Rollup) doesn't, and
  // the whole app crashes on load with "global is not defined" before
  // React ever mounts — this is why it only showed up once deployed.
  define: {
    global: 'globalThis',
  },
})
