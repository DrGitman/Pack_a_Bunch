-- Deleting an account now waits 30 days, and signing in again within them stops it.
--
-- The delete-account page promises exactly that: "Deleted within 30 days" and "Signing in again
-- within 30 days stops the deletion". Until now delete_account() removed everything at once,
-- so a mistaken tap, or a phone in the wrong hands, lost every pack for good.
begin;

-- One row per account waiting to be deleted. Nobody reads or writes it directly: only the three
-- functions below, which act for the caller alone.
create table public.account_deletions (
  user_id      uuid primary key references auth.users(id) on delete cascade,
  requested_at timestamptz not null default now()
);
alter table public.account_deletions enable row level security;
revoke all on public.account_deletions from anon, authenticated;

-- "Delete my account": starts the 30 days and says when they end. Asking again restarts them.
create function public.request_account_deletion() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  requested timestamptz;
begin
  if me is null then raise exception 'Not signed in'; end if;
  insert into public.account_deletions(user_id, requested_at) values (me, now())
    on conflict (user_id) do update set requested_at = excluded.requested_at
    returning requested_at into requested;
  return jsonb_build_object('deletes_at', requested + interval '30 days');
end $$;

-- Called by the app after every sign-in: coming back is how a deletion is stopped.
create function public.cancel_account_deletion() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null then raise exception 'Not signed in'; end if;
  delete from public.account_deletions where user_id = me;
  return jsonb_build_object('cancelled', found);
end $$;

-- The daily sweep: every account whose 30 days are up goes, packs, settings and login.
create function public.purge_due_account_deletions() returns integer
language plpgsql security definer set search_path = '' as $$
declare
  gone integer := 0;
  r record;
begin
  for r in
    select user_id from public.account_deletions
    where requested_at <= now() - interval '30 days'
    for update skip locked
  loop
    delete from public.packs where owner_id = r.user_id;
    delete from public.user_settings where user_id = r.user_id;
    delete from auth.users where id = r.user_id;   -- takes its account_deletions row with it
    gone := gone + 1;
  end loop;
  return gone;
end $$;

revoke all on function public.request_account_deletion() from public, anon;
revoke all on function public.cancel_account_deletion() from public, anon;
revoke all on function public.purge_due_account_deletions() from public, anon, authenticated;
grant execute on function public.request_account_deletion() to authenticated;
grant execute on function public.cancel_account_deletion() to authenticated;

-- Run the sweep every night at 03:17 UTC. pg_cron is Supabase's scheduler (Integrations → Cron);
-- where it is not available this step is skipped, and the sweep can be run by hand:
--   select public.purge_due_account_deletions();
do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
    create extension if not exists pg_cron;
    perform cron.schedule('purge-deleted-accounts', '17 3 * * *', 'select public.purge_due_account_deletions()');
  end if;
end $$;

commit;
