<script lang="ts">
  import { page } from '$app/state';
  import { supabase } from '$lib/supabase';
  import type { Day } from '$lib/types';
  import { fmtEur, prettyDate } from '$lib/format';
  import EntryCard from '$lib/components/EntryCard.svelte';
  import { ChevronLeft } from 'lucide-svelte';

  let day = $state<Day | null>(null);
  let loading = $state(true);
  let error = $state('');

  const date = $derived(page.params.date ?? '');

  async function load() {
    if (!date) return;
    loading = true;
    error = '';
    const { data, error: e } = await supabase
      .from('days')
      .select('date,entries,day_totals,raw_text')
      .eq('date', date)
      .maybeSingle<Day>();
    if (e) error = e.message;
    else day = data;
    loading = false;
  }

  $effect(() => {
    void date;
    load();
  });
</script>

<a
  href="/history"
  class="mb-4 inline-flex items-center gap-1 text-sm text-[var(--color-muted)] hover:text-white"
>
  <ChevronLeft size={16} /> History
</a>

<header class="mb-6">
  <h1 class="text-3xl font-bold">{prettyDate(date)}</h1>
</header>

{#if loading}
  <div class="py-20 text-center text-[var(--color-muted)]">Loading…</div>
{:else if error}
  <div class="card p-6 text-rose-300">{error}</div>
{:else if !day}
  <div class="card p-8 text-center text-[var(--color-muted)]">
    No data for this day.
  </div>
{:else}
  <div
    class="card mb-6 flex flex-wrap items-center justify-around gap-6 p-5"
    style="background: linear-gradient(135deg, rgba(34,197,94,0.18), rgba(96,165,250,0.10))"
  >
    <div class="text-center">
      <div class="text-3xl font-bold">{Math.round(day.day_totals.kcal)}</div>
      <div class="text-xs text-[var(--color-muted)]">kcal</div>
    </div>
    <div class="text-center">
      <div class="text-3xl font-bold" style="color: var(--color-blue)">
        {day.day_totals.protein_g.toFixed(0)}
      </div>
      <div class="text-xs text-[var(--color-muted)]">protein</div>
    </div>
    <div class="text-center">
      <div class="text-3xl font-bold" style="color: var(--color-amber)">
        {day.day_totals.carbs_g.toFixed(0)}
      </div>
      <div class="text-xs text-[var(--color-muted)]">carbs</div>
    </div>
    <div class="text-center">
      <div class="text-3xl font-bold" style="color: var(--color-rose)">
        {day.day_totals.fat_g.toFixed(0)}
      </div>
      <div class="text-xs text-[var(--color-muted)]">fat</div>
    </div>
    <div class="text-center">
      <div class="text-3xl font-bold" style="color: var(--color-cyan)">
        {fmtEur(day.day_totals.cost_eur)}
      </div>
      <div class="text-xs text-[var(--color-muted)]">spent</div>
    </div>
  </div>

  <div class="grid gap-3">
    {#each day.entries as entry}
      <EntryCard {entry} />
    {/each}
  </div>

  {#if day.raw_text}
    <section class="mt-8">
      <h2 class="mb-2 text-sm font-semibold text-[var(--color-muted)]">Raw</h2>
      <pre
        class="card overflow-x-auto p-4 text-xs text-[var(--color-muted)]">{day.raw_text}</pre>
    </section>
  {/if}
{/if}
