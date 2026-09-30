import { defineConfig } from '@playwright/test';
export default defineConfig({testDir:'./tests',timeout:60000,workers:1,use:{baseURL:'http://127.0.0.1:5178',channel:'msedge',reducedMotion:'reduce'},webServer:{command:'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5178 --strictPort',url:'http://127.0.0.1:5178',reuseExistingServer:!process.env.CI}});
