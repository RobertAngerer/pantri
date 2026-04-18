# Pantri web

Read-only dashboard for the Pantri Supabase backend. Mirrors the Android app
(today, history, stats, weight, pantry) with nicer charts and a desktop-friendly
layout.

## Stack

- SvelteKit 2 + Svelte 5 (runes)
- Tailwind CSS v4
- Chart.js
- Supabase JS (auth + data)
- `@sveltejs/adapter-static` — builds a pure static site suitable for Cloudflare
  Pages / any static host.

## Local development

```bash
cd website
npm install
npm run dev
```

The client talks directly to Supabase; no backend required.

## Environment

The Supabase URL and anon key are hard-coded as defaults (matching the Android
app and the existing `static/index.html`). To override, create a `.env.local`:

```
PUBLIC_SUPABASE_URL=https://<project>.supabase.co
PUBLIC_SUPABASE_ANON_KEY=<anon key>
```

## Auth

Users must sign in to view data (Supabase email/password auth). The gate is
purely UX — the anon key already grants the same read access via RLS, so this
is not a security boundary. Create users in the Supabase dashboard or via the
in-app "Sign up" button (email confirmation required).

## Build

```bash
npm run build       # outputs to ./build
npm run preview     # serve the build locally
```

## Deploy to Cloudflare Pages

1. Push the repo to GitHub.
2. In Cloudflare Pages, create a new project connected to the repo.
3. Framework preset: **SvelteKit (static)**.
4. Build command: `cd website && npm install && npm run build`
5. Build output directory: `website/build`
6. (Optional) set `PUBLIC_SUPABASE_URL` and `PUBLIC_SUPABASE_ANON_KEY`
   as environment variables.
