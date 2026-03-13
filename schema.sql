-- Pantri Supabase schema
-- Run this in the Supabase SQL editor

create table days (
  date text primary key,
  entries jsonb not null default '[]'::jsonb,
  day_totals jsonb not null default '{}'::jsonb,
  raw_text text not null default ''
);

create table weight (
  date text primary key,
  weight_kg real not null
);

-- RLS: anyone with the anon key can read, service_role key for writes
alter table days enable row level security;
alter table weight enable row level security;

create policy "public read" on days for select to anon using (true);
create policy "public read" on weight for select to anon using (true);

-- Storage bucket for foods.json sync
insert into storage.buckets (id, name, public) values ('pantri', 'pantri', true);

create policy "public read" on storage.objects for select to anon using (bucket_id = 'pantri');
create policy "anon upload" on storage.objects for insert to anon with check (bucket_id = 'pantri');
create policy "anon update" on storage.objects for update to anon using (bucket_id = 'pantri');
