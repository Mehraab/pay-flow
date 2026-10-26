import { buildPageWindow } from './pagination';

describe('buildPageWindow', () => {
  it('returns an empty window when there are no pages', () => {
    expect(buildPageWindow(0, 0)).toEqual([]);
  });

  it('returns a single page item when there is only one page', () => {
    expect(buildPageWindow(0, 1)).toEqual([{ kind: 'page', page: 0 }]);
  });

  it('renders every page with no ellipsis when the total is small', () => {
    expect(buildPageWindow(2, 5)).toEqual([
      { kind: 'page', page: 0 },
      { kind: 'page', page: 1 },
      { kind: 'page', page: 2 },
      { kind: 'page', page: 3 },
      { kind: 'page', page: 4 },
    ]);
  });

  it('collapses both gaps into a single ellipsis each when current page is in the middle of a large range', () => {
    expect(buildPageWindow(10, 20)).toEqual([
      { kind: 'page', page: 0 },
      { kind: 'ellipsis' },
      { kind: 'page', page: 9 },
      { kind: 'page', page: 10 },
      { kind: 'page', page: 11 },
      { kind: 'ellipsis' },
      { kind: 'page', page: 19 },
    ]);
  });

  it('only shows a trailing ellipsis when current page is near the start', () => {
    expect(buildPageWindow(0, 20)).toEqual([
      { kind: 'page', page: 0 },
      { kind: 'page', page: 1 },
      { kind: 'ellipsis' },
      { kind: 'page', page: 19 },
    ]);
  });

  it('only shows a leading ellipsis when current page is near the end', () => {
    expect(buildPageWindow(19, 20)).toEqual([
      { kind: 'page', page: 0 },
      { kind: 'ellipsis' },
      { kind: 'page', page: 18 },
      { kind: 'page', page: 19 },
    ]);
  });

  it('always includes first and last even when currentPage is far from both', () => {
    const result = buildPageWindow(50, 100);
    expect(result[0]).toEqual({ kind: 'page', page: 0 });
    expect(result[result.length - 1]).toEqual({ kind: 'page', page: 99 });
  });
});
