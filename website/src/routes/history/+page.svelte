<script lang="ts">
  import { supabase } from '$lib/supabase';
  import type { Day } from '$lib/types';
  import { fmtEur, prettyDate } from '$lib/format';

  type Row = Pick<Day, 'date' | 'day_totals' | 'entries'>;

  let rows: Row[] = $state([]);
  let loading = $state(true);
  let error = $state('');

  async function load() {
    loading = true;
    error = '';
    const { data, error: e } = await supabase
      .from('days')
      .select('date,day_totals,entries')
      .order('date', { ascending: false });
    if (e) error = e.message;
    else rows = (data ?? []) as Row[];
    loading = false;
  }

  $effect(() => {
    load();
  });
</script>

<header class="mb-6">
  <h1 class="text-3xl font-bold">History</h1>
  <p class="text-sm text-[var(--color-muted)]">
    {rows.length} day{rows.length === 1 ? '' : 's'} logged
  </p>
</header>

{#if loading}
  <div class="py-20 text-center text-[var(--color-muted)]">Loading…</div>
{:else if error}
  <div class="card p-6 text-rose-300">{error}</div>
{:else if !rows.length}
  <div class="card p-8 text-center text-[var(--color-muted)]">No days yet.</div>
{:else}
  <div class="grid gap-2">
    {#each rows as r}
      <a
        href={`/history/${r.date}`}
        class="card flex items-center justify-between p-4 transition-colors hover:border-[var(--color-brand)]"
      >
        <div>
          <div class="font-semibold">{prettyDate(r.date)}</div>
          <div class="text-xs text-[var(--color-muted)]">
            {(r.entries ?? []).length} meals
          </div>
        </div>
        <div class="text-right">
          <div class="font-bold" style="color: var(--color-brand)">
            {Math.round(r.day_totals.kcal)} kcal
          </div>
          <div class="text-xs text-[var(--color-muted)]">
            {r.day_totals.protein_g.toFixed(0)}P · {fmtEur(r.day_totals.cost_eur)}
          </div>
        </div>
      </a>
    {/each}
  </div>
{/if}
