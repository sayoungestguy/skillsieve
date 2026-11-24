import { z } from 'zod';

export const loginSchema = z.object({
  email: z.string().trim().email('Enter the email you sign in with.'),
  password: z.string().min(1, 'Enter your password.'),
});

export type LoginForm = z.infer<typeof loginSchema>;
