-- Disposable local cluster only; fixture users are supplied by local_auth_fixture.sql.
begin;
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
do $$
declare doc jsonb := '{"pack":{"name":"Sync test","client_id":"test"},
"pack_geometry":[],"pack_spaces":[{"client_id":"space","name":"Test space","width_mm":500,"depth_mm":400,"height_mm":300,"edge_gap_mm":5,"measurement_source":"TYPED_IN","unknown_is_solid":true}],
"pack_openings":[],"pack_obstructions":[],"pack_items":[],"pack_plans":[],"pack_plan_instances":[],"pack_placements":[],"pack_unplaced":[],"pack_progress":[]}';
v bigint; next_v bigint; p uuid := '11111111-1111-1111-1111-111111111111';
begin
  v := public.write_pack(p,0,doc);
  if v<1 or public.read_pack(p)->'pack'->>'client_id'<>'test' then raise exception 'Round trip failed'; end if;
  begin perform public.write_pack(p,0,doc); raise exception 'Stale update accepted';
    exception when serialization_failure then null; end;
  begin perform public.write_pack(p,null,doc); raise exception 'Null version accepted';
    exception when raise_exception then if sqlerrm='Null version accepted' then raise; end if; end;
  begin perform public.write_pack(p,v,jsonb_set(doc,'{pack_spaces,0,width_mm}','0'));
    raise exception 'Invalid dimension accepted'; exception when check_violation then null; end;
  if (public.read_pack(p)->'pack'->>'row_version')::bigint<>v then raise exception 'Failed update was not atomic'; end if;
  begin update public.packs set name='Bypass' where id=p;
    raise exception 'Direct write allowed'; exception when insufficient_privilege then null; end;
  next_v := public.write_pack(p,v,doc,true);
  if public.read_pack(p)->'pack'->>'deleted_at' is null then raise exception 'Missing tombstone'; end if;
  perform public.write_pack(p,next_v,doc,false);
  if public.read_pack(p)->'pack'->>'deleted_at' is not null then raise exception 'Restore failed'; end if;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
do $$ begin
  if public.read_pack('11111111-1111-1111-1111-111111111111') is not null then raise exception 'Other owner could read pack'; end if;
  begin perform public.write_pack('11111111-1111-1111-1111-111111111111',0,'{}',true);
    raise exception 'Other owner could write pack'; exception when insufficient_privilege then null; end;
end $$;
reset role;
set local role anon;
do $$ begin
  begin perform public.read_pack('11111111-1111-1111-1111-111111111111');
    raise exception 'Anonymous read allowed'; exception when insufficient_privilege then null; end;
end $$;
rollback;
