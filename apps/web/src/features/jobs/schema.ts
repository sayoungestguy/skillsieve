import { z } from 'zod';

export const MIN_POST_CHARS = 200;
export const MAX_POST_CHARS = 30_000;

export const addJobSchema = z.object({
  rawText: z
    .string()
    .trim()
    .min(MIN_POST_CHARS, `Paste at least ${MIN_POST_CHARS} characters.`)
    .max(MAX_POST_CHARS, `Keep it under ${MAX_POST_CHARS.toLocaleString()} characters.`),
  sourceUrl: z.union([z.literal(''), z.string().url('Enter a full link, starting with https://')]),
});

export type AddJobForm = z.infer<typeof addJobSchema>;
