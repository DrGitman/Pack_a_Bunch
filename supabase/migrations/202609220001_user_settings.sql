-- One row per account holding the measuring preferences, so a new phone starts correct.
-- Deliberately not part of the pack tables: these are about the person, not about a pack.
begin;

create table public.user_settings (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  unit       text not null check (unit in ('CENTIMETRES', 'INCHES')),
  habit      text check (habit in ('MOVING_HOUSE', 'LOADING_A_CAR', 'STORAGE')),
  updated_at timestamptz not null default now()
);

alter table public.user_settings enable row level security;

-- Each person reads and writes their own row, and nobody else's.
create policy user_settings_select on public.user_settings
  for select to authenticated using ((select auth.uid()) = user_id);
create policy user_settings_insert on public.user_settings
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy user_settings_update on public.user_settings
  for update to authenticated using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy user_settings_delete on public.user_settings
  for delete to authenticated using ((select auth.uid()) = user_id);

commit;
