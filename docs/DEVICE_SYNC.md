# Device Sync Guide

BookTower supports syncing books and reading progress with Kobo e-readers, KOReader, and OPDS catalog clients.

> [!CAUTION]
> **Device sync features are experimental.** The protocols are implemented based on
> specifications and reverse-engineering, but have **not been tested with real hardware**
> (physical Kobo e-readers, KOReader on actual devices, etc.). The API endpoints exist
> and pass integration tests against mocked clients, but real-world behavior may differ.
> Please report issues if you encounter problems with actual devices.

## Kobo Sync

> **Status: Experimental** — implemented from protocol analysis, not tested with physical Kobo hardware.

BookTower implements the Kobo sync protocol, allowing Kobo e-readers to download books and sync reading progress directly from your server.

### Setup

1. **Register a device** in BookTower:
   - Go to **Settings > Devices > Kobo** and click **Add Device**.
   - Give the device a name (e.g., "My Kobo Clara").
   - BookTower generates a unique device token.

2. **Configure the Kobo** to point at your server. On the Kobo, you need to redirect the sync endpoint to BookTower. The setup URL is:

   ```
   https://your-booktower-url/kobo/{device-token}/v1/initialization
   ```

   The device token is shown in the BookTower UI after registration.

3. **Sync** the Kobo. When the Kobo performs a library sync, it contacts BookTower instead of the Kobo store, and your books appear on the device.

### How It Works

- **Library sync** (`POST /kobo/{token}/v1/library/sync`) — returns books added or updated since the last sync. The response uses epoch-millisecond timestamps for delta sync tokens.
- **Full snapshot** (`GET /kobo/{token}/v1/library/snapshot`) — returns the complete library in one request (used for initial sync).
- **Reading state** (`PUT /kobo/{token}/v1/library/{bookId}/reading-state`) — the Kobo pushes progress updates (percent read, CFI location) back to BookTower.

### KEPUB Support

If you enable KEPUB conversion in **Settings > Devices > Kobo**, BookTower serves EPUB files as `.kepub.epub` with the `application/x-kobo-epub+zip` content type. Kobo firmware recognizes this format and enables its enhanced reading UI (better typography, page-turn animations, reading stats).

### Managing Devices

- **List devices**: `GET /api/kobo/devices` (JWT required)
- **Delete device**: `DELETE /api/kobo/devices/{token}` (JWT required)

## KOReader Sync (kosync)

> **Status: Experimental** — implements the kosync spec, not tested with KOReader on real devices.

BookTower implements the [kosync protocol](https://github.com/koreader/koreader/wiki/Progress-sync), enabling KOReader to sync reading progress across devices.

### Setup

1. **Register a device** in BookTower:
   - Go to **Settings > Devices > KOReader** and click **Add Device**.
   - BookTower generates a device token.

2. **Configure KOReader** to use BookTower as its sync server:
   - Open KOReader, go to **Tools > Progress sync**.
   - Set the **Custom sync server** URL to:
     ```
     https://your-booktower-url/koreader/{device-token}
     ```
   - KOReader identifies books by their content hash (MD5), so the same book on different devices will automatically sync.

### How It Works

- **Push progress** (`PUT /koreader/{token}/syncs/progress`) — KOReader sends the current reading position, including a progress string and percentage.
- **Get progress** (`GET /koreader/{token}/syncs/progress/{document}`) — KOReader requests the last-known position for a document. The `document` parameter is the file's MD5 hash. BookTower looks up the book by `file_hash` in its database.

The response format follows the kosync specification:

```json
{
  "document": "abc123...",
  "progress": "42",
  "percentage": 0.42,
  "device": "booktower",
  "device_id": "booktower",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

### Managing Devices

- **List devices**: `GET /api/koreader/devices` (JWT required)
- **Delete device**: `DELETE /api/koreader/devices/{token}` (JWT required)

## OPDS Catalog

> **Status: Experimental** — basic catalog and download work in integration tests, but not verified with all listed client apps.

BookTower exposes an [OPDS 1.2](https://specs.opds.io/opds-1.2) catalog for downloading books to any OPDS-compatible reader app.

### Catalog URL

```
https://your-booktower-url/opds/catalog
```

### Authentication

OPDS endpoints use **HTTP Basic Auth** with your BookTower username and password. JWT cookies are not used. Alternatively, you can authenticate with a **Bearer API token** in the `Authorization` header.

You can also configure separate OPDS-specific credentials in **Settings > API Tokens** if you prefer not to use your main account password.

### Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /opds/catalog` | Navigation feed listing all libraries |
| `GET /opds/catalog/{libraryId}` | Acquisition feed listing books in a library |
| `GET /opds/books/{id}/file` | Download a book file |
| `GET /opds/books/{id}/chapters/{trackIndex}` | Stream an audiobook chapter |

### Supported Clients

The OPDS catalog works with any OPDS 1.2 compatible app:

- **KOReader** — built-in OPDS browser (Settings > OPDS catalog)
- **Librera Reader** (Android) — Add OPDS feed in Network tab
- **Moon+ Reader** (Android) — Net Library > Add OPDS catalog
- **Kybook 3** (iOS) — Add OPDS feed
- **Aldiko Next** (Android/iOS) — Catalogs > Add catalog
- **Thorium Reader** (Desktop) — Catalogs > Add OPDS feed
- **Calibre** — Get Books > Add OPDS catalog

### Audiobook Streaming

For audiobook entries, the OPDS feed includes individual chapter links with appropriate MIME types (`audio/mpeg`, `audio/mp4`, `audio/ogg`, `audio/flac`). Clients that support streaming can play chapters directly.

