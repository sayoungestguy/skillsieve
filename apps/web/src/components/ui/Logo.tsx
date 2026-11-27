interface LogoProps {
  size?: number;
  className?: string;
}

/** SkillSieve brand mark: a sieve holding back skills, one gold offer falls through. Decorative. */
export function Logo({ size = 32, className }: LogoProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <g stroke="#4F46E5" strokeWidth={2} strokeLinecap="round" fill="none">
        <path d="M3 7h18" />
        <path d="M4.5 7a7.5 7.5 0 0 0 15 0" />
      </g>
      <g fill="#4F46E5">
        <circle cx="8.6" cy="9.9" r="1.15" />
        <circle cx="12" cy="9.9" r="1.15" />
        <circle cx="15.4" cy="9.9" r="1.15" />
        <circle cx="10.3" cy="12.7" r="1.15" />
        <circle cx="13.7" cy="12.7" r="1.15" />
      </g>
      <circle cx="12" cy="19.6" r="2.1" fill="#D97706" />
    </svg>
  );
}
