insert into storage.buckets (id, name, public)
values ('pantri', 'pantri', true)
on conflict (id) do nothing;

do $$ begin
  create policy "public read storage" on storage.objects for select to anon using (bucket_id = 'pantri');
exception when duplicate_object then null;
end $$;

do $$ begin
  create policy "anon upload" on storage.objects for insert to anon with check (bucket_id = 'pantri');
exception when duplicate_object then null;
end $$;

do $$ begin
  create policy "anon update" on storage.objects for update to anon using (bucket_id = 'pantri');
exception when duplicate_object then null;
end $$;
