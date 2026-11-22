import { addJobSchema } from './schema';

const longText = 'x'.repeat(200);

describe('addJobSchema', () => {
  it('shouldRejectTextUnder200Chars', () => {
    expect(addJobSchema.safeParse({ rawText: 'short', sourceUrl: '' }).success).toBe(false);
  });
  it('shouldAcceptEmptyOrValidUrl', () => {
    expect(addJobSchema.safeParse({ rawText: longText, sourceUrl: '' }).success).toBe(true);
    expect(addJobSchema.safeParse({ rawText: longText, sourceUrl: 'https://a.sg/j' }).success).toBe(
      true,
    );
    expect(addJobSchema.safeParse({ rawText: longText, sourceUrl: 'not a url' }).success).toBe(
      false,
    );
  });
});
