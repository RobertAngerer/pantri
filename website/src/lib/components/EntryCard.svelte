<script lang="ts">
  import type { Entry } from '$lib/types';
  import { fmtEur } from '$lib/format';
  let { entry }: { entry: Entry } = $props();
</script>

<div class="card p-4">
  <div class="mb-2 flex items-center justify-between">
    <span class="font-semibold capitalize">{entry.label}</span>
    <span class="text-xs text-[var(--color-muted)]">{entry.timestamp ?? ''}</span>
  </div>
  <div class="divide-y divide-[var(--color-border)]">
    {#each entry.items as item}
      <div class="flex items-center justify-between py-1.5 text-sm">
        <span>
          {item.name}
          <span class="text-[var(--color-muted)]">{item.quantity}</span>
        </span>
        <span class="text-xs text-[var(--color-muted)]">
          {Math.round(item.kcal)} kcal · {item.protein_g.toFixed(0)}P
        </span>
      </div>
    {/each}
  </div>
  <div class="mt-3 flex items-baseline justify-between border-t border-[var(--color-border)] pt-3 text-sm">
    <span class="font-bold">{Math.round(entry.totals.kcal)} kcal</span>
    <span class="text-xs text-[var(--color-muted)]">
      {entry.totals.protein_g.toFixed(1)}P · {entry.totals.carbs_g.toFixed(1)}C ·
      {entry.totals.fat_g.toFixed(1)}F · {fmtEur(entry.totals.cost_eur)}
    </span>
  </div>
</div>
