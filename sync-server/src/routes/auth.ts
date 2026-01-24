import { Elysia, t } from "elysia";
import { eq } from "drizzle-orm";
import { db } from "../db";
import { users } from "../db/schema";

const JWT_EXPIRATION = process.env.JWT_EXPIRATION || "7d";

// Email validation regex
const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const authRoutes = (app: Elysia) =>
  app.group("/auth", (app) =>
    app
      // Register new user
      .post(
        "/register",
        async ({ body, jwt, set }) => {
          const { email, password } = body;

          // Validation
          if (!emailRegex.test(email)) {
            set.status = 400;
            return { error: "Invalid email format" };
          }

          if (password.length < 8) {
            set.status = 400;
            return { error: "Password must be at least 8 characters" };
          }

          // Check if user exists
          const existingUser = await db.query.users.findFirst({
            where: eq(users.email, email.toLowerCase()),
          });

          if (existingUser) {
            set.status = 409;
            return { error: "User already exists" };
          }

          // Hash password
          const passwordHash = await Bun.password.hash(password, {
            algorithm: "bcrypt",
            cost: 10,
          });

          // Create user
          const [user] = await db
            .insert(users)
            .values({
              email: email.toLowerCase(),
              passwordHash,
            })
            .returning({ id: users.id, email: users.email });

          // Generate JWT token
          const token = await jwt.sign({
            userId: user.id,
            email: user.email,
            exp: Math.floor(Date.now() / 1000) + parseExpiration(JWT_EXPIRATION),
          });

          set.status = 201;
          return {
            token,
            userId: user.id,
            email: user.email,
          };
        },
        {
          body: t.Object({
            email: t.String(),
            password: t.String(),
          }),
        }
      )
      // Login existing user
      .post(
        "/login",
        async ({ body, jwt, set }) => {
          const { email, password } = body;

          // Find user
          const user = await db.query.users.findFirst({
            where: eq(users.email, email.toLowerCase()),
          });

          if (!user) {
            set.status = 401;
            return { error: "Invalid email or password" };
          }

          // Verify password
          const isValid = await Bun.password.verify(password, user.passwordHash);

          if (!isValid) {
            set.status = 401;
            return { error: "Invalid email or password" };
          }

          // Generate JWT token
          const token = await jwt.sign({
            userId: user.id,
            email: user.email,
            exp: Math.floor(Date.now() / 1000) + parseExpiration(JWT_EXPIRATION),
          });

          return {
            token,
            userId: user.id,
            email: user.email,
          };
        },
        {
          body: t.Object({
            email: t.String(),
            password: t.String(),
          }),
        }
      )
      // Get encrypted master key
      .get(
        "/master-key",
        async ({ headers, set, jwt }) => {
          try {
            const authHeader = headers.authorization;
            if (!authHeader?.startsWith("Bearer ")) {
              set.status = 401;
              return { error: "Unauthorized" };
            }

            const token = authHeader.substring(7);
            const decoded = await jwt.verify(token);

            if (!decoded || typeof decoded !== "object" || !("userId" in decoded)) {
              set.status = 401;
              return { error: "Invalid token" };
            }

            const user = await db.query.users.findFirst({
              where: eq(users.id, decoded.userId as string),
            });

            if (!user) {
              set.status = 404;
              return { error: "User not found" };
            }

            return {
              encryptedMasterKey: user.encryptedMasterKey,
              exists: !!user.encryptedMasterKey,
            };
          } catch (error) {
            set.status = 500;
            return { error: "Failed to get master key" };
          }
        }
      )
      // Upload encrypted master key
      .post(
        "/master-key",
        async ({ headers, body, set, jwt }) => {
          try {
            const authHeader = headers.authorization;
            if (!authHeader?.startsWith("Bearer ")) {
              set.status = 401;
              return { error: "Unauthorized" };
            }

            const token = authHeader.substring(7);
            const decoded = await jwt.verify(token);

            if (!decoded || typeof decoded !== "object" || !("userId" in decoded)) {
              set.status = 401;
              return { error: "Invalid token" };
            }

            const { encryptedMasterKey } = body;

            if (!encryptedMasterKey) {
              set.status = 400;
              return { error: "Encrypted master key required" };
            }

            // Check if master key already exists
            const user = await db.query.users.findFirst({
              where: eq(users.id, decoded.userId as string),
            });

            if (user && user.encryptedMasterKey) {
              set.status = 409;
              return { error: "Master key already exists. Cannot overwrite." };
            }

            await db
              .update(users)
              .set({
                encryptedMasterKey,
                updatedAt: new Date(),
              })
              .where(eq(users.id, decoded.userId as string));

            return { success: true };
          } catch (error) {
            set.status = 500;
            return { error: "Failed to store master key" };
          }
        },
        {
          body: t.Object({
            encryptedMasterKey: t.String(),
          }),
        }
      )
      // Change master key password (re-encrypt master key with new password)
      .post(
        "/change-master-key-password",
        async ({ headers, body, set, jwt }) => {
          try {
            const authHeader = headers.authorization;
            if (!authHeader?.startsWith("Bearer ")) {
              set.status = 401;
              return { error: "Unauthorized" };
            }

            const token = authHeader.substring(7);
            const decoded = await jwt.verify(token);

            if (!decoded || typeof decoded !== "object" || !("userId" in decoded)) {
              set.status = 401;
              return { error: "Invalid token" };
            }

            const { newEncryptedMasterKey } = body;

            if (!newEncryptedMasterKey) {
              set.status = 400;
              return { error: "New encrypted master key required" };
            }

            await db
              .update(users)
              .set({
                encryptedMasterKey: newEncryptedMasterKey,
                updatedAt: new Date(),
              })
              .where(eq(users.id, decoded.userId as string));

            return { success: true };
          } catch (error) {
            set.status = 500;
            return { error: "Failed to change master key password" };
          }
        },
        {
          body: t.Object({
            newEncryptedMasterKey: t.String(),
          }),
        }
      )
  );

// Helper to parse expiration string (e.g., "7d" -> seconds)
function parseExpiration(exp: string): number {
  const match = exp.match(/^(\d+)([smhd])$/);
  if (!match) return 604800; // Default 7 days

  const value = Number.parseInt(match[1]);
  const unit = match[2];

  switch (unit) {
    case "s":
      return value;
    case "m":
      return value * 60;
    case "h":
      return value * 3600;
    case "d":
      return value * 86400;
    default:
      return 604800;
  }
}
