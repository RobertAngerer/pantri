<script lang="ts">
  import { supabase } from '$lib/supabase';
  import type { Day } from '$lib/types';
  import { GOALS } from '$lib/types';
  import { fmtEur, fmt0, fmt1 } from '$lib/format';
  import LineChart from '$lib/components/LineChart.svelte';
  import BarChart from '$lib/components/BarChart.svelte';

  type Row = Pick<Day, 'date' | 'day_totals'>;

  type Range = 'week' | 'month' | 'year';

  let rows: Row[] = $state([]);
  let loading = $state(true);
  let error = $state('');
  let range: Range = $state<Range>('month');

  async function load() {
    loading = true;
    error = '';
    const { data, error: e } = await supabase
      .from('days')
      .select('date,day_totals')
      .order('date', { ascending: true });
    if (e) error = e.message;
    else rows = (data ?? []) as Row[];
    loading = false;
  }

  $effect(() => {
    load();
  });

  const rangeDays = $derived(
    range === 'week' ? 7 : range === 'month' ? 30 : 365
  );

  const filtered = $derived.by(() => {
    if (!rows.length) return [] as Row[];
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - rangeDays);
    const iso = cutoff.toISOString().slice(0, 10);
    return rows.filter((r) => r.date >= iso);
  });

  const avg = $derived.by(() => {
    if (!filtered.length)
      return { kcal: 0, protein: 0, carbs: 0, fat: 0, cost: 0 };
    const s = filtered.reduce(
      (a, r) => ({
        kcal: a.kcal + r.day_totals.kcal,
        protein: a.protein + r.day_totals.protein_g,
        carbs: a.carbs + r.day_totals.carbs_g,
        fat: a.fat + r.day_totals.fat_g,
        cost: a.cost + r.day_totals.cost_eur
      }),
      { kcal: 0, protein: 0, carbs: 0, fat: 0, cost: 0 }
    );
    const n = filtered.length;
    return {
      kcal: s.kcal / n,
      protein: s.protein / n,
      carbs: s.carbs / n,
      fat: s.fat / n,
      cost: s.cost / n
    };
  });

  const totalSpent = $derived(
    filtered.reduce((a, r) => a + r.day_totals.cost_eur, 0)
  );

  const kcalSeries = $derived([
    {
      label: 'kcal',
      data: filtered.map((r) => ({ x: r.date, y: r.day_totals.kcal })),
      color: '#22c55e',
      fill: true
    }
  ]);

  const macroLabels = $derived(filtered.map((r) => r.date.slice(5)));

  const macroSeries = $derived([
    {
      label: 'Protein',
      data: filtered.map((r) => r.day_totals.protein_g),
      color: '#60a5fa'
    },
    {
      label: 'Carbs',
      data: filtered.map((r) => r.day_totals.carbs_g),
      color: '#fbbf24'
    },
    {
      label: 'Fat',
      data: filtered.map((r) => r.day_totals.fat_g),
      color: '#fb7185'
    }
  ]);

  const costSeries = $derived([
    {
      label: '€/day',
      data: filtered.map((r) => ({ x: r.date, y: r.day_totals.cost_eur })),
      color: '#22d3ee',
      fill: true
    }
  ]);

  const monthStart = $derived(() => {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
  });
  const monthRows = $derived(rows.filter((r) => r.date >= monthStart()));
  const monthSpent = $derived(
    monthRows.reduce((a, r) => a + r.day_totals.cost_eur, 0)
  );
  const monthDaysSoFar = $derived(monthRows.length || 1);
  const monthAvgPerDay = $derived(monthSpent / monthDaysSoFar);
  const daysInMonth = $derived.by(() => {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  });
  const monthForecast = $derived(monthAvgPerDay * daysInMonth);
  const budgetPct = $derived(
    Math.min((monthSpent / GOALS.budget_monthly_eur) * 100, 200)
  );
  const budgetColor = $derived(
    monthSpent > GOALS.budget_monthly_eur
      ? 'var(--color-rose)'
      : monthForecast > GOALS.budget_monthly_eur
        ? 'var(--color-amber)'
        : 'var(--color-brand)'
  );
