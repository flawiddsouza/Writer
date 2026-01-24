import { Elysia } from "elysia";
import { cors } from "@elysiajs/cors";
import { jwt } from "@elysiajs/jwt";
import { authRoutes } from "./routes/auth";
import { syncRoutes } from "./routes/sync";
import "./db"; // Initialize database connection

const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || "your_jwt_secret_change_this_in_production";

if (JWT_SECRET === "your_jwt_secret_change_this_in_production") {
  console.warn("⚠️  Warning: Using default JWT secret. Set JWT_SECRET in .env file!");
}

const app = new Elysia()
  .use(
    cors({
      origin: process.env.ALLOWED_ORIGINS?.split(",") || true,
      credentials: true,
    })
  )
  .use(
    jwt({
      name: "jwt",
      secret: JWT_SECRET,
    })
  )
  // Health check endpoint
  .get("/health", () => ({
    status: "ok",
    timestamp: new Date().toISOString(),
    runtime: "Bun",
    version: Bun.version,
  }))
  // Register routes
  .use(authRoutes)
  .use(syncRoutes)
  // 404 handler
  .onError(({ code, error, set }) => {
    if (code === "NOT_FOUND") {
      set.status = 404;
      return { error: "Endpoint not found" };
    }

    console.error("Error:", error);
    set.status = 500;
    return { error: error.message || "Internal server error" };
  })
  .listen(PORT);

console.log(`
🚀 Sync Server running!
   URL: http://localhost:${app.server?.port}
   Runtime: Bun ${Bun.version}
   Environment: ${process.env.NODE_ENV || "development"}
`);
