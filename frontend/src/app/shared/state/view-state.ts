export interface ViewState<T> {
  loading: boolean;
  error: string | null;
  data: T | null;
}

export function initialViewState<T>(): ViewState<T> {
  return { loading: true, error: null, data: null };
}
