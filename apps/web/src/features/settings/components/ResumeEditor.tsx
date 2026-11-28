import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '@/api/client';
import { useDeleteResume, useResume, useSaveResume } from '../hooks';

const MAX_CHARS = 20_000;
const dateFmt = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });

/** Paste my resume once. Stored encrypted; used only by the local model, never by Gemini. */
export function ResumeEditor() {
  const { data: resume, isPending } = useResume();
  const save = useSaveResume();
  const remove = useDeleteResume();
  const [text, setText] = useState('');
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);

  useEffect(() => {
    setText(resume?.text ?? '');
  }, [resume]);

  const fail = (err: unknown) =>
    setMessage({
      kind: 'error',
      text: err instanceof ApiError ? (err.problem.detail ?? err.message) : 'Something went wrong.',
    });

  const onSave = async (e: FormEvent) => {
    e.preventDefault();
    setMessage(null);
    try {
      await save.mutateAsync(text);
      setMessage({ kind: 'ok', text: 'Resume saved.' });
    } catch (err) {
      fail(err);
    }
  };

  const onDelete = async () => {
    setMessage(null);
    try {
      await remove.mutateAsync();
      setMessage({ kind: 'ok', text: 'Resume deleted.' });
    } catch (err) {
      fail(err);
    }
  };

  const unchanged = text.trim() === (resume?.text ?? '');
  const tooLong = text.length > MAX_CHARS;

  return (
    <section className="panel flex flex-col gap-4" aria-labelledby="cv">
      <div>
        <h2 id="cv" className="text-lg">
          Resume
        </h2>
        <p className="mt-1 text-sm text-muted">
          Paste your resume as plain text. It is stored encrypted and only a model running on this
          server reads it to suggest changes per job. It is never sent to Gemini.
        </p>
      </div>
      <form className="flex flex-col gap-3" onSubmit={onSave}>
        <label className="label" htmlFor="cv-text">
          Resume text
        </label>
        <textarea
          id="cv-text"
          className="field min-h-60 font-mono text-sm"
          value={text}
          disabled={isPending}
          aria-describedby="cv-h"
          onChange={(e) => setText(e.target.value)}
        />
        <p id="cv-h" className={`m-0 text-xs ${tooLong ? 'text-danger' : 'text-muted'}`}>
          {text.length.toLocaleString()} / {MAX_CHARS.toLocaleString()} characters
          {resume && ` · Last saved ${dateFmt.format(new Date(resume.updatedAt))}`}
        </p>
        <div className="flex flex-wrap gap-3">
          <button
            type="submit"
            className="btn btn-primary"
            disabled={save.isPending || !text.trim() || tooLong || unchanged}
          >
            Save resume
          </button>
          {resume && (
            <button
              type="button"
              className="btn btn-secondary"
              disabled={remove.isPending}
              onClick={onDelete}
            >
              Delete resume
            </button>
          )}
        </div>
      </form>
      {message && (
        <p
          role={message.kind === 'error' ? 'alert' : 'status'}
          className={`m-0 text-sm ${message.kind === 'error' ? 'text-danger' : 'text-muted'}`}
        >
          {message.text}
        </p>
      )}
    </section>
  );
}
