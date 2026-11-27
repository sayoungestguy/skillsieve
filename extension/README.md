# SkillSieve Chrome extension

Save the job post in the current tab to SkillSieve in one click (PRD A3, FR-11).

## Install (unpacked)

```bash
cd extension && npm ci && npm run build
```

1. Open `chrome://extensions`, turn on **Developer mode**, click **Load unpacked**, pick `extension/dist`.
2. In SkillSieve, go to **Settings → Browser extension** and create a token.
3. Open the extension's options (right-click the icon → Options). Enter your SkillSieve address
   (https, or `http://localhost:<port>` for local dev) and paste the token. Chrome asks to allow
   that one site.

## Use

Open a job post and click the SkillSieve icon, then **Save to SkillSieve**.

- **LinkedIn** (`/jobs/view/…`) and **MyCareersFuture** (`/job/…`): title, company and description
  are read for you.
- **Any other site:** select the job description first, then click the icon.

## Privacy and permissions

- `activeTab` + `scripting`: the page is read only when you click the icon, and only that tab.
  The extension has no standing access to job sites.
- Host permission: only your SkillSieve address, granted in the options page.
- The token can only save jobs and say who it belongs to. Revoke it anytime in Settings.
- Requests never carry your SkillSieve cookie (`credentials: 'omit'`).

## Develop

`npm test` (Vitest + jsdom), `npm run typecheck`, `npm run format:check`. `extractJob` in
`src/extract.ts` is serialised into the page, so keep it self-contained (a test checks this).
