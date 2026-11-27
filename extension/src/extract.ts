/** What the extension reads from a job page. */
export interface ExtractedJob {
  site: "linkedin" | "mycareersfuture" | "selection" | "page";
  title: string | null;
  company: string | null;
  text: string;
  url: string;
}

/**
 * Reads the job post from the page. Runs inside the tab via chrome.scripting.executeScript, which
 * serialises this function, so it must stay self-contained: no imports, no outer helpers.
 *
 * Order: the user's text selection (works on any site), then site-specific selectors, then the
 * page's main content. Only runs when the user clicks the extension (activeTab).
 */
export function extractJob(
  doc: Document = document,
  href: string = location.href,
): ExtractedJob {
  const MAX = 30_000; // core-api limit
  const clean = (s: string | null | undefined): string =>
    (s ?? "")
      .replace(/ /g, " ")
      .replace(/[ \t]+/g, " ")
      .replace(/\n\s*\n\s*/g, "\n\n")
      .trim();
  const textOf = (selectors: string[]): string | null => {
    for (const sel of selectors) {
      const el = doc.querySelector<HTMLElement>(sel);
      const t = clean(el?.innerText ?? el?.textContent);
      if (t) return t;
    }
    return null;
  };
  const header = (
    title: string | null,
    company: string | null,
    body: string,
  ): string =>
    clean(
      [title, company && `Company: ${company}`, body]
        .filter(Boolean)
        .join("\n\n"),
    ).slice(0, MAX);

  const host = (() => {
    try {
      return new URL(href).hostname;
    } catch {
      return "";
    }
  })();

  const selection = clean(doc.defaultView?.getSelection()?.toString());
  if (selection.length >= 200) {
    return {
      site: "selection",
      title: null,
      company: null,
      text: selection.slice(0, MAX),
      url: href,
    };
  }

  if (host.endsWith("linkedin.com")) {
    const title = textOf([
      ".job-details-jobs-unified-top-card__job-title",
      ".jobs-unified-top-card__job-title",
      ".top-card-layout__title",
      "h1",
    ]);
    const company = textOf([
      ".job-details-jobs-unified-top-card__company-name",
      ".jobs-unified-top-card__company-name",
      ".topcard__org-name-link",
    ]);
    const body = textOf([
      "#job-details",
      ".jobs-description__content",
      ".jobs-description-content__text",
      ".show-more-less-html__markup",
      ".description__text",
    ]);
    if (body)
      return {
        site: "linkedin",
        title,
        company,
        text: header(title, company, body),
        url: href,
      };
  }

  if (host.endsWith("mycareersfuture.gov.sg")) {
    const title = textOf(['[data-testid="job-details-info-job-title"]', "h1"]);
    const company = textOf([
      '[data-testid="company-hire-info"]',
      '[data-cy="company-hire-info__company"]',
    ]);
    const body = textOf([
      "#job_description",
      '[data-testid="description-content"]',
      "#description-content",
    ]);
    if (body) {
      return {
        site: "mycareersfuture",
        title,
        company,
        text: header(title, company, body),
        url: href,
      };
    }
  }

  const title = textOf(["h1"]);
  const body = textOf(["main", "article", '[role="main"]', "body"]) ?? "";
  return {
    site: "page",
    title,
    company: null,
    text: body.slice(0, MAX),
    url: href,
  };
}
