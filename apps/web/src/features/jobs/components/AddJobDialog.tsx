import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '@/api/client';
import { Icon } from '@/components/ui/Icon';
import { useToast } from '@/components/ui/Toast';
import { useCreateJob } from '../hooks';
import { addJobSchema, MIN_POST_CHARS, type AddJobForm } from '../schema';

export function AddJobDialog() {
  const navigate = useNavigate();
  const toast = useToast();
  const create = useCreateJob();
  const [duplicateId, setDuplicateId] = useState<string | null>(null);
  const { register, handleSubmit, watch, formState } = useForm<AddJobForm>({
    resolver: zodResolver(addJobSchema),
    defaultValues: { rawText: '', sourceUrl: '' },
  });
  const count = watch('rawText').trim().length;
  const ready = count >= MIN_POST_CHARS;
  const close = () => navigate('/');

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && navigate('/');
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [navigate]);

  const onSubmit = handleSubmit(async ({ rawText, sourceUrl }) => {
    setDuplicateId(null);
    try {
      const job = await create.mutateAsync({ rawText, ...(sourceUrl && { sourceUrl }) });
      toast({
        text: 'Saved. Extracting skills…',
        busy: true,
        link: { to: `/jobs/${job.id}`, label: 'View' },
      });
      navigate('/');
    } catch (e) {
      if (e instanceof ApiError && e.status === 409 && e.problem.existingId)
        setDuplicateId(e.problem.existingId);
      else throw e;
    }
  });

  const { ref: textRef, ...textField } = register('rawText');

  return (
    <div className="fixed inset-0 z-40 flex items-start justify-center bg-ink/45 p-0 md:px-4 md:pb-4 md:pt-24">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="add-h"
        className="flex h-full w-full max-w-[640px] flex-col gap-5 overflow-y-auto bg-surface p-6 shadow-modal md:h-auto md:rounded-modal"
      >
        <div className="flex items-center justify-between gap-3">
          <h2 id="add-h" className="text-xl">
            Add a job post
          </h2>
          <button type="button" className="icon-btn" aria-label="Close" onClick={close}>
            <Icon name="close" />
          </button>
        </div>
        <form onSubmit={onSubmit} className="flex flex-col gap-5" noValidate>
          <div>
            <label className="label" htmlFor="jd">
              Paste the job description
            </label>
            <textarea
              id="jd"
              className="field"
              rows={12}
              // eslint-disable-next-line jsx-a11y/no-autofocus -- DESIGN.md §3.2: autofocus for one-paste capture
              autoFocus
              aria-describedby="jd-count"
              aria-invalid={!!formState.errors.rawText && formState.isSubmitted}
              ref={textRef}
              {...textField}
            />
            <p
              id="jd-count"
              aria-live="polite"
              className={`mt-1.5 text-xs ${ready ? 'text-success' : 'text-muted'}`}
            >
              {ready
                ? `${count} characters · ready to save`
                : `${count} / ${MIN_POST_CHARS} characters · paste ${MIN_POST_CHARS - count} more to save`}
            </p>
          </div>
          <div>
            <label className="label" htmlFor="src">
              Source URL <span className="font-normal text-muted">(optional)</span>
            </label>
            <input
              id="src"
              className="field"
              type="url"
              inputMode="url"
              placeholder="https://www.mycareersfuture.gov.sg/job/…"
              aria-describedby={formState.errors.sourceUrl ? 'src-err' : undefined}
              aria-invalid={!!formState.errors.sourceUrl}
              {...register('sourceUrl')}
            />
            {formState.errors.sourceUrl && (
              <p id="src-err" className="mt-1.5 text-xs font-semibold text-danger">
                {formState.errors.sourceUrl.message}
              </p>
            )}
          </div>
          {duplicateId && (
            <div
              role="alert"
              className="flex items-start gap-3 rounded-card border border-amber-200 bg-amber-50 p-4 text-sm"
            >
              <span className="mt-0.5 flex text-warning">
                <Icon name="warning" />
              </span>
              <p className="m-0">
                <span className="font-semibold">You already saved this post.</span>{' '}
                <Link to={`/jobs/${duplicateId}`} className="font-semibold">
                  Open it
                </Link>
              </p>
            </div>
          )}
          {create.isError && !duplicateId && (
            <p role="alert" className="m-0 text-sm font-semibold text-danger">
              Could not save the post. Try again.
            </p>
          )}
          <p className="m-0 text-xs text-muted">
            We find the company, title, location and skills for you. You can edit them after saving.
          </p>
          <div className="flex justify-end gap-3">
            <button type="button" className="btn btn-secondary" onClick={close}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={!ready || create.isPending}>
              {create.isPending && (
                <span className="spinner border-indigo-300 border-t-white" aria-hidden="true" />
              )}
              Save job
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
