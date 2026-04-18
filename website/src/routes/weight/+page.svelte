<script lang="ts">
  import { supabase } from '$lib/supabase';
  import type { WeightEntry } from '$lib/types';
  import { fmt1, prettyDate } from '$lib/format';
  import LineChart from '$lib/components/LineChart.svelte';

  let rows: WeightEntry[] = $state([]);
  let loading = $state(true);
  let error = $state('');

  async function load() {
    loading = true;
    error = '';
    const { data, error: e } = await supabase
      .from('weight')
      .select('date,weight_kg')
      .order('date', { ascending: true });
    if (e) error = e.message;
    else rows = (data ?? []) as WeightEntry[];
    loading = false;
  }

  $effect(() => {
    load();
  });

  const latest = $derived(rows.length ? rows[rows.length - 1] : null);
  const first = $derived(rows.length ? rows[0] : null);
  const delta = $derived(
    latest && first ? latest.weight_kg - first.weight_kg : 0
  );
  const min = $derived(
    rows.length ? Math.min(...rows.map((r) => r.weight_kg)) : 0
  );
  const max = $derived(
    rows.length ? Math.max(...rows.map((r) => r.weight_kg)) : 0
  );

  const avg7 = $derived.by(() => {
    if (rows.length < 2) return rows.map((r) => ({ x: r.date, y: r.weight_kg }));
    const out: { x: string; y: number }[] = [];
    const sorted = [...rows].sort((a, b) => a.date.localeCompare(b.date));
    for (let i = 0; i < sorted.length; i++) {
      const start = Math.max(0, i - 6);
      const slice = sorted.slice(start, i + 1);
      const mean = slice.reduce((a, r) => a + r.weight_kg, 0) / slice.length;
      out.push({ x: sorted[i].date, y: Number(mean.toFixed(2)) });
    }
    return out;
  });

  const series = $derived([
    {
      label: 'Weight (kg)',
      data: rows.map((r) => ({ x: r.date, y: r.weight_kg })),
      color: '#60a5fa',
      fill: true,
      pointRadius: 2
    },
    {
      label: '7-day avg',
      data: avg7,
      color: '#22c55e',
      pointRadius: 0,
      fill: false
    }
  ]);
</script>

<header class="mb-6">
  <h1 class="text-3xl font-bold">Weight</h1>
</header>

{#if loading}
  <div class="py-20 text-center text-[var(--color-muted)]">Loading…</div>
{:else if error}
  <div class="card p-6 text-rose-300">{error}</div>
{:else if !rows.length}
  <div class="card p-8 text-center text-[var(--color-muted)]">No weight entries yet.</div>
{:else}
  <section class="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
    <div class="card p-4">
      <div class="text-xs uppercase tracking-wide text-[var(--color-muted)]">Current</div>
      <div class="text-2xl font-bold">{fmt1(latest!.weight_kg)} kg</div>
      <div class="text-xs text-[var(--color-muted)]">{latest!.date}</div>
    </div>
    <div class="card p-4">
      <div class="text-xs uppercase tracking-wide text-[var(--color-muted)]">Change</div>
      <div
        class="text-2xl font-bold"
        style="color: {delta < 0 ? 'var(--color-brand)' : delta > 0 ? 'var(--color-rose)' : 'inherit'}"
      >
        {delta > 0 ? '+' : ''}{fmt1(delta)} kg
      </div>
      <div class="text-xs text-[var(--color-muted)]">since {first!.date}</div>
    </div>
    <div class="card p-4">
      <div class="text-xs uppercase tracking-wide text-[var(--color-muted)]">Min</div>
      <div class="text-2xl font-bold">{fmt1(min)} kg</div>
    </div>
    <div class="card p-4">
      <div class="text-xs uppercase tracking-wide text-[var(--color-muted)]">Max</div>
      <div class="text-2xl font-bold">{fmt1(max)} kg</div>
    </div>
  </section>

  <section class="card mb-6 p-5">
    <LineChart {series} yLabel="kg" height={320} />
  </section>

  <section>
    <h2 class="mb-3 text-sm font-semibold text-[var(--color-muted)]">Entries</h2>
    <div class="grid gap-2">
      {#each [...rows].reverse() as r}
        <div class="card flex items-center justify-between p-3">
          <span>{prettyDate(r.date)}</span>
          <span class="font-semibold">{fmt1(r.weight_kg)} kg</span>
        </div>
      {/each}
    </div>
  </section>
{/if}
