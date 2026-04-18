<script lang="ts">
  type Props = {
    label: string;
    value: number;
    goal?: number;
    unit?: string;
    color?: string;
  };
  let {
    label,
    value,
    goal,
    unit = 'g',
    color = 'var(--color-blue)'
  }: Props = $props();

  const pct = $derived(goal && goal > 0 ? Math.min(value / goal, 1) * 100 : 0);
  const left = $derived(goal ? Math.max(goal - value, 0) : 0);
</script>

<div class="card p-4">
  <div class="mb-1 text-xs uppercase tracking-wide text-[var(--color-muted)]">
    {label}
  </div>
  <div class="flex items-baseline gap-1">
    <div class="text-2xl font-bold" style="color: {color}">{value.toFixed(0)}</div>
    <div class="text-sm text-[var(--color-muted)]">{unit}</div>
  </div>
  {#if goal}
    <div class="mt-3 h-1.5 overflow-hidden rounded-full bg-white/5">
      <div
        class="h-full rounded-full transition-[width] duration-700"
        style="width: {pct}%; background: {color}"
      ></div>
    </div>
    <div class="mt-1 text-xs text-[var(--color-muted)]">{left.toFixed(0)} left</div>
  {/if}
</div>
