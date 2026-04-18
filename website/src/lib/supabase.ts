import { createClient, type Session } from '@supabase/supabase-js';
import { browser } from '$app/environment';
import { env } from '$env/dynamic/public';

const SUPABASE_URL =
  env.PUBLIC_SUPABASE_URL || 'https://lhvzpkaekbxkkbnebwqb.supabase.co';
const SUPABASE_ANON_KEY =
  env.PUBLIC_SUPABASE_ANON_KEY ||
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imxodnpwa2Fla2J4a2tibmVid3FiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzMyMTg2MDIsImV4cCI6MjA4ODc5NDYwMn0.bNCHRLnSsYh0kxOmo-oFLvm-0Ib80eHJxSQVHgr1gOA';

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: {
    persistSession: browser,
    autoRefreshToken: browser,
    detectSessionInUrl: browser
  }
});

export type { Session };
