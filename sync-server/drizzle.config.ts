import type { Config } from "drizzle-kit";

export default {
  schema: "./src/db/schema.ts",
  out: "./drizzle",
  dialect: "postgresql",
  dbCredentials: {
    host: process.env.DB_HOST || "localhost",
    port: Number(process.env.DB_PORT) || 5432,
    user: process.env.DB_USER || "writer_user",
    password: process.env.DB_PASSWORD || "",
    database: process.env.DB_NAME || "writer_sync",
    ssl: false,
  },
} satisfies Config;
