<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import {
    Chart,
    BarController,
    BarElement,
    LinearScale,
    CategoryScale,
    Tooltip,
    Legend,
    type ChartConfiguration
  } from 'chart.js';

  Chart.register(
    BarController,
    BarElement,
    LinearScale,
    CategoryScale,
    Tooltip,
    Legend
  );

  type Series = { label: string; data: number[]; color: string };

  type Props = {
    labels: string[];
    series: Series[];
    yLabel?: string;
    height?: number;
    stacked?: boolean;
  };

  let { labels, series, yLabel = '', height = 260, stacked = false }: Props =
    $props();

  let canvas: HTMLCanvasElement;
  let chart: Chart | null = null;

  function build(): ChartConfiguration<'bar'> {
    return {
      type: 'bar',
      data: {
        labels,
        datasets: series.map((s) => ({
          label: s.label,
          data: s.data,
          backgroundColor: s.color,
          borderRadius: 4,
          borderSkipped: false
        }))
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
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
            stacked,
            grid: { display: false },
            ticks: { color: '#64748b' }
          },
          y: {
            stacked,
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
    const cfg = build();
    chart.data = cfg.data;
    chart.options = cfg.options!;
    chart.update('none');
  });

  onDestroy(() => chart?.destroy());
</script>

<div style="height: {height}px">
  <canvas bind:this={canvas}></canvas>
</div>
