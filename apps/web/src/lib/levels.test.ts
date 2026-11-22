import { isGap } from './levels';
import { adjacentStatus } from './status';

describe('isGap', () => {
  it('shouldFlagGapWhenFrequentAndLevelAtMostAware', () => {
    expect(isGap(30, 1)).toBe(true);
    expect(isGap(58, 0)).toBe(true);
  });
  it('shouldNotFlagGapWhenRareOrSkilled', () => {
    expect(isGap(29, 0)).toBe(false);
    expect(isGap(62, 2)).toBe(false);
  });
});

describe('adjacentStatus', () => {
  it('shouldMoveToNeighbourColumnAndStopAtEdges', () => {
    expect(adjacentStatus('SAVED', 1)).toBe('APPLIED');
    expect(adjacentStatus('SAVED', -1)).toBeNull();
    expect(adjacentStatus('WITHDRAWN', 1)).toBeNull();
  });
});
