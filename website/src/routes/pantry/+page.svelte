<script lang="ts">
  import { supabase } from '$lib/supabase';
  import type { PantryItem } from '$lib/types';
  import { fmt0 } from '$lib/format';

  type Filter = 'all' | 'out' | 'low' | 'stock';
  type Sort = 'name' | 'amount';

  let items: PantryItem[] = $state([]);
  let loading = $state(true);
  let error = $state('');
  let filter: Filter = $state('all');
  let sort: Sort = $state('name');

  async function load() {
    loading = true;
    error = '';
    const { data, error: e } = await supabase
      .from('pantry')
      .select('food,quantity_g,min_quantity_g,updated_at');
    if (e) error = e.message;
    else items = (data ?? []) as PantryItem[];
    loading = false;
  }

  $effect(() => {
    load();
  });

  function status(it: PantryItem): 'out' | 'low' | 'stock' {
    if (it.quantity_g <= 0) return 'out';
    if (it.quantity_g <= it.min_quantity_g) return 'low';
    return 'stock';
  }

  const filtered = $derived.by(() => {
    let out =
      filter === 'all' ? items.slice() : items.filter((i) => status(i) === filter);
    if (sort === 'name') out.sort((a, b) => a.food.localeCompare(b.food));
    else out.sort((a, b) => b.quantity_g - a.quantity_g);
    return out;
  });

  const counts = $derived({
    all: items.length,
    out: items.filter((i) => status(i) === 'out').length,
    low: items.filter((i) => status(i) === 'low').length,
    stock: items.filter((i) => status(i) === 'stock').length
  });

  function barColor(s: 'out' | 'low' | 'stock') {
    return s === 'out'
      ? 'var(--color-rose)'
      : s === 'low'
        ? 'var(--color-amber)'
        : 'var(--color-brand)';
  }
</script>

<header class="mb-6 flex flex-wrap items-center justify-between gap-3">
  <div>
    <h1 class="text-3xl font-bold">Pantry</h1>
    <p class="text-sm text-[var(--color-muted)]">{items.length} items</p>
  </div>
  <label class="flex items-center gap-2 text-sm text-[var(--color-muted)]">
    Sort
    <select
      class="input !py-1.5 !text-sm"
      bind:value={sort}
    >
      <option value="name">Name</option>
      <option value="amount">Amount</option>
    </select>
  </label>
</header>

<div class="mb-4 flex flex-wrap gap-2">
  {#each [
    { key: 'all', label: `All · ${counts.all}` },
    { key: 'out', label: `Out · ${counts.out}` },
    { key: 'low', label: `Low · ${counts.low}` },
    { key: 'stock', label: `In stock · ${counts.stock}` }
  ] as t}
    <button
      class="chip"
      class:active={filter === t.key}
      onclick={() => (filter = t.key as Filter)}
    >
      {t.label}
    </button>
  {/each}
</div>

{#if loading}
  <div class="py-20 text-center text-[var(--color-muted)]">Loading…</div>
{:else if error}
  <div class="card p-6 text-rose-300">{error}</div>
{:else if !filtered.length}
  <div class="card p-8 text-center text-[var(--color-muted)]">Nothing here.</div>
{:else}
  <div class="grid gap-2 sm:grid-cols-2">
    {#each filtered as it}
      {@const s = status(it)}
      {@const pct = Math.min(
        it.min_quantity_g > 0 ? (it.quantity_g / it.min_quantity_g) * 100 : 0,
        200
      )}
      <div class="card p-4">
        <div class="mb-2 flex items-center justify-between">
          <div class="font-medium capitalize">{it.food.replace(/-/g, ' ')}</div>
          <div class="text-sm font-semibold" style="color: {barColor(s)}">
            {fmt0(it.quantity_g)}g
          </div>
        </div>
        <div class="h-2 overflow-hidden rounded-full bg-white/5">
          <div
            class="h-full rounded-full transition-[width] duration-700"
            style="width: {Math.min(pct / 2, 100)}%; background: {barColor(s)}"
          ></div>
        </div>
        <div class="mt-1.5 flex justify-between text-xs text-[var(--color-muted)]">
          <span>min {fmt0(it.min_quantity_g)}g</span>
          <span class="capitalize">{s}</span>
        </div>
      </div>
    {/each}
  </div>
{/if}