</script>

<header class="mb-6 flex flex-wrap items-center justify-between gap-3">
  <h1 class="text-3xl font-bold">Stats</h1>
  <div class="flex gap-1 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-1">
    {#each ['week', 'month', 'year'] as r}
      <button
        class="rounded-lg px-3 py-1.5 text-sm font-medium capitalize transition-colors"
        class:bg-[var(--color-surface-2)]={range === r}
        class:text-white={range === r}
        class:text-[var(--color-muted)]={range !== r}
        onclick={() => (range = r as Range)}
      >
        {r}
      </button>
    {/each}
  </div>
</header>

{#if loading}
  <div class="py-20 text-center text-[var(--color-muted)]">Loading…</div>
{:else if error}
  <div class="card p-6 text-rose-300">{error}</div>
{:else}
  <section class="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-5">
    <div class="card p-4">
      <div class="text-xs uppercase tracking-wide text-[var(--color-muted)]">Avg kcal</div>
      <div class="text-2xl font-bold">{fmt0(avg.kcal)}</div>
    </div>
    <div class="card p-4">
      <div class="text-xs uppercase tracking-wide text-[var(--color-muted)]">Avg P</div>
      <div class="text-2xl font-bold" style="color: var(--color-blue)">{fmt1(avg.protein)}</div>
    </div>
    <div class="card p-4">
      <div class="text-xs uppercase tracking-wide text-[var(--color-muted)]">Avg C</div>
      <div class="text-2xl font-bold" style="color: var(--color-amber)">{fmt1(avg.carbs)}</div>
    </div>
    <div class="card p-4">
      <div class="text-xs uppercase tracking-wide text-[var(--color-muted)]">Avg F</div>
      <div class="text-2xl font-bold" style="color: var(--color-rose)">{fmt1(avg.fat)}</div>
    </div>
    <div class="card p-4">
      <div class="text-xs uppercase tracking-wide text-[var(--color-muted)]">Spent</div>
      <div class="text-2xl font-bold" style="color: var(--color-cyan)">{fmtEur(totalSpent)}</div>
    </div>
  </section>

  <section class="card mb-6 p-5">
    <div class="mb-4 flex flex-wrap items-end justify-between gap-2">
      <div>
        <div class="text-sm font-semibold">Monthly budget</div>
        <div class="text-xs text-[var(--color-muted)]">
          {fmtEur(monthSpent)} of {fmtEur(GOALS.budget_monthly_eur)} · forecast
          {fmtEur(monthForecast)}
        </div>
      </div>
      <div class="text-right text-xs text-[var(--color-muted)]">
        {fmtEur(monthAvgPerDay)}/day avg
      </div>
    </div>
    <div class="h-3 overflow-hidden rounded-full bg-white/5">
      <div
        class="h-full rounded-full transition-[width] duration-700"
        style="width: {Math.min(budgetPct, 100)}%; background: {budgetColor}"
      ></div>
    </div>
  </section>

  <section class="card mb-6 p-5">
    <div class="mb-3 flex items-center justify-between">
      <h2 class="text-sm font-semibold">Calories / day</h2>
      <span class="text-xs text-[var(--color-muted)]">
        goal {GOALS.kcal}
      </span>
    </div>
    <LineChart series={kcalSeries} goal={GOALS.kcal} goalLabel="Goal" yLabel="kcal" />
  </section>

  <section class="card mb-6 p-5">
    <h2 class="mb-3 text-sm font-semibold">Macros / day</h2>
    <BarChart labels={macroLabels} series={macroSeries} yLabel="g" stacked />
  </section>

  <section class="card p-5">
    <h2 class="mb-3 text-sm font-semibold">Spend / day</h2>
    <LineChart series={costSeries} yLabel="€" />
  </section>
{/if}
