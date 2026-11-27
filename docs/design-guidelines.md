# Design Guidelines

Design system extracted from the reference website **https://novomatic-tech.com/pl/** (NOVOMATIC Technologies Poland) on 2026-11-25. Values are computed styles at a 1280px viewport. The site is built with MUI (Material UI). The machine-readable source of truth is [`assets/design-tokens.json`](../assets/design-tokens.json).

> **Brand rights:** The logo, favicon and brand identity belong to NOVOMATIC. Verify usage rights before using them in the app or redistributing them.

## Assets

| Asset | Path | Notes |
|---|---|---|
| Design tokens | [`../assets/design-tokens.json`](../assets/design-tokens.json) | Colors, typography, spacing, radii, components |
| Homepage screenshot (desktop) | [`../assets/homepage.png`](../assets/homepage.png) | Full page, 1280×6281, no overlays. Visual reference for QA |
| Homepage screenshot (mobile, viewport) | [`../assets/homepage-mobile.png`](../assets/homepage-mobile.png) | 390×844 (iPhone 14 size), first screen |
| Homepage screenshot (mobile, full page) | [`../assets/homepage-mobile-full.png`](../assets/homepage-mobile-full.png) | 390×9296, full page |
| Logo | [`../assets/logo.webp`](../assets/logo.webp) | WebP 178×45 with alpha, the variant for dark backgrounds used in the header |
| Favicon | [`../assets/favicon.ico`](../assets/favicon.ico) | ICO, 6 sizes up to 256×256 |
| Fonts | Google Fonts (not downloaded) | Montserrat, see Typography |

## Colors

| Token | Hex | Usage |
|---|---|---|
| `brand.primary` | `#001D4C` | Navy. Headings, primary text, secondary buttons, dark sections (job offers, footer) |
| `brand.primaryDark` | `#091E43` | Deepest navy for hero gradient areas |
| `brand.header` | `#0D244C` | Header bar background |
| `brand.accent` | `#FF9700` | Orange. Primary CTA buttons, highlighted words ("since 1998", "NOVOMATIC"), active nav link, text links, hero display word |
| `brand.info` | `#308FC1` | Blue icon accent in illustrated icons |
| `brand.error` | `#D32F2F` | *Not seen on the site.* MUI default, used for validation errors |
| `brand.success` | `#2E7D32` | *Not seen on the site.* MUI default |
| `background.default` | `#FFFFFF` | Page and card background |
| `background.light` | `#F4F7FC` | Alternating sections ("Co robimy"), card footers |
| `background.lighter` | `#E8EDF6` | Subtle fills and decorative shapes |
| `text.secondary` | `#60708B` | Body and description text |
| `text.onDark` | `#FFFFFF` | Text on navy backgrounds |
| `border.default` | `#BDBDBD` | Outlined chips and dividers |

**Rule of thumb:** navy carries the structure, orange is for action and emphasis, and grey-blue is for reading text. Orange is used sparingly, mostly for one CTA per section and one highlighted word per heading.

## Typography

- **Font family:** `"Montserrat", Arial, sans-serif` for the whole site.
- **Source:** Google Fonts, `https://fonts.googleapis.com/css?family=Montserrat:300,400,500,700&subset=latin-ext&display=swap`. The `latin-ext` subset is required for Polish diacritics. The site also renders weights 100, 200 and 600, so load `100;200;300;400;500;600;700` in the app. No self-hosted fonts were downloaded because the site has no custom `@font-face` files of its own. For self-hosting, use the package `@fontsource/montserrat` (SIL Open Font License).

### Weight scale

| Token | Weight | Usage |
|---|---|---|
| thin | 100 | Hero subtitle ("Join the game") |
| extralight | 200 | Section subtitles (h4) |
| light | 300 | Inactive tabs |
| regular | 400 | Body, buttons |
| medium | 500 | Card titles |
| semibold | 600 | Headings, nav, active tab, display word |
| bold | 700 | Rare emphasis |

### Size scale

| Style | Size / weight / line height | Color |
|---|---|---|
| Display (hero word) | 120px / 600 | `#FF9700` |
| Hero subtitle | 48px / 100, letter-spacing 1px | `#FFFFFF` |
| Section title (h3) | 48px / 600 / 56px | `#001D4C` (white on navy) |
| Section subtitle (h4) | 34px / 200 / 42px | `#001D4C` |
| Block heading (h4) | 32px / 600 / 44px | `#001D4C` + orange keyword |
| Tab label | 26px / 300 (active 600) | `#001D4C` |
| Card title | 18px / 500 | `#001D4C` |
| Body | 16px / 400 / 24px | `#60708B` |
| Nav | 15px / 600 | `#FFFFFF` |
| Chip | 13px / 400 | `#001D4C` |
| Caption / footer legal | 10px / 400 | `#60708B` |

