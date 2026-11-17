# PharmTrack marketing site

The PharmTrack landing page and legal pages. Static HTML, plain CSS, one
small JavaScript module — no framework, no runtime dependencies.

Built with the Timmly landing design system, rebranded to PharmTrack's teal.

```
web/
├── index.html              the landing page
├── privacy.html            privacy policy
├── terms.html              terms of service
├── eula.html               end user license agreement
├── support.html            help and bug reports
├── delete-account.html     how to delete your local data
├── assets/
│   ├── css/                base + shell + sections (the design system)
│   ├── js/landing.js       the motion module
│   ├── fonts/              9 OFL webfont files
│   └── img/                app screenshots, brand mark, OG image
├── public/_redirects       Netlify rewrites
└── tools/                  the build pipeline (see below)
```

## Commands

| Command | What it does |
|---|---|
| `npm run dev` | Vite dev server on the source pages |
| `npm run build` | Regenerates the pages, then builds `dist/` |
| `npm run build:page` | Regenerates `index.html` and the stylesheets only |
| `npm run build:legal` | Regenerates the five prose pages only |
| `npm run preview` | Serves the built `dist/` |
| `npm run check` | Pre-ship check on the source |
| `npm run check:dist` | The same check on the built output |

## How the page is built

The design system is never edited in place. `tools/design-system/` holds a
pristine copy of the upstream CSS, motion module and page skeleton, and
`tools/build-page.py` regenerates `assets/css`, `assets/js/landing.js` and
`index.html` from it on every run.

That script owns three things:

1. **The rebrand.** The upstream palette is warm; PharmTrack's is teal. The
   token values come from `ios/Sources/App/Theme.swift`, so the page and the
   app are the same brand. Edit the `REBRAND` and `HTML_REBRAND` tables there,
   not the stylesheets.
2. **The copy.** Every placeholder token in the skeleton is filled from the
   `TOKENS` table.
3. **The sample data.** The mockups carry literal strings — the upcoming
   event cards, the dose timeline, the lock screen, the insight cards. Those
   are rewritten in the `LITERALS` table. Each entry must match exactly once,
   so the build fails loudly if the skeleton moves underneath it.

`tools/build-legal.py` does the same for the five prose pages; the legal copy
is carried over verbatim from the pages the old React site shipped.

`tools/check-page.mjs` is the pre-ship gate: it fails on a broken local
reference, a missing `url()` target, an unfilled placeholder, leftover
reference-brand copy, or a missing device-mockup rule.

## Screenshots

`tools/screenshots/` holds the four app screenshots at 1170 × 2532 (3× the
390 × 844 device frame the mockups use). They were captured from the 1.0 iOS
build running in the simulator, seeded with realistic data. The build renders
the 400 / 800 / 1170 webp derivatives into `assets/img/`.

To re-shoot them: build and run the iOS app, seed `medical_data.json` into
the simulator's app container, walk the four screens and capture with
`xcrun simctl io <udid> screenshot`.

## Deploying

`npm run build` writes `dist/`. Netlify publishes that directory
(`.github/workflows/deploy-netlify.yml`). The extensionless routes
(`/privacy`, `/terms`, `/eula`, `/support`, `/delete-account`) are mapped in
`public/_redirects`.
