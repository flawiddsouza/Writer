import { boolean, pgTable, text, timestamp, uuid } from "drizzle-orm/pg-core";

export const users = pgTable("users", {
  id: uuid("id").primaryKey().defaultRandom(),
  email: text("email").notNull().unique(),
  passwordHash: text("password_hash").notNull(),
  encryptedMasterKey: text("encrypted_master_key"),
  createdAt: timestamp("created_at").defaultNow().notNull(),
  updatedAt: timestamp("updated_at").defaultNow().notNull(),
});

// Generic sync items table - works for any app
// Stores encrypted blobs with minimal metadata
export const syncItems = pgTable("sync_items", {
  id: uuid("id").primaryKey().defaultRandom(),
  userId: uuid("user_id")
    .notNull()
    .references(() => users.id, { onDelete: "cascade" }),
  itemType: text("item_type").notNull(), // "note", "category", "todo", etc.
  encryptedData: text("encrypted_data").notNull(), // Encrypted JSON blob
  createdAt: timestamp("created_at").notNull(),
  updatedAt: timestamp("updated_at").notNull(),
  deletedAt: timestamp("deleted_at"),
});

// Type exports
export type User = typeof users.$inferSelect;
export type NewUser = typeof users.$inferInsert;

export type SyncItem = typeof syncItems.$inferSelect;
export type NewSyncItem = typeof syncItems.$inferInsert;
