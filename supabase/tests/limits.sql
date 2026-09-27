-- Rollback-only checks for the upload, rate and size limits. Disposable local cluster only:
-- needs local_auth_fixture.sql and a stand-in storage schema, never a Supabase project.
begin;
do $$
declare
  me uuid := '00000000-0000-0000-0000-000000000001';
  doc jsonb := '{"pack":{"name":"Limits","client_id":"limits"},
    "pack_geometry":[],"pack_spaces":[{"client_id":"s","name":"Crate","width_mm":500,"depth_mm":400,"height_mm":300,"edge_gap_mm":5,"measurement_source":"SUGGESTED","unknown_is_solid":true}],
    "pack_openings":[],"pack_obstructions":[],
    "pack_items":[{"id":"30000000-0000-0000-0000-00000000000a","client_id":"a","name":"Kettle","position":0,"width_mm":250,"depth_mm":170,"height_mm":250,"quantity":1,"keep_upright":false,"may_support_items":true,"measurement_source":"SUGGESTED"},
                  {"id":"30000000-0000-0000-0000-00000000000b","client_id":"b","name":"Toaster","position":1,"width_mm":300,"depth_mm":180,"height_mm":200,"quantity":1,"keep_upright":false,"may_support_items":true,"measurement_source":"TYPED_IN"}],
    "pack_plans":[],"pack_plan_instances":[],"pack_placements":[],"pack_unplaced":[],"pack_progress":[]}';
  counted integer;
  refused boolean;
begin
  perform set_config('request.jwt.claim.sub', me::text, true);
  execute 'set local role authenticated';

  -- A looked-up size syncs instead of being refused.
  perform public.write_pack('20000000-0000-0000-0000-000000000001', 0, doc);

  -- One sync of a pack with several items counts once, however many rows it stamps.
  execute 'reset role';
  select writes into counted from public.sync_rate where user_id = me;
  if counted <> 1 then raise exception 'One sync counted as % writes', counted; end if;

  -- The 121st sync in a minute is refused.
  update public.sync_rate set writes = 120, last_xact = 'earlier' where user_id = me;
  execute 'set local role authenticated';
  refused := false;
  begin
    perform public.write_pack('20000000-0000-0000-0000-000000000002', 0, jsonb_set(doc, '{pack,client_id}', '"second"'));
  exception when sqlstate '54000' then refused := true;
  end;
  if not refused then raise exception 'Rate limit did not refuse the 121st sync'; end if;

  -- The 1001st pack is refused.
  execute 'reset role';
  update public.sync_rate set writes = 0, last_xact = 'earlier' where user_id = me;
  insert into public.packs(id, owner_id, name, client_id)
    select gen_random_uuid(), me, 'Filler', 'filler-' || g from generate_series(1, 999) g;
  execute 'set local role authenticated';
  refused := false;
  begin
    perform public.write_pack('20000000-0000-0000-0000-000000000003', 0, jsonb_set(doc, '{pack,client_id}', '"third"'));
  exception when sqlstate '54000' then refused := true;
  end;
  if not refused then raise exception 'Pack cap did not refuse the 1001st pack'; end if;

  -- Avatars: only your own avatar.jpg, never another file or another person's folder.
  insert into storage.objects(bucket_id, name) values ('avatars', me::text || '/avatar.jpg');
  refused := false;
  begin insert into storage.objects(bucket_id, name) values ('avatars', me::text || '/page.html');
  exception when insufficient_privilege then refused := true; end;
  if not refused then raise exception 'Avatar bucket took a second file'; end if;
  refused := false;
  begin insert into storage.objects(bucket_id, name) values ('avatars', '00000000-0000-0000-0000-000000000002/avatar.jpg');
  exception when insufficient_privilege then refused := true; end;
  if not refused then raise exception 'Avatar bucket let one person write in another''s folder'; end if;

  -- The avatar address can only be https. A check constraint holds for every role; this runs
  -- as the owner because the local cluster lacks Supabase's default table grants.
  execute 'reset role';
  refused := false;
  begin insert into public.user_settings(user_id, unit, avatar_url) values (me, 'CENTIMETRES', 'javascript:alert(1)');
  exception when check_violation then refused := true; end;
  if not refused then raise exception 'avatar_url took a javascript: address'; end if;

  if (select allowed_mime_types from storage.buckets where id = 'avatars') <> array['image/jpeg', 'image/png', 'image/webp']
     or (select file_size_limit from storage.buckets where id = 'avatars') <> 2097152 then
    raise exception 'Avatar bucket limits missing';
  end if;
  raise notice 'PASS: suggested sizes, per-sync counting, rate limit, pack cap, avatar name and folder, avatar_url, bucket limits';
end $$;
rollback;
