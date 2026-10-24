export interface DailyVolumeInput {
  date: string;
  amountMinor: number;
}

export interface SparklineGridLine {
  y: number;
  label: string;
}

export interface SparklineXLabel {
  x: number;
  label: string;
}

export interface SparklineResult {
  areaPath: string;
  linePath: string;
  gridLines: SparklineGridLine[];
  xLabels: SparklineXLabel[];
  lastPoint: { x: number; y: number };
  viewBoxWidth: number;
  viewBoxHeight: number;
}

const WIDTH = 640;
const HEIGHT = 200;
const PAD_LEFT = 48;
const PAD_RIGHT = 20;
const PAD_TOP = 20;
const PAD_BOTTOM = 30;
const BASELINE_Y = HEIGHT - PAD_BOTTOM;

const EMPTY_RESULT: SparklineResult = {
  areaPath: '',
  linePath: '',
  gridLines: [],
  xLabels: [],
  lastPoint: { x: 0, y: 0 },
  viewBoxWidth: WIDTH,
  viewBoxHeight: HEIGHT,
};

function niceCeiling(value: number): number {
  // 10000 minor units ($100) — a plausible floor for "no volume yet" that still spaces
  // the gridline labels apart (a tiny fallback like $1 rounds every label to the same
  // compact string, e.g. "$0 $0 $1 $1").
  if (value <= 0) return 10000;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;
  const niceNormalized = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return niceNormalized * magnitude;
}

function formatCompactCurrency(amountMinor: number): string {
  if (amountMinor === 0) return '$0';
  return '$' + new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 0 }).format(amountMinor / 100);
}

function formatShortDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00Z`);
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
}

/** Builds a dependency-free line+area chart from daily volume buckets — no library needed for ~30 points. */
export function buildSparkline(points: DailyVolumeInput[]): SparklineResult {
  if (points.length === 0) {
    return EMPTY_RESULT;
  }

  const maxValue = niceCeiling(Math.max(...points.map((p) => p.amountMinor)));
  const plotWidth = WIDTH - PAD_LEFT - PAD_RIGHT;
  const plotHeight = BASELINE_Y - PAD_TOP;

  const xForIndex = (i: number) =>
    points.length === 1 ? PAD_LEFT : PAD_LEFT + (i / (points.length - 1)) * plotWidth;
  const yForValue = (v: number) => BASELINE_Y - (v / maxValue) * plotHeight;

  const coords = points.map((p, i) => ({ x: xForIndex(i), y: yForValue(p.amountMinor) }));

  const linePath = coords.map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x},${c.y}`).join(' ');
  const areaPath =
    `M${coords[0].x},${BASELINE_Y} ` +
    coords.map((c) => `L${c.x},${c.y}`).join(' ') +
    ` L${coords[coords.length - 1].x},${BASELINE_Y} Z`;

  const gridLines: SparklineGridLine[] = [0, 1 / 3, 2 / 3, 1].map((fraction) => ({
    y: BASELINE_Y - fraction * plotHeight,
    label: formatCompactCurrency(fraction * maxValue),
  }));

  const labelIndices =
    points.length <= 4
      ? points.map((_, i) => i)
      : [0, Math.round((points.length - 1) / 3), Math.round(((points.length - 1) * 2) / 3), points.length - 1];
  const xLabels: SparklineXLabel[] = [...new Set(labelIndices)].map((i) => ({
    x: xForIndex(i),
    label: i === points.length - 1 ? `${formatShortDate(points[i].date)} (today)` : formatShortDate(points[i].date),
  }));

  return {
    areaPath,
    linePath,
    gridLines,
    xLabels,
    lastPoint: coords[coords.length - 1],
    viewBoxWidth: WIDTH,
    viewBoxHeight: HEIGHT,
  };
}
