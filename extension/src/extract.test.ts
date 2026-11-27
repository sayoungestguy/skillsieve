import { extractJob } from "./extract";

const LONG =
  "You will build Java and Spring Boot services on Kubernetes with PostgreSQL. ".repeat(
    5,
  );

function page(html: string): Document {
  document.body.innerHTML = html;
  window.getSelection()?.removeAllRanges();
  return document;
}

describe("extractJob", () => {
  it("reads title, company and description on LinkedIn", () => {
    const doc = page(`
      <h1 class="job-details-jobs-unified-top-card__job-title">Backend Engineer</h1>
      <div class="job-details-jobs-unified-top-card__company-name"> Northwind Labs </div>
      <div id="job-details"><p>${LONG}</p></div>
      <aside>People also viewed: Frontend Engineer at Contoso</aside>`);

    const job = extractJob(
      doc,
      "https://www.linkedin.com/jobs/view/4012345678/",
    );

    expect(job.site).toBe("linkedin");
    expect(job.title).toBe("Backend Engineer");
    expect(job.company).toBe("Northwind Labs");
    expect(
      job.text.startsWith(
        "Backend Engineer\n\nCompany: Northwind Labs\n\nYou will build",
      ),
    ).toBe(true);
    expect(job.text).not.toContain("People also viewed");
  });

  it("reads MyCareersFuture", () => {
    const doc = page(`
      <h1 data-testid="job-details-info-job-title">Platform Engineer</h1>
      <p data-testid="company-hire-info">GovTech</p>
      <div id="job_description">${LONG}</div>`);

    const job = extractJob(
      doc,
      "https://www.mycareersfuture.gov.sg/job/engineering/platform-1",
    );

    expect(job.site).toBe("mycareersfuture");
    expect(job.company).toBe("GovTech");
    expect(job.text).toContain("Kubernetes");
  });

  it("prefers the user selection on any site", () => {
    const doc = page(
      `<main><p>Navigation and ads</p><p id="jd">${LONG}</p></main>`,
    );
    const range = doc.createRange();
    range.selectNodeContents(doc.getElementById("jd")!);
    window.getSelection()!.addRange(range);

    const job = extractJob(doc, "https://careers.example.com/jobs/42");

    expect(job.site).toBe("selection");
    expect(job.text).not.toContain("Navigation");
  });

  it("falls back to the main content and caps the length", () => {
    const doc = page(
      `<h1>Data Engineer</h1><main>${"x".repeat(40_000)}</main>`,
    );

    const job = extractJob(doc, "https://jobs.example.org/1");

    expect(job.site).toBe("page");
    expect(job.title).toBe("Data Engineer");
    expect(job.text.length).toBe(30_000);
  });

  it("falls back when LinkedIn markup is not a job page", () => {
    const doc = page(`<main>${LONG}</main>`);

    expect(extractJob(doc, "https://www.linkedin.com/feed/").site).toBe("page");
  });

  it("stays self-contained so chrome.scripting can serialise it", () => {
    const src = extractJob.toString();
    expect(src).not.toMatch(/\b(import|require)\b/);
    // eslint-disable-next-line @typescript-eslint/no-implied-eval
    const rebuilt = new Function(`return (${src})`)() as typeof extractJob;
    expect(
      rebuilt(page(`<main>${LONG}</main>`), "https://a.example/").text,
    ).toContain("Spring");
  });
});
