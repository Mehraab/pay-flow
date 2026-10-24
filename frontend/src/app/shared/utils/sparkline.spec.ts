import { buildSparkline } from './sparkline';

describe('buildSparkline', () => {
  it('returns an empty result for no points', () => {
    const result = buildSparkline([]);
    expect(result.areaPath).toBe('');
    expect(result.linePath).toBe('');
    expect(result.gridLines).toEqual([]);
    expect(result.xLabels).toEqual([]);
  });

  it('places a single point at the left edge and still produces a valid path', () => {
    const result = buildSparkline([{ date: '2026-01-01', amountMinor: 10000 }]);
    expect(result.linePath).toContain('M48,');
    expect(result.lastPoint.x).toBe(48);
  });

  it('plots the last point at the right edge and scales the max gridline to the peak value', () => {
    const points = [
      { date: '2026-01-01', amountMinor: 1000 },
      { date: '2026-01-02', amountMinor: 5000 },
      { date: '2026-01-03', amountMinor: 3000 },
    ];
    const result = buildSparkline(points);

    expect(result.lastPoint.x).toBe(620); // viewBoxWidth(640) - padRight(20)
    // The top gridline's y should be the smallest y (near the top of the plot area).
    const topGridLine = result.gridLines[result.gridLines.length - 1];
    expect(topGridLine.y).toBeLessThan(result.gridLines[0].y);
  });

  it('collapses duplicate label indices for short series instead of repeating them', () => {
    const points = [
      { date: '2026-01-01', amountMinor: 100 },
      { date: '2026-01-02', amountMinor: 200 },
    ];
    const result = buildSparkline(points);
    expect(result.xLabels.length).toBe(2);
  });

  it('marks the final x label as "(today)"', () => {
    const points = [
      { date: '2026-01-01', amountMinor: 100 },
      { date: '2026-01-02', amountMinor: 200 },
    ];
    const result = buildSparkline(points);
    expect(result.xLabels[result.xLabels.length - 1].label).toContain('(today)');
  });
});
