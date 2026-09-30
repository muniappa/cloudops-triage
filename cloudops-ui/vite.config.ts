import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    proxy: {
      '/services': 'http://localhost:8080',
      '/incidents': 'http://localhost:8080',
      '/suggestions': 'http://localhost:8080',
    },
  },
})
