# Design — SkillSieve (UI / UX)

> Version 0.1 · Last updated 2026-11-22 22:41 SGT · Status: Draft

## 1. Design principles

1. **Fast capture.** Saving a job must take one paste and one tap.
2. **Show what to do next.** Every screen points to an action: follow up, learn a skill, apply.
3. **Mobile first.** Most quick checks happen on a phone. Design at 375 px, then scale up.
4. **Calm and clear.** Light mode, lots of white space, one accent colour, no clutter.
5. **Accessible by default.** WCAG 2.1 AA contrast, full keyboard use, visible focus.

## 2. Information architecture

```
SkillSieve
├── Board            (home)  — kanban by status
├── Add job          (modal / full screen on mobile)
├── Job detail               — post text, extracted skills, status, notes, history
├── Insights                 — skill-gap dashboard
├── My skills                — rate my level per skill
├── Search                   — full-text across posts
└── Settings                 — account, demo mode, export data
```

Navigation:
- **Desktop (≥ 1024 px):** left sidebar (Board, Insights, My skills, Search, Settings) + top bar with "+ Add job".
- **Mobile (< 768 px):** bottom tab bar (Board, Insights, Add ＋, Skills, More). "Add" is the centre button.

## 3. Screens

### 3.1 Board
```
┌───────────────────────────────────────────────────────────────┐
│ SkillSieve        [ Search jobs…            ]   [+ Add job]  │
├──────────┬──────────┬──────────┬──────────┬──────────┬────────┤
│ Saved 12 │Applied 8 │Interview2│ Offer 0  │Rejected 3│Withdr.1│
├──────────┼──────────┼──────────┼──────────┼──────────┼────────┤
│┌────────┐│┌────────┐│          │          │          │        │
││DBS     │││GovTech ││          │          │          │        │
││SWE II  │││Full-   ││          │          │          │        │
││Docker K8│││stack ● ││  ← red dot = follow-up due              │
││3d ago  │││Apply 5d││          │          │          │        │
│└────────┘│└────────┘│          │          │          │        │
└──────────┴──────────┴──────────┴──────────┴──────────┴────────┘
```
- Card: company (bold), title, top 3 skill chips, relative date, follow-up dot.
- Drag-and-drop between columns (desktop). On mobile: columns become horizontal swipe tabs; change status from a card's "⋯" menu.
- Empty column: muted text "Nothing here yet".

### 3.2 Add job
```
┌─────────────────────────────────────────┐
│ Add a job post                      ✕   │
│ Paste the job description               │
│ ┌─────────────────────────────────────┐ │
│ │                                     │ │
│ │  (textarea, 12 rows, autofocus)     │ │
│ └─────────────────────────────────────┘ │
│ Source URL (optional) [______________]  │
│                     [Cancel] [Save job] │
└─────────────────────────────────────────┘
```
- Save disabled until ≥ 200 characters. Counter shown under the textarea.
- After Save: toast "Saved. Extracting skills…" → card appears; chips fill in when extraction finishes.
- Duplicate: inline message "You already saved this post" + "Open it" link.

### 3.3 Job detail
Two columns on desktop, stacked on mobile:
- **Left:** company, title, location, source link, status dropdown, next-action date, notes (autosave), status history timeline.
- **Right:** extracted skills as two groups — **Required** and **Preferred**. Each chip shows my level as a small dot (grey 0, amber 1, blue 2, green 3). Collapsible full post text.
- Extraction states: `Pending` (spinner + "Extracting skills…"), `Failed` (warning + "Retry" button), `Done`.

### 3.4 Insights (skill-gap dashboard)
```
Filters: [Status: All ▾] [Last 90 days ▾]        Based on 34 posts

Skill            Required ███████████ 62%   Me: ● Working
Docker           ██████████░ 58%            Me: ○ Aware     GAP
Kubernetes       ████████░░░ 47%            Me: ○ None      GAP
React            ███████░░░░ 41%            Me: ● Strong
Python           ██████░░░░░ 38%            Me: ○ Aware     GAP
…
[Top gaps] Docker · Kubernetes · Python   → "Add to learning list"
```
- Horizontal bar chart. Bar = % of posts mentioning the skill. Split into required (solid) and preferred (lighter shade of the same hue).
- **Gap** badge rule: frequency ≥ 30% and my level ≤ 1.
- Summary cards on top: Total posts · Applied · Interview rate · Top gap.
- Click a skill → list of posts that mention it.

### 3.5 My skills
- Searchable list of all skills seen in posts, plus taxonomy skills.
- Each row: skill name, category, segmented control 0–3 (None / Aware / Working / Strong).
- Sort: "Most in demand first" (default) or A–Z.

