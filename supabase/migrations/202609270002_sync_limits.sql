-- Limits on how fast and how much one account can write, and room for looked-up sizes.
--
-- Nothing stopped a script holding a valid session from calling write_pack thousands of times a
-- minute, or from creating packs without end — each one up to 32 MB. That is somebody else's
-- storage bill and database load. The app itself syncs a changed pack once, so these limits are
-- far above anything a person does and only ever meet a loop.
begin;

-- One row per account: the start of its current one-minute window and how many syncs it has
-- made in it. No policies, so nobody can read or write it except the trigger below.
create table public.sync_rate (
  user_id      uuid primary key references auth.users(id) on delete cascade,
  window_start timestamptz not null,
  writes       integer not null,
  last_xact    text not null
);
alter table public.sync_rate enable row level security;
revoke all on public.sync_rate from anon, authenticated;

-- Counts syncs, not rows. One write_pack stamps the pack row once per child row it stores, so
-- counting every update would throttle a single large pack; the transaction id makes all of
-- those one sync. Runs as the definer so it can keep the counter; auth.uid() is still the caller.
create function public.sync_rate_check() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  xact text := pg_current_xact_id()::text;
  r public.sync_rate%rowtype;
  owned integer;
begin
  -- Only people are limited: maintenance run with the service key has no auth.uid().
  if me is null then return new; end if;

  insert into public.sync_rate(user_id, window_start, writes, last_xact)
    values (me, now(), 0, '') on conflict (user_id) do nothing;
  select * into r from public.sync_rate where user_id = me for update;

  if r.last_xact <> xact then
    if r.window_start < now() - interval '1 minute' then
      update public.sync_rate set window_start = now(), writes = 1, last_xact = xact where user_id = me;
    elsif r.writes >= 120 then
      raise exception 'Too many syncs in a minute. Try again shortly.' using errcode = '54000';
    else
      update public.sync_rate set writes = r.writes + 1, last_xact = xact where user_id = me;
    end if;
  end if;

  if tg_op = 'INSERT' then
    select count(*) into owned from public.packs where owner_id = me;
    if owned >= 1000 then
      raise exception 'This account has reached the limit of 1000 packs.' using errcode = '54000';
    end if;
  end if;
  return new;
end $$;
revoke all on function public.sync_rate_check() from public, anon, authenticated;

create trigger packs_sync_rate before insert or update on public.packs
  for each row execute function public.sync_rate_check();

-- A size looked up from a catalogue is stored as SUGGESTED, so the person sees it must be
-- checked. The original checks only knew typed and camera figures, which made the server
-- refuse any pack holding a suggestion.
do $$
declare c record;
begin
  for c in
    select conrelid::regclass as tab, conname from pg_constraint
    where contype = 'c'
      and conrelid in ('public.pack_spaces'::regclass, 'public.pack_openings'::regclass, 'public.pack_items'::regclass)
      and pg_get_constraintdef(oid) like '%measurement_source%'
  loop
    execute format('alter table %s drop constraint %I', c.tab, c.conname);
  end loop;
end $$;
alter table public.pack_spaces add constraint pack_spaces_measurement_source_check
  check (measurement_source in ('TYPED_IN', 'CAMERA_ESTIMATE', 'SUGGESTED'));
alter table public.pack_openings add constraint pack_openings_measurement_source_check
  check (measurement_source in ('TYPED_IN', 'CAMERA_ESTIMATE', 'SUGGESTED'));
alter table public.pack_items add constraint pack_items_measurement_source_check
  check (measurement_source in ('TYPED_IN', 'CAMERA_ESTIMATE', 'SUGGESTED'));

commit;
