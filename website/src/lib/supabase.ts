import { createClient, type Session } from '@supabase/supabase-js';
import { browser } from '$app/environment';
import { env } from '$env/dynamic/public';

// Shared "rawbangerer" Supabase project — same one used by the Android app,
// the Python CLI (via RAWBANGERER_SUPABASE_URL / _KEY), and other apps like
// taily. Publishable keys are safe to ship to clients; RLS is the boundary.
const SUPABASE_URL =
  env.PUBLIC_SUPABASE_URL || 'https://qeksokwmqqvwmybitvxm.supabase.co';
const SUPABASE_ANON_KEY =
  env.PUBLIC_SUPABASE_ANON_KEY ||
  'sb_publishable_UeHtG_1E1ZrolSS6ld0lzQ_iFccAmtc';

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: {
    persistSession: browser,
    autoRefreshToken: browser,
    detectSessionInUrl: browser
  }
});

export type { Session };
