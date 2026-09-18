-- Atomic normalized pack exchange. Client identifiers preserve engine revision identity.
begin;
alter table public.packs add column client_id text;
alter table public.pack_spaces add column client_id text;
alter table public.pack_items add column client_id text;
alter table public.pack_obstructions add column client_id text;
create unique index packs_owner_client on public.packs(owner_id,client_id);

create function public.read_pack(p_id uuid) returns jsonb
language plpgsql stable security invoker set search_path = '' as $$
declare result jsonb; tab text; rows jsonb;
begin
  select to_jsonb(p) into result from public.packs p where id=p_id;
  if result is null then return null; end if;
  result := jsonb_build_object('pack',result);
  foreach tab in array array['pack_geometry','pack_spaces','pack_openings','pack_obstructions',
    'pack_items','pack_plans','pack_plan_instances','pack_placements','pack_unplaced','pack_progress'] loop
    execute format('select coalesce(jsonb_agg(to_jsonb(t)),''[]''::jsonb) from public.%I t where pack_id=$1',tab)
      into rows using p_id;
    result := result || jsonb_build_object(tab,rows);
  end loop;
  return result;
end $$;
revoke all on function public.read_pack(uuid) from public, anon;
grant execute on function public.read_pack(uuid) to authenticated;

create function public.write_pack(p_id uuid, p_expected bigint, p_document jsonb, p_deleted boolean default false)
returns bigint language plpgsql security definer set search_path = '' as $$
declare current_version bigint; tab text; rows jsonb; result bigint; uid uuid := auth.uid();
begin
  if uid is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if p_expected is null or p_expected<0 or p_deleted is null or p_document is null then raise exception 'Invalid sync request'; end if;
  if not p_deleted and coalesce(length(p_document->'pack'->>'client_id'),0) not between 1 and 200 then raise exception 'Client identity required'; end if;
  if octet_length(p_document::text)>33554432 then raise exception 'Pack too large'; end if;
  -- Advisory lock also serializes concurrent first inserts for the same identifier.
  perform pg_advisory_xact_lock(hashtextextended(p_id::text,0));
  select row_version into current_version from public.packs where id=p_id and owner_id=uid for update;
  if exists(select 1 from public.packs where id=p_id and owner_id<>uid) then
    raise exception 'Not authorized' using errcode='42501';
  end if;
  if coalesce(current_version,0)<>p_expected then
    raise exception 'Pack changed on another device' using errcode='40001';
  end if;
  if current_version is null then
    if p_deleted then return 0; end if;
    insert into public.packs(id,owner_id,name,client_id)
      values(p_id,uid,p_document->'pack'->>'name',p_document->'pack'->>'client_id');
  else
    update public.packs set name=case when p_deleted then name else p_document->'pack'->>'name' end,
      deleted_at=case when p_deleted then clock_timestamp() else null end where id=p_id;
  end if;
  if not p_deleted then
    delete from public.pack_plans where pack_id=p_id;
    delete from public.pack_items where pack_id=p_id;
    delete from public.pack_spaces where pack_id=p_id;
    delete from public.pack_geometry where pack_id=p_id;
    foreach tab in array array['pack_geometry','pack_spaces','pack_openings','pack_obstructions',
      'pack_items','pack_plans','pack_plan_instances','pack_placements','pack_unplaced','pack_progress'] loop
      if jsonb_typeof(p_document->tab) is distinct from 'array' then raise exception 'Missing table %',tab; end if;
      select coalesce(jsonb_agg(value || jsonb_build_object('pack_id',p_id)),'[]'::jsonb)
        into rows from jsonb_array_elements(p_document->tab);
      execute format('insert into public.%I select * from jsonb_populate_recordset(null::public.%I,$1)',tab,tab) using rows;
    end loop;
    if (select count(*) from public.pack_spaces where pack_id=p_id)<>1 then raise exception 'One space required'; end if;
    if exists(select 1 from public.pack_plans pl where pl.pack_id=p_id and
      (select count(*) from public.pack_plan_instances i where i.pack_id=p_id and i.plan_id=pl.id)
      <> (select coalesce(sum(quantity),0) from public.pack_items where pack_id=p_id)) then
      raise exception 'Incomplete plan instances';
    end if;
    if exists(select 1 from public.pack_plan_instances i where i.pack_id=p_id and
      ((i.outcome='placed' and not exists(select 1 from public.pack_placements p where p.pack_id=p_id and p.plan_id=i.plan_id and p.instance_id=i.instance_id)) or
       (i.outcome='unplaced' and not exists(select 1 from public.pack_unplaced p where p.pack_id=p_id and p.plan_id=i.plan_id and p.instance_id=i.instance_id)))) then
      raise exception 'Missing plan outcome';
    end if;
  end if;
  select row_version into result from public.packs where id=p_id;
  return result;
end $$;
revoke all on function public.write_pack(uuid,bigint,jsonb,boolean) from public,anon;
grant execute on function public.write_pack(uuid,bigint,jsonb,boolean) to authenticated;
-- All mobile writes now go through the ownership-checked atomic RPC.
do $$ declare tab text; begin
  foreach tab in array array['packs','pack_geometry','pack_spaces','pack_openings','pack_obstructions',
    'pack_items','pack_plans','pack_plan_instances','pack_placements','pack_unplaced','pack_progress'] loop
    execute format('revoke insert,update,delete on public.%I from authenticated',tab);
  end loop;
end $$;
commit;
