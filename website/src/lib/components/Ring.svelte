<script lang="ts">
  type Props = {
    value: number;
    goal: number;
    unit?: string;
    label?: string;
    size?: number;
  };
  let { value, goal, unit = 'kcal', label = 'left', size = 240 }: Props = $props();

  const r = $derived(size / 2 - 14);
  const circ = $derived(2 * Math.PI * r);
  const pct = $derived(goal > 0 ? Math.min(value / goal, 1.5) : 0);
  const offset = $derived(circ * (1 - Math.min(pct, 1)));
  const over = $derived(value > goal);
  const remaining = $derived(goal - value);
</script>

<div class="relative" style="width: {size}px; height: {size}px;">
  <svg viewBox="0 0 {size} {size}" class="-rotate-90">
    <defs>
      <linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="var(--color-brand)" />
        <stop offset="1" stop-color="var(--color-blue)" />
      </linearGradient>
    </defs>
    <circle
      cx={size / 2}
      cy={size / 2}
      {r}
      fill="none"
      stroke="rgba(255,255,255,0.06)"
      stroke-width="14"
    />
    <circle
      cx={size / 2}
      cy={size / 2}
      {r}
      fill="none"
      stroke={over ? 'var(--color-rose)' : 'url(#ringGrad)'}
      stroke-width="14"
      stroke-linecap="round"
      stroke-dasharray={circ}
      stroke-dashoffset={offset}
      style="transition: stroke-dashoffset .8s ease"
    />
  </svg>
  <div class="absolute inset-0 flex flex-col items-center justify-center">
    <div
      class="text-5xl font-bold"
      style="color: {over ? 'var(--color-rose)' : 'inherit'}"
    >
      {over ? `+${Math.round(-remaining)}` : Math.round(remaining)}
    </div>
    <div class="text-sm text-[var(--color-muted)]">{unit} {label}</div>
    <div class="mt-1 text-xs text-[var(--color-muted)]">
      {Math.round(value)} / {goal}
    </div>
  </div>
</div>
