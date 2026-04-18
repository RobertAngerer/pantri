<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { auth, initAuth, signOut } from '$lib/auth.svelte';
  import {
    Home,
    CalendarDays,
    LineChart,
    Scale,
    Package,
    LogOut
  } from 'lucide-svelte';

  let { children } = $props();

  onMount(() => {
    initAuth();
  });

  const nav = [
    { href: '/', label: 'Dashboard', icon: Home },
    { href: '/history', label: 'History', icon: CalendarDays },
    { href: '/stats', label: 'Stats', icon: LineChart },
    { href: '/weight', label: 'Weight', icon: Scale },
    { href: '/pantry', label: 'Pantry', icon: Package }
  ];

  const isLogin = $derived(page.url.pathname === '/login');

  $effect(() => {
    if (auth.loading) return;
    if (!auth.session && !isLogin) goto('/login', { replaceState: true });
    if (auth.session && isLogin) goto('/', { replaceState: true });
  });

  async function handleSignOut() {
    await signOut();
    goto('/login', { replaceState: true });
  }
</script>

{#if auth.loading}
  <div class="grid min-h-screen place-items-center text-zinc-500">Loading…</div>
{:else if isLogin || !auth.session}
  {@render children()}
{:else}
  <div class="min-h-screen lg:grid lg:grid-cols-[240px_1fr]">
    <aside
      class="sticky top-0 z-10 hidden h-screen flex-col border-r border-[var(--color-border)] bg-[color-mix(in_oklab,var(--color-surface)_70%,transparent)] p-5 backdrop-blur lg:flex"
    >
      <div class="mb-8 flex items-center gap-2">
        <div
          class="grid h-9 w-9 place-items-center rounded-xl"
          style="background: linear-gradient(135deg, var(--color-brand), var(--color-blue))"
        >
          <span class="text-base font-bold text-[#05160c]">P</span>
        </div>
        <div>
          <div class="text-sm font-semibold">Pantri</div>
          <div class="text-xs text-[var(--color-muted)]">Nutrition & pantry</div>
        </div>
      </div>

      <nav class="flex flex-1 flex-col gap-1">
        {#each nav as item}
          {@const active =
            item.href === '/'
              ? page.url.pathname === '/'
              : page.url.pathname.startsWith(item.href)}
          <a
            href={item.href}
            class="flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors"
            class:bg-[var(--color-surface-2)]={active}
            class:text-white={active}
            class:text-[var(--color-muted)]={!active}
          >
            <item.icon size={18} />
            {item.label}
          </a>
        {/each}
      </nav>

      <div class="mt-4 border-t border-[var(--color-border)] pt-4">
        <div class="mb-2 truncate text-xs text-[var(--color-muted)]">
          {auth.session?.user?.email ?? ''}
        </div>
        <button class="btn btn-ghost w-full justify-start" onclick={handleSignOut}>
          <LogOut size={16} /> Sign out
        </button>
      </div>
    </aside>

    <nav
      class="sticky top-0 z-20 flex items-center justify-around border-b border-[var(--color-border)] bg-[color-mix(in_oklab,var(--color-surface)_85%,transparent)] px-2 py-2 backdrop-blur lg:hidden"
    >
      {#each nav as item}
        {@const active =
          item.href === '/'
            ? page.url.pathname === '/'
            : page.url.pathname.startsWith(item.href)}
        <a
          href={item.href}
          class="flex flex-col items-center gap-1 px-3 py-1 text-xs"
          class:text-[var(--color-brand)]={active}
          class:text-[var(--color-muted)]={!active}
        >
          <item.icon size={20} />
          {item.label}
        </a>
      {/each}
    </nav>

    <main class="mx-auto w-full max-w-6xl p-4 md:p-8">
      {@render children()}
    </main>
  </div>
{/if}
