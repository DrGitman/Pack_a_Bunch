-- Rollback-only checks. Requires the local auth fixture; never writes production users.
begin;
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
insert into public.packs(id,name) values ('10000000-0000-0000-0000-000000000001','Owner one');
insert into public.pack_spaces(pack_id,name,width_mm,depth_mm,height_mm,measurement_source)
 values ('10000000-0000-0000-0000-000000000001','Crate',600,400,350,'TYPED_IN');
insert into public.pack_items(pack_id,id,name,position,width_mm,depth_mm,height_mm,quantity,measurement_source)
 values ('10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',
 'Toolbox',0,100,100,100,1,'TYPED_IN');
do $$ begin
 begin
  insert into public.pack_items(pack_id,name,position,width_mm,depth_mm,height_mm,quantity,measurement_source)
   values ('10000000-0000-0000-0000-000000000001','Invalid',1,0,100,100,1,'TYPED_IN');
  raise exception 'FAIL: zero dimension accepted';
 exception when check_violation then null; end;
 begin
  insert into public.packs(owner_id,name) values ('00000000-0000-0000-0000-000000000002','Spoofed');
  raise exception 'FAIL: foreign owner accepted';
 exception when insufficient_privilege then null; end;
end $$;
update public.packs set name='Renamed' where id='10000000-0000-0000-0000-000000000001' and row_version=3;
do $$ declare n integer; begin
 if (select row_version from public.packs where id='10000000-0000-0000-0000-000000000001') <> 4
  then raise exception 'FAIL: child changes did not advance version'; end if;
 update public.packs set name='Stale overwrite' where id='10000000-0000-0000-0000-000000000001' and row_version=3;
 get diagnostics n = row_count;
 if n <> 0 then raise exception 'FAIL: stale optimistic update'; end if;
end $$;
insert into public.pack_plans(pack_id,id,input_revision,solver_version,strategy,stopped_on_time_budget,usable_volume_mm3)
 values ('10000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','test-revision',1,'test',false,84000000);
insert into public.pack_plan_instances(pack_id,plan_id,instance_id,item_id,outcome) values
 ('10000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','item-1',
 '20000000-0000-0000-0000-000000000001','placed');
insert into public.pack_placements(pack_id,plan_id,instance_id,sequence_index,x_mm,y_mm,z_mm,
 oriented_width_mm,oriented_depth_mm,oriented_height_mm,orientation) values
 ('10000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','item-1',0,0,0,0,100,100,100,'WIDTH_DEPTH_HEIGHT');
insert into public.pack_progress(pack_id,plan_id,instance_id) values
 ('10000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','item-1');
do $$ begin
 begin
  insert into public.pack_unplaced(pack_id,plan_id,instance_id,reason) values
   ('10000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','item-1','TIME_BUDGET_REACHED');
  raise exception 'FAIL: instance is both placed and unplaced';
 exception when foreign_key_violation then null; end;
 begin
  insert into public.pack_progress(pack_id,plan_id,instance_id) values
   ('10000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','not-placed');
  raise exception 'FAIL: orphan guide progress';
 exception when foreign_key_violation then null; end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
do $$ declare n integer; begin
 if exists(select 1 from public.packs) or exists(select 1 from public.pack_items)
  or exists(select 1 from public.pack_progress) then raise exception 'FAIL: cross-user read'; end if;
 update public.packs set name='Hijacked' where id='10000000-0000-0000-0000-000000000001';
 get diagnostics n = row_count;
 if n <> 0 then raise exception 'FAIL: cross-user update'; end if;
 begin
  insert into public.pack_items(pack_id,name,position,width_mm,depth_mm,height_mm,quantity,measurement_source)
   values ('10000000-0000-0000-0000-000000000001','Foreign child',1,100,100,100,1,'TYPED_IN');
  raise exception 'FAIL: cross-user child insert';
 exception when insufficient_privilege then null; end;
end $$;
set local role anon;
do $$ begin
 begin
  perform * from public.packs;
  raise exception 'FAIL: anonymous read';
 exception when insufficient_privilege then null; end;
end $$;
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
delete from public.packs where id='10000000-0000-0000-0000-000000000001';
do $$ begin
 if exists(select 1 from public.pack_items) or exists(select 1 from public.pack_progress)
  or exists(select 1 from public.pack_plan_instances) then raise exception 'FAIL: orphan after cascade'; end if;
end $$;
rollback;
select 'PASS: dimension, ownership, optimistic update, outcome, progress, isolation and cascade checks' as result;
