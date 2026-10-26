export type PageWindowItem = { kind: 'page'; page: number } | { kind: 'ellipsis' };

/**
 * Builds the set of page buttons to render for a 0-indexed page/totalPages pair —
 * always includes the first and last page plus currentPage ± 1, collapsing any larger
 * gap into a single ellipsis marker rather than rendering every page in between.
 */
export function buildPageWindow(currentPage: number, totalPages: number): PageWindowItem[] {
  if (totalPages <= 0) return [];

  const included = new Set<number>();
  included.add(0);
  included.add(totalPages - 1);
  for (const page of [currentPage - 1, currentPage, currentPage + 1]) {
    if (page >= 0 && page < totalPages) {
      included.add(page);
    }
  }

  const sorted = [...included].sort((a, b) => a - b);
  const items: PageWindowItem[] = [];
  let previous: number | null = null;
  for (const page of sorted) {
    if (previous !== null && page - previous > 1) {
      items.push({ kind: 'ellipsis' });
    }
    items.push({ kind: 'page', page });
    previous = page;
  }
  return items;
}
