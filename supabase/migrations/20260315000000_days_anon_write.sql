-- Allow anon key to insert and update days (for Android app food diary scans)
create policy "anon insert" on days for insert to anon with check (true);
create policy "anon update" on days for update to anon using (true);
