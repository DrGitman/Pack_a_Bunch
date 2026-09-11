-- Run once through Supabase migrations or SQL Editor. No existing tables are dropped.
begin;
create table public.packs (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
  name text not null check (length(btrim(name)) between 1 and 160),
  row_version bigint not null default 1 check (row_version > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index packs_owner_updated on public.packs(owner_id, updated_at desc, id);

-- Geometry is an indivisible, versioned binary artifact, not a JSON substitute for tables.
-- All sizes/axes are canonical integer mm: X right, Y back, Z up.
create table public.pack_geometry (
  pack_id uuid not null references public.packs(id) on delete cascade,
  id uuid not null default gen_random_uuid(),
  codec_version smallint not null check (codec_version = 1),
  kind text not null check (kind in ('space_scan','collision_mask','visual_surface','obstruction_mask')),
  payload bytea not null check (octet_length(payload) between 1 and 16777216),
  primary key (pack_id,id)
);
create table public.pack_spaces (
  pack_id uuid primary key references public.packs(id) on delete cascade,
  name text not null check (length(btrim(name)) between 1 and 160),
  width_mm integer not null check (width_mm between 1 and 50000),
  depth_mm integer not null check (depth_mm between 1 and 50000),
  height_mm integer not null check (height_mm between 1 and 50000),
  edge_gap_mm integer not null default 0 check (edge_gap_mm >= 0),
  measurement_source text not null check (measurement_source in ('TYPED_IN','CAMERA_ESTIMATE')),
  scan_geometry_id uuid,
  unknown_is_solid boolean not null default true,
  foreign key (pack_id,scan_geometry_id) references public.pack_geometry(pack_id,id),
  check (2::bigint*edge_gap_mm < width_mm and 2::bigint*edge_gap_mm < depth_mm and edge_gap_mm < height_mm)
);
create table public.pack_openings (
  pack_id uuid primary key references public.pack_spaces(pack_id) on delete cascade,
  width_mm integer not null check (width_mm between 1 and 50000),
  height_mm integer not null check (height_mm between 1 and 50000),
  measurement_source text not null check (measurement_source in ('TYPED_IN','CAMERA_ESTIMATE'))
);
create table public.pack_obstructions (
  pack_id uuid not null references public.pack_spaces(pack_id) on delete cascade,
  id uuid not null default gen_random_uuid(),
  label text not null check (length(btrim(label)) between 1 and 160),
  kind text not null check (kind in ('PART_OF_THE_STRUCTURE','REMOVABLE','UNIDENTIFIED','SOFT')),
  included boolean not null default true,
  geometry_id uuid not null,
  primary key (pack_id,id),
  foreign key (pack_id,geometry_id) references public.pack_geometry(pack_id,id),
  check (kind <> 'PART_OF_THE_STRUCTURE' or included)
);
create table public.pack_items (
  pack_id uuid not null references public.packs(id) on delete cascade,
  id uuid not null default gen_random_uuid(),
  name text not null check (length(btrim(name)) between 1 and 160),
  position integer not null check (position >= 0),
  width_mm integer not null check (width_mm between 1 and 50000),
  depth_mm integer not null check (depth_mm between 1 and 50000),
  height_mm integer not null check (height_mm between 1 and 50000),
  quantity integer not null check (quantity between 1 and 10000),
  keep_upright boolean not null default false,
  may_support_items boolean not null default true,
  measurement_source text not null check (measurement_source in ('TYPED_IN','CAMERA_ESTIMATE')),
  collision_geometry_id uuid,
  visual_geometry_id uuid,
  primary key (pack_id,id),
  unique (pack_id,position) deferrable initially deferred,
  foreign key (pack_id,collision_geometry_id) references public.pack_geometry(pack_id,id),
  foreign key (pack_id,visual_geometry_id) references public.pack_geometry(pack_id,id)
);
-- A plan belongs to a particular immutable input revision. Never infer validity from age.
create table public.pack_plans (
  pack_id uuid not null references public.packs(id) on delete cascade,
  id uuid not null default gen_random_uuid(),
  input_revision text not null check (length(input_revision) between 1 and 256),
  solver_version integer not null check (solver_version > 0),
  strategy text not null check (length(strategy) between 1 and 100),
  stopped_on_time_budget boolean not null,
  usable_volume_mm3 bigint not null check (usable_volume_mm3 between 1 and 125000000000000),
  created_at timestamptz not null default now(),
  primary key (pack_id,id)
);
create index pack_plans_latest on public.pack_plans(pack_id,created_at desc);
create table public.pack_plan_instances (
  pack_id uuid not null,
  plan_id uuid not null,
  instance_id text not null check (length(instance_id) between 1 and 200),
  item_id uuid not null,
  outcome text not null check (outcome in ('placed','unplaced')),
  primary key (pack_id,plan_id,instance_id),
  unique (pack_id,plan_id,instance_id,outcome),
  foreign key (pack_id,plan_id) references public.pack_plans(pack_id,id) on delete cascade,
  foreign key (pack_id,item_id) references public.pack_items(pack_id,id) on delete cascade
);
create index plan_instances_item on public.pack_plan_instances(pack_id,item_id);
create table public.pack_placements (
  pack_id uuid not null,
  plan_id uuid not null,
  instance_id text not null check (length(instance_id) between 1 and 200),
  outcome text not null default 'placed' check (outcome = 'placed'),
  sequence_index integer not null check (sequence_index >= 0),
  x_mm integer not null check (x_mm between 0 and 50000),
  y_mm integer not null check (y_mm between 0 and 50000),
  z_mm integer not null check (z_mm between 0 and 50000),
  oriented_width_mm integer not null check (oriented_width_mm between 1 and 50000),
  oriented_depth_mm integer not null check (oriented_depth_mm between 1 and 50000),
  oriented_height_mm integer not null check (oriented_height_mm between 1 and 50000),
  orientation text not null check (orientation in ('WIDTH_DEPTH_HEIGHT','DEPTH_WIDTH_HEIGHT',
    'WIDTH_HEIGHT_DEPTH','HEIGHT_WIDTH_DEPTH','DEPTH_HEIGHT_WIDTH','HEIGHT_DEPTH_WIDTH')),
  primary key (pack_id,plan_id,instance_id),
  unique (pack_id,plan_id,sequence_index),
  foreign key (pack_id,plan_id,instance_id,outcome)
    references public.pack_plan_instances(pack_id,plan_id,instance_id,outcome) on delete cascade,
  check (x_mm::bigint + oriented_width_mm <= 50000 and
    y_mm::bigint + oriented_depth_mm <= 50000 and z_mm::bigint + oriented_height_mm <= 50000)
);
create table public.pack_unplaced (
  pack_id uuid not null,
  plan_id uuid not null,
  instance_id text not null check (length(instance_id) between 1 and 200),
  outcome text not null default 'unplaced' check (outcome = 'unplaced'),
  reason text not null check (reason in ('LARGER_THAN_THE_SPACE','NO_ROOM_IN_THIS_ARRANGEMENT',
    'WILL_NOT_FIT_THROUGH_THE_OPENING','TIME_BUDGET_REACHED')),
  primary key (pack_id,plan_id,instance_id),
  foreign key (pack_id,plan_id,instance_id,outcome)
    references public.pack_plan_instances(pack_id,plan_id,instance_id,outcome) on delete cascade
);
create table public.pack_progress (
  pack_id uuid not null,
  plan_id uuid not null,
  instance_id text not null,
  packed_at timestamptz not null default now(),
  primary key (pack_id,plan_id,instance_id),
  foreign key (pack_id,plan_id,instance_id)
    references public.pack_placements(pack_id,plan_id,instance_id) on delete cascade
);

-- Only the owner can see or change a pack. The public publishable key alone grants nothing.
alter table public.packs enable row level security;
create policy own_packs on public.packs to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
revoke all on public.packs from anon;
grant select,insert,update,delete on public.packs to authenticated;
do $$
declare tab text;
begin
  foreach tab in array array['pack_geometry','pack_spaces','pack_openings','pack_obstructions',
    'pack_items','pack_plans','pack_plan_instances','pack_placements','pack_unplaced','pack_progress']
  loop
    execute format('alter table public.%I enable row level security',tab);
    execute format('revoke all on public.%I from anon',tab);
    execute format('grant select,insert,update,delete on public.%I to authenticated',tab);
    execute format('create policy owner_access on public.%I to authenticated using
      (exists(select 1 from public.packs p where p.id = pack_id and p.owner_id = (select auth.uid())))
      with check (exists(select 1 from public.packs p where p.id = pack_id and p.owner_id = (select auth.uid())))',tab);
  end loop;
end $$;

-- Server-managed version/time for optimistic updates. Sync must match row_version in WHERE
-- and treat zero updated rows as a conflict, never overwrite an unseen remote revision.
create function public.pack_stamp() returns trigger language plpgsql set search_path = '' as $$
begin
  new.owner_id := old.owner_id;
  new.created_at := old.created_at;
  new.row_version := old.row_version + 1;
  new.updated_at := clock_timestamp();
  return new;
end $$;
create trigger pack_stamp before update on public.packs for each row execute function public.pack_stamp();
revoke all on function public.pack_stamp() from public;
-- Child changes must also make the owning pack visible in an incremental update query.
create function public.pack_child_stamp() returns trigger language plpgsql set search_path = '' as $$
begin
  if tg_op = 'DELETE' then
    update public.packs set updated_at = clock_timestamp() where id = old.pack_id;
    return old;
  end if;
  update public.packs set updated_at = clock_timestamp() where id = new.pack_id;
  if tg_op = 'UPDATE' and old.pack_id <> new.pack_id then
    update public.packs set updated_at = clock_timestamp() where id = old.pack_id;
  end if;
  return new;
end $$;
revoke all on function public.pack_child_stamp() from public;
do $$
declare tab text;
begin
  foreach tab in array array['pack_geometry','pack_spaces','pack_openings','pack_obstructions',
    'pack_items','pack_plans','pack_plan_instances','pack_placements','pack_unplaced','pack_progress']
  loop
    execute format('create trigger pack_child_stamp after insert or update or delete on public.%I
      for each row execute function public.pack_child_stamp()',tab);
  end loop;
end $$;
commit;
