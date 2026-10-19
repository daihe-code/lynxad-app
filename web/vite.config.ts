import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const config = {
    plugins: [vue()],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
  };
  if (command !== "serve") {
    return config;
  }
  const port = Number(env.LYNX_WEB_PORT);
  if (!Number.isInteger(port) || port <= 0) {
    throw new Error("LYNX_WEB_PORT 必须是正整数");
  }
  return {
    ...config,
    server: {
      port,
      strictPort: true,
      proxy: {
        "/lynx-ad": {
          target: "http://127.0.0.1:8080",
          changeOrigin: true,
        },
      },
    },
  };
});
