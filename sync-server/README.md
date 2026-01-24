# Writer Sync Server

Modern, high-performance sync server built with cutting-edge technologies:

- 🚀 **[Bun](https://bun.sh)** - Lightning-fast JavaScript runtime
- ⚡ **[Elysia](https://elysiajs.com)** - Type-safe, ergonomic web framework
- 🗄️ **[Drizzle ORM](https://orm.drizzle.team)** - Type-safe ORM with excellent TypeScript support
- 🎨 **[Biome](https://biomejs.dev)** - Fast, all-in-one toolchain for linting and formatting
- 📘 **TypeScript** - Full type safety throughout

## Features

- ✅ JWT authentication with bcrypt password hashing
- ✅ Bidirectional sync with conflict detection
- ✅ Delta updates (only changed data syncs)
- ✅ Type-safe API with automatic validation
- ✅ Zero-knowledge encryption support
- ✅ Modern SQL queries with Drizzle ORM
- ✅ Auto-generated types from database schema
- ✅ Fast development with hot-reload

## Prerequisites

- [Bun](https://bun.sh) v1.0+
- PostgreSQL 14+

## Quick Start

### 1. Install Bun

```bash
# Windows (PowerShell)
powershell -c "irm bun.sh/install.ps1 | iex"

# macOS/Linux
curl -fsSL https://bun.sh/install | bash
```

### 2. Install Dependencies

```bash
cd sync-server
bun install
```

### 3. Configure Environment

```bash
cp .env.example .env
```

Edit `.env`:

```env
# Server
PORT=3000
NODE_ENV=development

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=writer_sync
DB_USER=writer_user
DB_PASSWORD=your_secure_password

# JWT
JWT_SECRET=your_jwt_secret_here
JWT_EXPIRATION=7d

# CORS
ALLOWED_ORIGINS=http://localhost:3000,https://yourdomain.com
```

**Generate JWT Secret:**
```bash
openssl rand -base64 64
```

### 4. Set Up Database

```bash
# Create PostgreSQL database
psql -U postgres
CREATE DATABASE writer_sync;
CREATE USER writer_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE writer_sync TO writer_user;
\q

# Generate migrations
bun db:generate

# Run migrations
bun db:migrate
```

### 5. Start Development Server

```bash
bun dev
```

Server will start on http://localhost:3000 with hot-reload enabled!

## Development

### Available Scripts

```bash
bun dev          # Start dev server with hot-reload
bun start        # Start production server
bun db:generate  # Generate Drizzle migrations from schema
bun db:migrate   # Apply migrations to database
bun db:studio    # Open Drizzle Studio (database GUI)
bun lint         # Check code with Biome
bun lint:fix     # Fix linting issues automatically
bun format       # Format code with Biome
bun typecheck    # Type check TypeScript
```

### Database Migrations

When you modify `src/db/schema.ts`:

```bash
# 1. Generate migration files
bun db:generate

# 2. Review migrations in drizzle/ folder

# 3. Apply migrations
bun db:migrate
```

### Database GUI

Drizzle Studio provides a web-based database browser:

```bash
bun db:studio
```

Opens at https://local.drizzle.studio

## API Documentation

### Base URL
```
http://localhost:3000
```

### Health Check

**GET /health**

```bash
curl http://localhost:3000/health
```

Response:
```json
{
  "status": "ok",
  "timestamp": "2024-01-22T10:00:00Z",
  "runtime": "Bun",
  "version": "1.1.33"
}
```

### Authentication

#### Register

**POST /auth/register**

```bash
curl -X POST http://localhost:3000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "securepassword123"
  }'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com"
}
```

#### Login

**POST /auth/login**

Same request/response as register.

### Sync

All sync endpoints require `Authorization: Bearer <token>` header.

#### Get Changes

**GET /sync/changes?since=2024-01-01T00:00:00Z**

```bash
curl http://localhost:3000/sync/changes?since=2024-01-01T00:00:00Z \
  -H "Authorization: Bearer YOUR_TOKEN"
```

Response:
```json
{
  "entries": [
    {
      "id": "uuid",
      "title": "Note Title",
      "body": "content",
      "is_encrypted": false,
      "category_id": null,
      "created_at": "2024-01-22T10:00:00Z",
      "updated_at": "2024-01-22T11:00:00Z",
      "deleted_at": null
    }
  ],
  "categories": [...],
  "current_timestamp": "2024-01-22T12:00:00Z"
}
```

#### Push Changes

**POST /sync/push**

```bash
curl -X POST http://localhost:3000/sync/push \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "entries": [
      {
        "local_id": 1,
        "server_id": null,
        "title": "My Note",
        "body": "Content",
        "is_encrypted": false,
        "category_id": null,
        "created_at": "2024-01-22T10:00:00Z",
        "updated_at": "2024-01-22T11:00:00Z",
        "is_deleted": false
      }
    ],
    "categories": []
  }'
```

Response:
```json
{
  "entries": [
    {
      "local_id": 1,
      "server_id": "new-uuid",
      "status": "success"
    }
  ],
  "categories": []
}
```

Conflict response:
```json
{
  "local_id": 1,
  "server_id": "uuid",
  "status": "conflict",
  "conflict_data": { ... }
}
```

## Project Structure

```
sync-server/
├── src/
│   ├── db/
│   │   ├── schema.ts         # Drizzle schema definitions
│   │   ├── index.ts          # Database connection
│   │   └── migrate.ts        # Migration runner
│   ├── routes/
│   │   ├── auth.ts           # Authentication endpoints
│   │   └── sync.ts           # Sync endpoints
│   └── index.ts              # Main server file
├── drizzle/                  # Generated migrations
├── biome.json                # Biome config
├── drizzle.config.ts         # Drizzle Kit config
├── tsconfig.json             # TypeScript config
├── package.json
└── README.md
```

## Deployment

### Option 1: VPS (DigitalOcean, Linode, AWS)

```bash
# Install Bun on server
curl -fsSL https://bun.sh/install | bash

# Clone and setup
git clone your-repo
cd sync-server
bun install
cp .env.example .env
# Edit .env with production values

# Run migrations
bun db:migrate

# Install PM2 (cross-platform process manager)
bun add -g pm2

# Start with PM2
pm2 start src/index.ts --name writer-sync --interpreter bun
pm2 save
pm2 startup
```

**Alternative: systemd (Linux only)**

```bash
sudo nano /etc/systemd/system/writer-sync.service
```

```ini
[Unit]
Description=Writer Sync Server
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/var/www/writer-sync
ExecStart=/home/user/.bun/bin/bun src/index.ts
Restart=always
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable writer-sync
sudo systemctl start writer-sync
```

### Option 2: Docker

```dockerfile
FROM oven/bun:1

WORKDIR /app

COPY package.json bun.lockb ./
RUN bun install --frozen-lockfile

COPY . .

EXPOSE 3000

CMD ["bun", "start"]
```

```bash
docker build -t writer-sync .
docker run -p 3000:3000 --env-file .env writer-sync
```

### Option 3: Railway/Fly.io

These platforms support Bun natively. Just push your code!

**Railway:**
```bash
railway login
railway init
railway up
```

**Fly.io:**
```bash
fly launch
fly deploy
```

## Security

- ✅ Passwords hashed with bcrypt (cost factor 10)
- ✅ JWT tokens with configurable expiration
- ✅ CORS protection
- ✅ SQL injection prevention (parameterized queries via Drizzle)
- ✅ Type validation on all endpoints
- ✅ Zero-knowledge encryption support

**Production checklist:**
- [ ] Set strong JWT_SECRET (64+ random characters)
- [ ] Use HTTPS (Let's Encrypt)
- [ ] Set ALLOWED_ORIGINS to your domains only
- [ ] Use strong database password
- [ ] Enable PostgreSQL SSL
- [ ] Set up rate limiting (add plugin)
- [ ] Configure firewall

## Troubleshooting

### "Cannot connect to database"

```bash
# Check PostgreSQL is running
sudo systemctl status postgresql

# Test connection
psql -U writer_user -d writer_sync -h localhost

# Check .env credentials
cat .env
```

### "JWT secret not set"

Set `JWT_SECRET` in `.env` file:
```bash
openssl rand -base64 64 >> .env
```

### "Migration failed"

```bash
# Drop and recreate database
psql -U postgres
DROP DATABASE writer_sync;
CREATE DATABASE writer_sync;
\q

# Re-run migrations
bun db:migrate
```

### "Port already in use"

```bash
# Find process on port 3000
lsof -ti:3000

# Kill it
kill -9 $(lsof -ti:3000)

# Or change PORT in .env
echo "PORT=3001" >> .env
```

## Contributing

1. Make changes to code
2. Run `bun lint:fix` and `bun format`
3. Run `bun typecheck` to verify types
4. Test locally with `bun dev`
5. Submit PR

## License

Same as Writer app (MIT)

---

Built with ❤️ using modern web technologies
