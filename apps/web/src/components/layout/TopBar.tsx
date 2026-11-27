import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Logo } from '@/components/ui/Logo';
import { Icon } from '@/components/ui/Icon';

/** Desktop top bar: global search ("/" to focus) + Add job. */
export function TopBar() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const inputRef = useRef<HTMLInputElement>(null);
  const [q, setQ] = useState('');
  const onSearchPage = pathname === '/search';

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (e.key !== '/' || target.closest('input, textarea, select, [contenteditable]')) return;
      e.preventDefault();
      (document.getElementById('search-q') ?? inputRef.current)?.focus();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    navigate(`/search?q=${encodeURIComponent(q)}`);
  };

  return (
    <header className="flex min-h-[68px] flex-wrap items-center gap-4 border-b border-line bg-surface px-4 py-3 lg:px-6">
      <Link
        to="/"
        className="flex items-center gap-2 text-ink no-underline hover:text-ink lg:hidden"
      >
        <Logo size={32} />
        <span className="text-lg font-bold">SkillSieve</span>
      </Link>
      {!onSearchPage && (
        <form
          role="search"
          onSubmit={submit}
          className="relative hidden max-w-[480px] flex-1 items-center md:flex"
        >
          <label className="sr-only" htmlFor="top-q">
            Search jobs
          </label>
          <span className="absolute left-3 flex text-muted">
            <Icon name="search" size={18} />
          </span>
          <input
            ref={inputRef}
            id="top-q"
            className="field bg-bg pl-10 pr-11"
            type="search"
            placeholder="Search jobs, companies, skills"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <kbd className="absolute right-3" aria-hidden="true">
            /
          </kbd>
        </form>
      )}
      <div className="ml-auto hidden items-center gap-3 md:flex">
        <Link className="btn btn-primary" to="/jobs/new">
          <Icon name="plus" size={18} />
          Add job
        </Link>
      </div>
    </header>
  );
}
