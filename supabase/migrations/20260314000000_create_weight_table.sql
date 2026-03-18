-- Add missing insert/update RLS policies for weight table
-- (table already exists from schema.sql with only a select policy)
create policy "anon insert" on weight for insert to anon with check (true);
create policy "anon update" on weight for update to anon using (true);
