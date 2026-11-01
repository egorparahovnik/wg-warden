import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
export default defineConfig({
  plugins:[react(),tailwindcss()],
  server:{proxy:{'/api':'http://localhost:8081','/actuator':'http://localhost:8081'}},
  build:{rollupOptions:{output:{manualChunks:{charts:['recharts'],query:['@tanstack/react-query']}}}},
})
