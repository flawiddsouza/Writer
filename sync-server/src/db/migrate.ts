import { migrate } from "drizzle-orm/postgres-js/migrator";
import postgres from "postgres";
import { drizzle } from "drizzle-orm/postgres-js";

const connectionString = `postgres://${process.env.DB_USER || "writer_user"}:${process.env.DB_PASSWORD}@${process.env.DB_HOST || "localhost"}:${process.env.DB_PORT || 5432}/${process.env.DB_NAME || "writer_sync"}`;

const client = postgres(connectionString, { max: 1 });
const db = drizzle(client);

console.log("Running migrations...");

await migrate(db, { migrationsFolder: "./drizzle" });

console.log("✓ Migrations complete!");

await client.end();
