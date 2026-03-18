create table mealpreps (
  id serial primary key,
  food text not null,
  initial_g real not null,
  remaining_g real not null,
  created text not null,
  active boolean not null default true
);

alter table mealpreps enable row level security;
create policy "public read" on mealpreps for select to anon using (true);
create policy "anon insert" on mealpreps for insert to anon with check (true);
create policy "anon update" on mealpreps for update to anon using (true);
