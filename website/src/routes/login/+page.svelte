<script lang="ts">
  import { signIn, signUp } from '$lib/auth.svelte';

  let email = $state('');
  let password = $state('');
  let mode: 'signin' | 'signup' = $state('signin');
  let error = $state('');
  let loading = $state(false);
  let info = $state('');

  async function submit(e: Event) {
    e.preventDefault();
    error = '';
    info = '';
    loading = true;
    try {
      const { error: err } =
        mode === 'signin'
          ? await signIn(email, password)
          : await signUp(email, password);
      if (err) error = err.message;
      else if (mode === 'signup')
        info = 'Check your email to confirm the account, then sign in.';
    } finally {
      loading = false;
    }
  }
</script>

<div class="grid min-h-screen place-items-center p-6">
  <div class="card w-full max-w-sm p-6 shadow-2xl">
    <div class="mb-6 flex items-center gap-3">
      <div
        class="grid h-10 w-10 place-items-center rounded-xl"
        style="background: linear-gradient(135deg, var(--color-brand), var(--color-blue))"
      >
        <span class="text-base font-bold text-[#05160c]">P</span>
      </div>
      <div>
        <div class="text-lg font-semibold">Pantri</div>
        <div class="text-xs text-[var(--color-muted)]">
          {mode === 'signin' ? 'Sign in to continue' : 'Create your account'}
        </div>
      </div>
    </div>

    <form onsubmit={submit} class="flex flex-col gap-3">
      <label class="flex flex-col gap-1 text-sm">
        <span class="text-[var(--color-muted)]">Email</span>
        <input
          class="input"
          type="email"
          required
          autocomplete="email"
          bind:value={email}
        />
      </label>
      <label class="flex flex-col gap-1 text-sm">
        <span class="text-[var(--color-muted)]">Password</span>
        <input
          class="input"
          type="password"
          required
          minlength="6"
          autocomplete={mode === 'signin' ? 'current-password' : 'new-password'}
          bind:value={password}
        />
      </label>

      {#if error}
        <div class="rounded-md border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
          {error}
        </div>
      {/if}
      {#if info}
        <div class="rounded-md border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-300">
          {info}
        </div>
      {/if}

      <button class="btn btn-primary mt-2 w-full" disabled={loading}>
        {loading ? 'Please wait…' : mode === 'signin' ? 'Sign in' : 'Create account'}
      </button>
    </form>

    <button
      class="btn btn-ghost mt-3 w-full"
      onclick={() => (mode = mode === 'signin' ? 'signup' : 'signin')}
    >
      {mode === 'signin'
        ? "Don't have an account? Sign up"
        : 'Already have an account? Sign in'}
    </button>
  </div>
</div>
