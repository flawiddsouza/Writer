import { Elysia, t } from "elysia";
import { and, eq, gt } from "drizzle-orm";
import { db } from "../db";
import { syncItems } from "../db/schema";

export const syncRoutes = (app: Elysia) =>
  app.group("/sync", (app) =>
    app
      .use((app) =>
        app.derive(async ({ headers, jwt, set }) => {
          const authHeader = headers.authorization;

          if (!authHeader || !authHeader.startsWith("Bearer ")) {
            set.status = 401;
            throw new Error("Access token required");
          }

          const token = authHeader.substring(7);
          const payload = await jwt.verify(token);

          if (!payload) {
            set.status = 403;
            throw new Error("Invalid or expired token");
          }

          return {
            userId: payload.userId as string,
          };
        })
      )
      // Get changes since timestamp
      .get(
        "/changes",
        async ({ query, userId, set }) => {
          const { since } = query;

          if (!since) {
            set.status = 400;
            return { error: 'Missing "since" query parameter' };
          }

          const sinceDate = new Date(since);

          if (Number.isNaN(sinceDate.getTime())) {
            set.status = 400;
            return { error: 'Invalid date format for "since" parameter' };
          }

          // Fetch all items changed since last sync
          const changedItems = await db.query.syncItems.findMany({
            where: and(eq(syncItems.userId, userId), gt(syncItems.updatedAt, sinceDate)),
            orderBy: (syncItems, { asc }) => [asc(syncItems.updatedAt)],
          });

          // Split items by type for backward compatibility with client
          const entries = changedItems
            .filter((item) => item.itemType === "note")
            .map((item) => ({
              id: item.id,
              item_type: item.itemType,
              encrypted_data: item.encryptedData,
              created_at: item.createdAt.toISOString(),
              updated_at: item.updatedAt.toISOString(),
              deleted_at: item.deletedAt?.toISOString() ?? null,
            }));

          const categories = changedItems
            .filter((item) => item.itemType === "category")
            .map((item) => ({
              id: item.id,
              item_type: item.itemType,
              encrypted_data: item.encryptedData,
              created_at: item.createdAt.toISOString(),
              updated_at: item.updatedAt.toISOString(),
              deleted_at: item.deletedAt?.toISOString() ?? null,
            }));

          return {
            entries,
            categories,
            current_timestamp: new Date().toISOString(),
          };
        },
        {
          query: t.Object({
            since: t.String(),
          }),
        }
      )
      // Push local changes to server
      .post(
        "/push",
        async ({ body, userId }) => {
          const { entries = [], categories: cats = [] } = body;

          const categoryResults = [];
          const entryResults = [];

          // Process categories first (entries may reference them)
          for (const category of cats) {
            try {
              const result = await processItem(userId, "category", category);
              categoryResults.push(result);
            } catch (error) {
              categoryResults.push({
                local_id: category.local_id,
                status: "error",
                error: error instanceof Error ? error.message : "Unknown error",
              });
            }
          }

          // Process entries
          for (const entry of entries) {
            try {
              const result = await processItem(userId, "note", entry);
              entryResults.push(result);
            } catch (error) {
              entryResults.push({
                local_id: entry.local_id,
                status: "error",
                error: error instanceof Error ? error.message : "Unknown error",
              });
            }
          }

          return {
            entries: entryResults,
            categories: categoryResults,
          };
        },
        {
          body: t.Object({
            entries: t.Optional(
              t.Array(
                t.Object({
                  local_id: t.Optional(t.Number()),
                  server_id: t.Optional(t.Nullable(t.String())),
                  encrypted_data: t.String(),
                  created_at: t.String(),
                  updated_at: t.String(),
                  is_deleted: t.Optional(t.Boolean()),
                })
              )
            ),
            categories: t.Optional(
              t.Array(
                t.Object({
                  local_id: t.Optional(t.Number()),
                  server_id: t.Optional(t.Nullable(t.String())),
                  encrypted_data: t.String(),
                  created_at: t.String(),
                  updated_at: t.String(),
                  is_deleted: t.Optional(t.Boolean()),
                })
              )
            ),
          }),
        }
      )
  );

// Generic function to process any item type
async function processItem(
  userId: string,
  itemType: string,
  item: {
    local_id?: number;
    server_id?: string | null;
    encrypted_data: string;
    created_at: string;
    updated_at: string;
    is_deleted?: boolean;
  }
) {
  const { local_id, server_id, encrypted_data, created_at, updated_at, is_deleted } = item;

  // Handle deletion
  if (is_deleted && server_id) {
    await db
      .update(syncItems)
      .set({
        deletedAt: new Date(),
        updatedAt: new Date(updated_at),
      })
      .where(and(eq(syncItems.id, server_id), eq(syncItems.userId, userId)));

    return {
      local_id,
      server_id,
      status: "success",
    };
  }

  // Update existing item
  if (server_id) {
    const existing = await db.query.syncItems.findFirst({
      where: and(eq(syncItems.id, server_id), eq(syncItems.userId, userId)),
    });

    if (existing) {
      const serverUpdatedAt = existing.updatedAt;
      const clientUpdatedAt = new Date(updated_at);

      // Check for conflict
      if (serverUpdatedAt > clientUpdatedAt) {
        return {
          local_id,
          server_id,
          status: "conflict",
          conflict_data: {
            item_type: existing.itemType,
            encrypted_data: existing.encryptedData,
            created_at: existing.createdAt.toISOString(),
            updated_at: existing.updatedAt.toISOString(),
          },
        };
      }

      // No conflict, update
      await db
        .update(syncItems)
        .set({
          encryptedData: encrypted_data,
          updatedAt: clientUpdatedAt,
        })
        .where(eq(syncItems.id, server_id));

      return {
        local_id,
        server_id,
        status: "success",
      };
    }
  }

  // Insert new item
  const [newItem] = await db
    .insert(syncItems)
    .values({
      userId,
      itemType,
      encryptedData: encrypted_data,
      createdAt: new Date(created_at),
      updatedAt: new Date(updated_at),
    })
    .returning({ id: syncItems.id });

  return {
    local_id,
    server_id: newItem.id,
    status: "success",
  };
}