### 3.6 Search
- One search box. Results show company, title, highlighted snippet, status chip.
- Keyboard shortcut: `/` focuses search on desktop.

## 4. Design tokens

### 4.1 Colour (light mode only for R1)

| Token | Hex | Use |
|---|---|---|
| `--bg` | `#F8FAFC` | Page background |
| `--surface` | `#FFFFFF` | Cards, modals |
| `--border` | `#E2E8F0` | Dividers, card borders |
| `--text` | `#0F172A` | Primary text (contrast 17:1 on surface) |
| `--text-muted` | `#475569` | Secondary text (7.5:1) |
| `--accent` | `#4F46E5` | Primary buttons, links, focus ring |
| `--accent-weak` | `#EEF2FF` | Selected states, chip background |
| `--success` | `#15803D` | Offer, level 3 |
| `--info` | `#2563EB` | Interview, level 2 |
| `--warning` | `#B45309` | Gap badge, level 1, follow-up due |
| `--danger` | `#B91C1C` | Rejected, errors, delete |
| `--neutral` | `#94A3B8` | Level 0, withdrawn |

Status colours: Saved `--neutral`, Applied `--accent`, Interview `--info`, Offer `--success`, Rejected `--danger`, Withdrawn `--neutral`.
Never use colour alone — every status also has a text label; every level dot has a tooltip and `aria-label`.

### 4.2 Typography
- Font: **Inter** (fallback: system-ui, -apple-system, Segoe UI, Roboto, sans-serif).
- Scale (rem): 0.75 caption · 0.875 small · 1 body · 1.125 h4 · 1.25 h3 · 1.5 h2 · 1.875 h1.
- Body line height 1.5. Headings 1.25. Weight: 400 body, 600 headings and card company.
- Numbers in dashboards: `font-variant-numeric: tabular-nums`.

### 4.3 Spacing, radius, elevation
- Spacing scale (px): 4, 8, 12, 16, 24, 32, 48.
- Radius: 6 (chips, inputs), 10 (cards), 14 (modals).
- Shadow: cards `0 1px 2px rgb(15 23 42 / 0.06)`; modal `0 10px 30px rgb(15 23 42 / 0.15)`.

### 4.4 Breakpoints
| Name | Min width | Layout |
|---|---|---|
| sm | 0 | Single column, bottom tab bar |
| md | 768 px | Two-column detail, board scrolls horizontally |
| lg | 1024 px | Sidebar nav, full board |
| xl | 1280 px | Max content width 1280 px, centred |

## 5. Components

| Component | Variants / states | Notes |
|---|---|---|
| Button | primary, secondary, ghost, danger · default/hover/focus/disabled/loading | Min touch target 44×44 px |
| Input / Textarea | default, focus, error, disabled | Label always visible (no placeholder-only labels) |
| Skill chip | required, preferred · with level dot | `aria-label="Docker, required, my level Aware"` |
| Status badge | 6 statuses | Colour + text |
| Job card | default, dragging, follow-up due | Whole card is a link; drag handle for keyboard users |
| Kanban column | normal, drop target, empty | Keyboard: select card, `Shift+→` moves to next column |
| Toast | success, error, info | Auto-dismiss 4 s, `role="status"` |
| Level control | 0–3 segmented | Radio group semantics |
| Bar chart row | required/preferred split, gap badge | Values also shown as text |
| Empty state | per screen | Icon + one sentence + primary action |
| Skeleton loader | card, row | Shown after 300 ms delay to avoid flicker |

## 6. Content and tone
- Short, plain words. Sentence case for buttons ("Save job", not "SAVE JOB").
- Errors say what happened and what to do: "Skills could not be extracted. The AI limit was reached. Try again in a few minutes."
- Dates: relative for < 7 days ("3d ago"), else `22 Nov 2026`. Times in SGT.

## 7. Accessibility checklist
- [ ] All text meets 4.5:1 contrast (large text 3:1)
- [ ] Full keyboard path for: add job, change status, rate skill
- [ ] Visible focus ring: 2 px `--accent`, 2 px offset
- [ ] Drag-and-drop has a keyboard and menu alternative
- [ ] Charts have a text/table equivalent
- [ ] `prefers-reduced-motion` turns off card animations
- [ ] Form errors linked with `aria-describedby`

## 8. Release 2 design items
- PWA install (manifest, icons, offline shell).
- Chrome extension popup: detected title/company, "Save to SkillSieve" button, success state.
- Resume tailoring panel in Job detail: suggested bullets with "Copy" buttons.
- Dark mode tokens.
