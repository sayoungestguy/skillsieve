import type { Config } from 'tailwindcss';

/** Tokens from docs/DESIGN.md §4. Tints (slate/indigo/amber…) come from the default palette. */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: '#F8FAFC',
        surface: '#FFFFFF',
        line: '#E2E8F0',
        ink: '#0F172A',
        muted: '#475569',
        accent: { DEFAULT: '#4F46E5', weak: '#EEF2FF' },
        success: '#15803D',
        info: '#2563EB',
        warning: '#B45309',
        danger: '#B91C1C',
        neutral: '#94A3B8',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', '"Segoe UI"', 'Roboto', 'sans-serif'],
      },
      borderRadius: { chip: '6px', card: '10px', modal: '14px' },
      boxShadow: {
        card: '0 1px 2px rgb(15 23 42 / 0.06)',
        modal: '0 10px 30px rgb(15 23 42 / 0.15)',
      },
      maxWidth: { content: '1280px' },
    },
  },
  plugins: [],
} satisfies Config;
