import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig(async () => {
  const migrations = await readD1Migrations("./migrations");
  return {
    plugins: [
      cloudflareTest({
        wrangler: { configPath: "./wrangler.toml" },
        miniflare: {
          bindings: { TEST_MIGRATIONS: migrations, MAX_MEDIA_BYTES: "1024", QUOTA_BYTES: "4096" },
        },
      }),
    ],
    test: { setupFiles: ["./test/setup.ts"] },
  };
});
