# ADR-001: Book Sharing — Authenticated Users Only

**Date:** 2026-03-19
**Status:** Accepted
**Deciders:** Project owner

## Context

BookTower needs a way for users to share books with other users on the same instance. The question is whether shared books should be accessible publicly (no login required) or only to authenticated users.

Competitors like Kavita offer public share links that anyone with the URL can access. However, BookTower is designed as a self-hosted personal library manager, typically running on a home network or small server for a household or friend group.

## Decision

**Shared books require authentication.** A share link (`/shared/book/{token}`) is only accessible to users who are logged in to the BookTower instance. Anonymous visitors are redirected to the login page.

### Design Rules

1. **Sharing is instance-internal** — shared links only work for users with an account on the same server
2. **Any authenticated user can view a shared book** — the viewer does not need to own the book or be in the same library
3. **Shared books show a proper book detail page** — cover, metadata, and download button (not raw JSON)
4. **Share tokens are revocable** — the book owner can unshare at any time, immediately revoking access
5. **Share does not grant write access** — viewers cannot edit metadata, change status, or delete the shared book
6. **The book detail page shows a "Share" button** — with copy-to-clipboard for the share URL

## Consequences

- Users cannot share books with people who don't have an account on the server
- This is intentional — BookTower is not a public content distribution platform
- If public sharing is ever needed, it can be added as a separate opt-in feature with a different endpoint (`/public/book/{token}`) behind an admin flag
- The existing API endpoints (`/public/book/{token}`) will be changed to require authentication and moved to `/shared/book/{token}`

## Alternatives Considered

### Public share links (rejected)
- Pro: Works for anyone with the URL
- Con: Turns a private library into a public file server
- Con: Legal/copyright risk for the server operator
- Con: No way to audit who accessed shared content

### Library-level sharing (deferred)
- Share an entire library with specific users rather than individual books
- More complex: needs user picker UI, permission management
- Can be added later as a complementary feature
