<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import {
    Chart,
    LineController,
    LineElement,
    PointElement,
    LinearScale,
    TimeScale,
    CategoryScale,
    Tooltip,
    Filler,
    Legend,
    type ChartConfiguration,
    type ChartDataset
  } from 'chart.js';
  import 'chartjs-adapter-date-fns';

  Chart.register(
    LineController,
    LineElement,
    PointElement,
    LinearScale,
    TimeScale,
    CategoryScale,
    Tooltip,
    Filler,
    Legend
  );

  type Series = {
    label: string;
    data: { x: string; y: number }[];
    color: string;
    fill?: boolean;
    dashed?: boolean;
    pointRadius?: number;
  };

  type Props = {
    series: Series[];
    yLabel?: string;
    height?: number;
    goal?: number;
    goalLabel?: string;
  };

  let { series, yLabel = '', height = 260, goal, goalLabel }: Props = $props();

  let canvas: HTMLCanvasElement;
  let chart: Chart | null = null;

  function hexWithAlpha(hex: string, alpha: number): string {
    const h = hex.replace('#', '');
    const v =
      h.length === 3
        ? h
            .split('')
            .map((c) => c + c)
            .join('')
        : h;
    const r = parseInt(v.slice(0, 2), 16);
    const g = parseInt(v.slice(2, 4), 16);
    const b = parseInt(v.slice(4, 6), 16);
    return `rgba(${r},${g},${b},${alpha})`;
  }

  function build(): ChartConfiguration<'line'> {
    const datasets: ChartDataset<'line'>[] = series.map((s) => ({
      label: s.label,
      data: s.data as unknown as ChartDataset<'line'>['data'],
      borderColor: s.color,
      backgroundColor: s.fill ? hexWithAlpha(s.color, 0.18) : 'transparent',
      pointBackgroundColor: s.color,
      pointBorderColor: s.color,
      pointRadius: s.pointRadius ?? 3,
      pointHoverRadius: 5,
      tension: 0.3,
      fill: s.fill ?? false,
      borderDash: s.dashed ? [6, 6] : []
    }));

    if (goal !== undefined) {
      const xs = series
        .flatMap((s) => s.data.map((d) => d.x))
        .sort();
      if (xs.length >= 2) {
        datasets.push({
          label: goalLabel ?? 'Goal',
          data: [
            { x: xs[0], y: goal },
            { x: xs[xs.length - 1], y: goal }
          ] as unknown as ChartDataset<'line'>['data'],
          borderColor: 'rgba(148,163,184,0.7)',
          backgroundColor: 'transparent',
          pointBackgroundColor: 'transparent',
          pointBorderColor: 'transparent',
          pointRadius: 0,
          pointHoverRadius: 0,
          tension: 0,
          fill: false,
          borderDash: [4, 4]
        });
      }
    }

    return {
      type: 'line',
      data: { datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: {
            labels: { color: '#cbd5e1', usePointStyle: true, boxWidth: 8 }
          },
          tooltip: {
            backgroundColor: '#0f172a',
            titleColor: '#f1f5f9',
            bodyColor: '#cbd5e1',
            borderColor: '#334155',
            borderWidth: 1
          }
        },
        scales: {
          x: {
            type: 'time',
            time: { unit: 'day', tooltipFormat: 'MMM d, yyyy' },
            grid: { color: 'rgba(255,255,255,0.04)' },
            ticks: { color: '#64748b', maxRotation: 0, autoSkipPadding: 20 }
          },
          y: {
            title: yLabel
              ? { display: true, text: yLabel, color: '#94a3b8' }
              : undefined,
            grid: { color: 'rgba(255,255,255,0.04)' },
            ticks: { color: '#64748b' }
          }
        }
      }
    };
  }

  onMount(() => {
    chart = new Chart(canvas, build());
  });

  $effect(() => {
    if (!chart) return;
    // re-read props
    const cfg = build();
    chart.data = cfg.data;
    chart.options = cfg.options!;
    chart.update('none');
  });

  onDestroy(() => {
    chart?.destroy();
  });
</script>

<div style="height: {height}px">
  <canvas bind:this={canvas}></canvas>
</div>
