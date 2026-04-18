<script lang="ts">
  import { supabase } from '$lib/supabase';
  import type { Day, Totals } from '$lib/types';
  import { EMPTY_TOTALS, GOALS, goalCarbs } from '$lib/types';
  import { fmtEur, todayISO, prettyDate } from '$lib/format';
  import Ring from '$lib/components/Ring.svelte';
  import MacroCard from '$lib/components/MacroCard.svelte';
  import EntryCard from '$lib/components/EntryCard.svelte';

  let day = $state<Day | null>(null);
  let loading = $state(true);
  let error = $state('');

  const today = todayISO();

  const totals: Totals = $derived(day?.day_totals ?? EMPTY_TOTALS);

  async function load() {
    loading = true;
    error = '';
    const { data, error: e } = await supabase
      .from('days')
      .select('date,entries,day_totals,raw_text')
      .eq('date', today)
      .maybeSingle<Day>();
    if (e) error = e.message;
    else day = data;
    loading = false;
  }

  $effect(() => {
    load();
  });
</script>

<header class="mb-6">
  <div class="text-xs uppercase tracking-wider text-[var(--color-muted)]">
    Today
  </div>
  <h1 class="text-3xl font-bold">{prettyDate(today)}</h1>
</header>

{#if loading}
  <div class="py-20 text-center text-[var(--color-muted)]">Loading…</div>
{:else if error}
  <div class="card p-6 text-rose-300">
    <div class="font-semibold">Could not load data</div>
    <div class="mt-1 text-sm text-[var(--color-muted)]">{error}</div>
    <button class="btn btn-primary mt-4" onclick={load}>Retry</button>
  </div>
{:else}
  <section class="grid grid-cols-1 gap-6 md:grid-cols-[auto_1fr] md:items-center">
    <div class="card flex items-center justify-center p-6">
      <Ring value={totals.kcal} goal={GOALS.kcal} />
    </div>

    <div class="grid grid-cols-2 gap-4 sm:grid-cols-3">
      <MacroCard
        label="Protein"
        value={totals.protein_g}
        goal={GOALS.protein_g}
        color="var(--color-blue)"
      />
      <MacroCard
        label="Carbs"
        value={totals.carbs_g}
        goal={goalCarbs()}
        color="var(--color-amber)"
      />
      <MacroCard
        label="Fat"
        value={totals.fat_g}
        goal={GOALS.fat_g}
        color="var(--color-rose)"
      />
      <div class="card col-span-2 flex items-center justify-between p-4 sm:col-span-3">
        <div>
          <div class="text-xs uppercase tracking-wide text-[var(--color-muted)]">
            Spent today
          </div>
          <div class="text-2xl font-bold" style="color: var(--color-cyan)">
            {fmtEur(totals.cost_eur)}
          </div>
        </div>
        <div class="text-right">
          <div class="text-xs text-[var(--color-muted)]">Entries</div>
          <div class="text-xl font-semibold">{day?.entries?.length ?? 0}</div>
        </div>
      </div>
    </div>
  </section>

  {#if day?.entries?.length}
    <section class="mt-8">
      <h2 class="mb-3 text-lg font-semibold">Meals</h2>
      <div class="grid gap-3">
        {#each day.entries as entry}
          <EntryCard {entry} />
        {/each}
      </div>
    </section>
  {:else}
    <section class="card mt-8 p-8 text-center text-[var(--color-muted)]">
      Nothing logged yet today.
    </section>
  {/if}
{/if}
