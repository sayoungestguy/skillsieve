import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '@/api/client';
import { Logo } from '@/components/ui/Logo';
import { useLogin, useMe } from '../hooks';
import { loginSchema, type LoginForm } from '../schema';

/** Only allow same-app paths as the post-login target (no open redirects). */
export function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
}

function errorText(e: unknown): string {
  if (e instanceof ApiError && e.status === 401) return 'Email or password is wrong.';
  if (e instanceof ApiError && e.status === 429)
    return 'Too many attempts. Wait a minute and try again.';
  return 'Could not sign in. Try again.';
}

export function LoginPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const next = safeNext(params.get('next'));
  const { data: me } = useMe();
  const login = useLogin();
  const { register, handleSubmit, formState } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  if (me) return <Navigate to={next} replace />;

  const onSubmit = handleSubmit(async (values) => {
    try {
      await login.mutateAsync(values);
      navigate(next, { replace: true });
    } catch {
      // Shown from login.error below.
    }
  });

  const { errors } = formState;
  return (
    <main className="flex min-h-screen items-center justify-center bg-bg p-4">
      <div className="flex w-full max-w-[400px] flex-col gap-6">
        <div className="flex items-center justify-center gap-2.5 text-ink">
          <Logo size={40} />
          <span className="text-2xl font-bold tracking-tight">SkillSieve</span>
        </div>
        <form className="panel flex flex-col gap-4" onSubmit={onSubmit} noValidate>
          <div>
            <h1 className="text-xl">Sign in</h1>
            <p className="mt-1 text-sm text-muted">Sift job posts. Find your gaps.</p>
          </div>
          {login.isError && (
            <p
              role="alert"
              className="rounded-chip border border-danger bg-red-50 px-3 py-2 text-sm text-red-800"
            >
              {errorText(login.error)}
            </p>
          )}
          <div>
            <label className="label" htmlFor="login-email">
              Email
            </label>
            <input
              id="login-email"
              className="field"
              type="email"
              autoComplete="username"
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? 'login-email-err' : undefined}
              {...register('email')}
            />
            {errors.email && (
              <p id="login-email-err" className="mt-1.5 text-sm text-danger">
                {errors.email.message}
              </p>
            )}
          </div>
          <div>
            <label className="label" htmlFor="login-password">
              Password
            </label>
            <input
              id="login-password"
              className="field"
              type="password"
              autoComplete="current-password"
              aria-invalid={!!errors.password}
              aria-describedby={errors.password ? 'login-password-err' : undefined}
              {...register('password')}
            />
            {errors.password && (
              <p id="login-password-err" className="mt-1.5 text-sm text-danger">
                {errors.password.message}
              </p>
            )}
          </div>
          <button type="submit" className="btn btn-primary w-full" disabled={login.isPending}>
            {login.isPending && <span className="spinner" aria-hidden="true" />}
            {login.isPending ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </main>
  );
}
