import { Icon } from '@/components/ui/Icon';
import { PageHeader } from '@/components/ui/PageHeader';

/** Settings screen. Account, demo mode and export are wired up once core-api has the endpoints. */
export function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" />
      <div className="flex max-w-[760px] flex-col gap-6">
        <section className="panel flex flex-col gap-4" aria-labelledby="acc">
          <h2 id="acc" className="text-lg">
            Account
          </h2>
          <div className="grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-4">
            <div>
              <label className="label" htmlFor="un">
                Email
              </label>
              <input id="un" className="field" type="email" autoComplete="username" />
            </div>
            <div>
              <label className="label" htmlFor="pw">
                New password
              </label>
              <input
                id="pw"
                className="field"
                type="password"
                autoComplete="new-password"
                aria-describedby="pw-h"
              />
              <p id="pw-h" className="mt-1.5 text-xs text-muted">
                At least 12 characters.
              </p>
            </div>
          </div>
          <div>
            <button type="button" className="btn btn-primary">
              Save changes
            </button>
          </div>
        </section>
        <section className="panel flex flex-wrap items-center gap-4" aria-labelledby="dm">
          <div className="flex-[1_1_320px]">
            <h2 id="dm" className="text-lg">
              Public demo mode
            </h2>
            <p className="mt-1 text-sm text-muted">
              Visitors see sample data only and cannot change anything. Your real jobs stay private.
            </p>
          </div>
          <label className="inline-flex min-h-11 cursor-pointer items-center gap-3 text-sm font-semibold">
            <input className="peer sr-only" type="checkbox" role="switch" />
            <span
              aria-hidden="true"
              className="relative inline-block h-7 w-12 flex-none rounded-full bg-slate-300 after:absolute after:left-[3px] after:top-[3px] after:h-[22px] after:w-[22px] after:rounded-full after:bg-white after:transition-all peer-checked:bg-accent peer-checked:after:left-[23px] peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-accent motion-reduce:after:transition-none"
            />
            Demo mode
          </label>
        </section>
        <section className="panel flex flex-col gap-3" aria-labelledby="ex">
          <h2 id="ex" className="text-lg">
            Export data
          </h2>
          <p className="m-0 text-sm text-muted">
            Download all your jobs, statuses, notes and skill levels.
          </p>
          <div className="flex flex-wrap gap-3">
            <button type="button" className="btn btn-secondary">
              <Icon name="download" size={18} />
              Export JSON
            </button>
            <button type="button" className="btn btn-secondary">
              <Icon name="download" size={18} />
              Export CSV
            </button>
          </div>
        </section>
        <section
          className="panel flex flex-wrap items-center gap-4 border-red-200"
          aria-labelledby="dz"
        >
          <div className="flex-[1_1_320px]">
            <h2 id="dz" className="text-lg text-danger">
              Delete all data
            </h2>
            <p className="mt-1 text-sm text-muted">
              This removes every job, note and skill level. You cannot undo this.
            </p>
          </div>
          <button type="button" className="btn btn-danger">
            Delete all data
          </button>
        </section>
      </div>
    </>
  );
}
