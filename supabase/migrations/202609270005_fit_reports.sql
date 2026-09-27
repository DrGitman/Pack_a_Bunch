-- Why a planned item didn't fit, as the person told us on the "It doesn't fit" page.
--
-- Each report is the reason they picked and the sizes involved — never a name or any free
-- text — so the plans can be checked against what actually happened: typed sizes that were too
-- small, camera measurements that ran short, spaces with something in the way the plan never
-- knew about. People can add reports but not read them back, their own included; they are for
-- whoever looks after the app, in the dashboard.
begin;

create table public.fit_reports (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null default auth.uid() references auth.users(id) on delete cascade,
  reason          text not null check (reason in ('item_bigger', 'space_smaller', 'in_the_way', 'other_arrangement', 'skipped')),
  item_width_mm   integer check (item_width_mm between 1 and 100000),
  item_depth_mm   integer check (item_depth_mm between 1 and 100000),
  item_height_mm  integer check (item_height_mm between 1 and 100000),
  space_width_mm  integer check (space_width_mm between 1 and 100000),
  space_depth_mm  integer check (space_depth_mm between 1 and 100000),
  space_height_mm integer check (space_height_mm between 1 and 100000),
  space_measured  text check (space_measured in ('TYPED_IN', 'CAMERA_ESTIMATE', 'SUGGESTED')),
  space_scanned   boolean not null default false,
  step            integer check (step between 1 and 10000),
  steps           integer check (steps between 1 and 10000),
  created_at      timestamptz not null default now()
);
alter table public.fit_reports enable row level security;
revoke all on public.fit_reports from anon, authenticated;
grant insert on public.fit_reports to authenticated;

create policy fit_reports_insert on public.fit_reports
  for insert to authenticated
  with check (user_id = (select auth.uid()));

-- A person reports a misfit now and then; a loop reports hundreds. Thirty an hour is plenty.
create function public.fit_reports_rate() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (select count(*) from public.fit_reports
      where user_id = new.user_id and created_at > now() - interval '1 hour') >= 30 then
    raise exception 'Too many reports. Try again later.' using errcode = '54000';
  end if;
  return new;
end $$;
revoke all on function public.fit_reports_rate() from public, anon, authenticated;

create trigger fit_reports_rate before insert on public.fit_reports
  for each row execute function public.fit_reports_rate();

create index fit_reports_user_time on public.fit_reports(user_id, created_at);

commit;
