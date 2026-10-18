import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The UI talks to the Java API. During development we proxy /api to it so there
// are no CORS surprises. Set SYMMETRY_API (default http://localhost:8080).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.SYMMETRY_API ?? "http://localhost:8080",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
});
