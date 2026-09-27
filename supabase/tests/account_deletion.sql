-- Rollback-only checks for the 30-day account deletion. Disposable local cluster only: needs
-- local_auth_fixture.sql, never a Supabase project.
begin;
do $$
declare
  me uuid := '00000000-0000-0000-0000-000000000001';
  other uuid := '00000000-0000-0000-0000-000000000002';
  answer jsonb;
  refused boolean;
begin
  insert into public.packs(id, owner_id, name, client_id) values (gen_random_uuid(), me, 'Mine', 'mine'), (gen_random_uuid(), other, 'Theirs', 'theirs');

  perform set_config('request.jwt.claim.sub', me::text, true);
  execute 'set local role authenticated';

  -- Asking starts 30 days; nothing is deleted yet.
  answer := public.request_account_deletion();
  if (answer->>'deletes_at')::timestamptz < now() + interval '29 days' then raise exception 'Deletion is not 30 days out: %', answer; end if;
  execute 'reset role';
  if not exists (select 1 from auth.users where id = me) then raise exception 'Account went at once'; end if;

  -- Signing in again stops it.
  execute 'set local role authenticated';
  answer := public.cancel_account_deletion();
  if not (answer->>'cancelled')::boolean then raise exception 'Cancel did not find the request'; end if;
  answer := public.cancel_account_deletion();
  if (answer->>'cancelled')::boolean then raise exception 'Cancel found a request that was already gone'; end if;

  -- A person cannot run the sweep, nor see the queue.
  refused := false;
  begin perform public.purge_due_account_deletions(); exception when insufficient_privilege then refused := true; end;
  if not refused then raise exception 'authenticated ran the purge'; end if;
  refused := false;
  begin perform 1 from public.account_deletions; exception when insufficient_privilege then refused := true; end;
  if not refused then raise exception 'authenticated read the deletion queue'; end if;

  -- Asked, 30 days pass, the sweep runs: this account and its packs go, the other stays.
  perform public.request_account_deletion();
  execute 'reset role';
  if public.purge_due_account_deletions() <> 0 then raise exception 'Swept before 30 days'; end if;
  update public.account_deletions set requested_at = now() - interval '31 days' where user_id = me;
  if public.purge_due_account_deletions() <> 1 then raise exception 'Sweep did not delete the due account'; end if;
  if exists (select 1 from auth.users where id = me) or exists (select 1 from public.packs where owner_id = me) then raise exception 'Due account or its packs left behind'; end if;
  if not exists (select 1 from auth.users where id = other) or not exists (select 1 from public.packs where owner_id = other) then raise exception 'Sweep touched another account'; end if;
  raise notice 'PASS: 30-day request, cancel on sign-in, private queue and sweep, sweep deletes only due accounts';
end $$;
rollback;
