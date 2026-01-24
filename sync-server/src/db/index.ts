import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema";

const connectionString = `postgres://${process.env.DB_USER || "writer_user"}:${process.env.DB_PASSWORD}@${process.env.DB_HOST || "localhost"}:${process.env.DB_PORT || 5432}/${process.env.DB_NAME || "writer_sync"}`;

// Disable prepare for Bun compatibility
const client = postgres(connectionString, { prepare: false });

export const db = drizzle(client, { schema });

// Test connection
try {
  await client`SELECT 1`;
  console.log("✓ Connected to PostgreSQL database");
} catch (error) {
  console.error("✗ Failed to connect to database:", error);
  process.exit(1);
}
