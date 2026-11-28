import { saveJob, whoAmI } from "./api";
import { normaliseBase, originPattern } from "./settings";

const S = { apiBase: "https://skillsieve.example.com", token: "ssv_test" };
const JOB = {
  text: "x".repeat(250),
  url: "https://www.linkedin.com/jobs/view/1/",
};

function fake(status: number, body: unknown) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fn = (async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return new Response(JSON.stringify(body), { status });
  }) as unknown as typeof fetch;
  return { fn, calls };
}

describe("saveJob", () => {
  it("posts the text with the token and no cookies", async () => {
    const { fn, calls } = fake(201, { id: "j1" });

    expect(await saveJob(S, JOB, fn)).toEqual({ kind: "saved", jobId: "j1" });
    const { url, init } = calls[0]!;
    expect(url).toBe("https://skillsieve.example.com/api/jobs");
    expect(init.credentials).toBe("omit");
    expect((init.headers as Record<string, string>).Authorization).toBe(
      "Bearer ssv_test",
    );
    expect(
      (init.headers as Record<string, string>)["X-Requested-With"],
    ).toBeTruthy();
    expect(JSON.parse(init.body as string)).toEqual({
      rawText: JOB.text,
      sourceUrl: JOB.url,
    });
  });

  it("maps duplicate, bad token and other errors", async () => {
    expect(await saveJob(S, JOB, fake(409, { existingId: "j0" }).fn)).toEqual({
      kind: "duplicate",
      jobId: "j0",
    });
    expect(await saveJob(S, JOB, fake(401, {}).fn)).toEqual({
      kind: "bad_token",
    });
    expect(
      await saveJob(S, JOB, fake(400, { detail: "Paste between…" }).fn),
    ).toEqual({
      kind: "error",
      message: "Paste between…",
    });
  });

  it("does not call the API for too little text", async () => {
    const { fn, calls } = fake(201, {});
    expect(await saveJob(S, { ...JOB, text: "short" }, fn)).toEqual({
      kind: "too_short",
    });
    expect(calls).toHaveLength(0);
  });

  it("reports an unreachable server", async () => {
    const fn = (async () => {
      throw new TypeError("Failed to fetch");
    }) as unknown as typeof fetch;
    expect((await saveJob(S, JOB, fn)).kind).toBe("error");
  });

  it("drops a source URL the API would reject", async () => {
    const { fn, calls } = fake(201, { id: "j1" });
    await saveJob(S, { ...JOB, url: "chrome://newtab" }, fn);
    expect(JSON.parse(calls[0]!.init.body as string)).not.toHaveProperty(
      "sourceUrl",
    );
  });
});

describe("whoAmI", () => {
  it("returns the email or null", async () => {
    expect(await whoAmI(S, fake(200, { email: "me@example.com" }).fn)).toBe(
      "me@example.com",
    );
    expect(await whoAmI(S, fake(401, {}).fn)).toBeNull();
  });
});

describe("settings", () => {
  it("normalises the address and builds a host pattern", () => {
    expect(normaliseBase(" https://skillsieve.example.com/board ")).toBe(
      "https://skillsieve.example.com",
    );
    expect(normaliseBase("http://localhost:8089")).toBe(
      "http://localhost:8089",
    );
    expect(originPattern("http://localhost:8089")).toBe("http://localhost/*");
  });

  it("refuses plain http outside localhost and non-web schemes", () => {
    expect(() => normaliseBase("http://192.168.0.7:8089")).toThrow(/https/);
    expect(() => normaliseBase("ftp://x.example")).toThrow();
  });
});
