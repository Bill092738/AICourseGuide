import { defineConfig, mergeConfig } from "vitest/config"
import viteConfig from "./vite.config"

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      include: ["src/**/*.test.{ts,tsx}"],
      css: false,
      // material-color-utilities ships ESM with extensionless relative imports that Node
      // cannot resolve on its own; let Vite transform it like application code.
      server: { deps: { inline: ["@material/material-color-utilities"] } },
    },
  }),
)