Section headers are centered as a pair: a bold title above a thin, lighter subtitle.

## Spacing

The base unit is **8px**, following the MUI spacing scale. Common values are 4, 8, 12, 16, 24, 32, 40, 48, 64 and 80px.

- Section vertical padding is about 80px.
- Content width is about 1140px, centered. Job cards are 368px wide, three per row.
- Button horizontal padding is a generous 40px.
- Card footer padding is 16px.

## Border radius

| Token | Value | Usage |
|---|---|---|
| `none` | 0px | Sections, tabs, full-bleed images |
| `xs` | 3px | Outlined chips (technology tags) |
| `sm` | 4px | Small elements |
| `md` | 8px | **Default.** Buttons, nav items, cards, image frames |
| `lg` | 10px | Occasional containers |
| `xl` | 20px | Large decorative frames (company building image) |
| `circle` | 100% | Icon backgrounds, avatars |

## Shadows

A single elevation is used for cards: `0px 8px 10px 0px rgba(0, 0, 0, 0.1)`.

## Components

### Header
- Full-width bar with background `#0D244C`. The logo sits at the left at 178×45px, and the nav sits at the right.
- A small top utility row holds the language switch (PL / EN) and social icons (LinkedIn, Facebook, Instagram, YouTube) in white at 16px.

### Navigation
- Text links in white at 15px/600 with padding `6px 8px` and radius 8px, without uppercase.
- The active item is orange `#FF9700`.

### Buttons
| Variant | Background | Text | Padding | Radius | Height |
|---|---|---|---|---|---|
| Primary (CTA) | `#FF9700` | `#FFFFFF` 16px/400 | 10px 40px | 8px | 48px |
| Secondary | `#001D4C` | `#FFFFFF` 15px/400 | 10px 40px | 8px | 36px |
| Icon (arrow) | `#001D4C` | `#FFFFFF` | 8px | 8px | 48×48px |
| Text link | transparent | `#FF9700` 16px, often with a `›` chevron | — | — | — |

Buttons do not use uppercase, which overrides the MUI default. There is no border.

### Cards (job offers)
- White, radius 8px, card shadow, 368px wide.
- The title is at the top in 18px navy. Metadata follows in a 10px caption, then outlined chips.
- The footer strip uses background `#F4F7FC` and 16px padding, with a full-width secondary button ("Aplikuj").
- The last card in the row is a call-to-action card with an icon button.

### Chips (tags)
Outlined chips with a 1px `#BDBDBD` border, 3px radius, 32px height, and 13px navy text.

### Tabs
Large text tabs at 26px. Inactive tabs use weight 300 and the active tab uses 600, with a navy underline indicator.

### Feature grid ("Co robimy")
Three columns. Each item has a line icon (navy with blue `#308FC1` accents), an 18px navy title, and a 16px grey-blue description, all centered, on the `#F4F7FC` section background.

### Footer
Navy `#001D4C` background with the white logo at the left, centered nav links (the active one orange), and social icons at the right. Legal links appear below in a small caption.

### Inputs
No form inputs appear on the homepage. Use MUI `TextField` in the outlined variant with an 8px radius, a `#BDBDBD` border, a `#001D4C` focus color, and `#D32F2F` for errors.

## Responsive behavior (mobile, 390px)

Observed in the mobile screenshots:
- The header collapses into a hamburger menu at the left, followed by the logo, with social icons at the right. The reference site lets the language switch overlap the logo at this width. Do not copy that and keep the logo clear.
- Every multi-column grid (feature grid, job cards, "Dowiedz się więcej") stacks into a single centered column.
- Headings stay large and centered. For example, "Building soft, playing hard" is about 40px on mobile. Body text stays at 16px.
- Primary CTA buttons keep their orange style and are centered under their section.

## Logo usage

- `assets/logo.webp` is the **white wordmark for dark backgrounds**: a white "N" badge with NOVOMATIC Technologies Poland. Use it only on navy (`#0D244C` / `#001D4C`) surfaces such as the header and footer.
- Display it at 178×45px in the header and keep clear space of at least 16px around it. The file is low resolution (native 178×45), so do not scale it up because it blurs. Get an SVG or high-resolution official asset for larger use.
- No dark variant for light backgrounds was extracted. Do not recolor the WebP. If one is needed, obtain an official asset.
- Use `assets/favicon.ico` as the app favicon.

## Visual style summary

The brand looks corporate-tech with a gaming spark. Deep navy surfaces and clean white sections give a trustworthy, engineering-first base, and a single vivid orange marks every call to action and keyword. Montserrat sets the voice through contrast between heavy 600 headings and airy 100–200 subtitles. Layouts are generous and centered, with soft 8px corners, one subtle shadow, and photography or illustrated line icons rather than decoration. The tone is confident, modern and recruitment-friendly ("Building soft, playing hard").
