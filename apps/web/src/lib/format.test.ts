import { formatCategory, formatDate, hostOf, isDue, plural, todaySgt } from './format';

const now = new Date('2026-11-22T04:00:00Z'); // 12:00 SGT

describe('formatDate', () => {
  it('shouldShowRelativeDaysWhenUnderOneWeek', () => {
    expect(formatDate('2026-11-19T04:00:00Z', now)).toBe('3d ago');
    expect(formatDate('2026-11-22T01:00:00Z', now)).toBe('today');
  });
  it('shouldShowFullDateWhenOneWeekOrOlder', () => {
    expect(formatDate('2026-11-12T04:00:00Z', now)).toBe('12 Nov 2026');
  });
});

describe('isDue', () => {
  it('shouldBeDueWhenDateIsTodayOrEarlierInSgt', () => {
    expect(todaySgt(new Date('2026-11-21T17:00:00Z'))).toBe('2026-11-22');
    expect(isDue('2026-11-22', now)).toBe(true);
    expect(isDue('2026-11-23', now)).toBe(false);
    expect(isDue(null, now)).toBe(false);
  });
});

it('shouldStripWwwFromHost', () => {
  expect(hostOf('https://www.careers.gov.sg/job/1')).toBe('careers.gov.sg');
});

describe('plural', () => {
  it('uses the singular only for exactly one', () => {
    expect(plural(1, 'job')).toBe('job');
    expect(plural(0, 'job')).toBe('jobs');
    expect(plural(2, 'post')).toBe('posts');
  });
});

describe('formatCategory', () => {
  it('turns API categories into words', () => {
    expect(formatCategory('DEVOPS')).toBe('DevOps');
    expect(formatCategory('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });
});
