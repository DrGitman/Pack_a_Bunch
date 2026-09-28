-- Rollback-only checks for in-app feedback. Disposable local cluster only: needs
-- local_auth_fixture.sql, never a Supabase project.
begin;
do $$
declare
  me uuid := '00000000-0000-0000-0000-000000000001';
  other uuid := '00000000-0000-0000-0000-000000000002';
  refused boolean;
begin
  perform set_config('request.jwt.claim.sub', me::text, true);
  execute 'set local role authenticated';

  insert into public.app_feedback(stars, comment, packs_finished, app_version) values (4, 'Packed the boot first go', 6, '1.0.0');

  -- Feedback can't be read back, not even one's own.
  refused := false;
  begin perform 1 from public.app_feedback; exception when insufficient_privilege then refused := true; end;
  if not refused then raise exception 'authenticated read feedback'; end if;

  -- Nor written in someone else's name, nor with stars outside one to five, nor an essay.
  refused := false;
  begin insert into public.app_feedback(user_id, stars) values (other, 5);
  exception when insufficient_privilege then refused := true; end;
  if not refused then raise exception 'wrote feedback as another person'; end if;
  refused := false;
  begin insert into public.app_feedback(stars) values (6);
  exception when check_violation then refused := true; end;
  if not refused then raise exception 'took six stars'; end if;
  refused := false;
  begin insert into public.app_feedback(stars, comment) values (3, repeat('x', 1001));
  exception when check_violation then refused := true; end;
  if not refused then raise exception 'took a comment over 1000 characters'; end if;

  -- The 11th in an hour is refused.
  for i in 1..9 loop insert into public.app_feedback(stars) values (5); end loop;
  refused := false;
  begin insert into public.app_feedback(stars) values (5);
  exception when sqlstate '54000' then refused := true; end;
  if not refused then raise exception 'rate limit did not refuse the 11th'; end if;
  raise notice 'PASS: insert own feedback only, no reading back, 1-5 stars, 1000 characters, 10 an hour';
end $$;
rollback;
