import type { Session } from '@supabase/supabase-js';
import { supabase } from './supabase';

type AuthState = {
  session: Session | null;
  loading: boolean;
};

export const auth: AuthState = $state({
  session: null,
  loading: true
});

let initialized = false;

export async function initAuth(): Promise<void> {
  if (initialized) return;
  initialized = true;
  const { data } = await supabase.auth.getSession();
  auth.session = data.session;
  auth.loading = false;
  supabase.auth.onAuthStateChange((_event, session) => {
    auth.session = session;
  });
}

export async function signIn(email: string, password: string) {
  return supabase.auth.signInWithPassword({ email, password });
}

export async function signUp(email: string, password: string) {
  return supabase.auth.signUp({ email, password });
}

export async function signOut() {
  return supabase.auth.signOut();
}
