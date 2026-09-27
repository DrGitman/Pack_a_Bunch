-- Rollback-only checks for fit reports. Disposable local cluster only: needs
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

  insert into public.fit_reports(reason, item_width_mm, item_depth_mm, item_height_mm, step, steps)
    values ('item_bigger', 330, 200, 120, 3, 6);

  -- Reports can't be read back, not even one's own.
  refused := false;
  begin perform 1 from public.fit_reports; exception when insufficient_privilege then refused := true; end;
  if not refused then raise exception 'authenticated read fit reports'; end if;

  -- Nor written in someone else's name, nor with a made-up reason.
  refused := false;
  begin insert into public.fit_reports(user_id, reason) values (other, 'skipped');
  exception when insufficient_privilege then refused := true; end;
  if not refused then raise exception 'wrote a report as another person'; end if;
  refused := false;
  begin insert into public.fit_reports(reason) values ('because');
  exception when check_violation then refused := true; end;
  if not refused then raise exception 'took a reason that is not one of the four'; end if;

  -- The 31st in an hour is refused.
  for i in 1..29 loop insert into public.fit_reports(reason) values ('skipped'); end loop;
  refused := false;
  begin insert into public.fit_reports(reason) values ('skipped');
  exception when sqlstate '54000' then refused := true; end;
  if not refused then raise exception 'rate limit did not refuse the 31st report'; end if;
  raise notice 'PASS: insert own reports only, no reading back, known reasons only, 30 an hour';
end $$;
rollback;
