-- "How are we doing?": the stars and the few words people give on the in-app feedback card.
--
-- Shown now and then after a pack is finished (never gating the Play review, which is asked for
-- separately and whatever the stars were). Like fit reports, people can add feedback but not
-- read it back; it is for whoever looks after the app, in the dashboard.
begin;

create table public.app_feedback (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null default auth.uid() references auth.users(id) on delete cascade,
  stars          smallint not null check (stars between 1 and 5),
  comment        text check (char_length(comment) <= 1000),
  packs_finished integer check (packs_finished between 0 and 100000),
  app_version    text check (char_length(app_version) <= 40),
  created_at     timestamptz not null default now()
);
alter table public.app_feedback enable row level security;
revoke all on public.app_feedback from anon, authenticated;
grant insert on public.app_feedback to authenticated;

create policy app_feedback_insert on public.app_feedback
  for insert to authenticated
  with check (user_id = (select auth.uid()));

-- The card shows a few times a year; ten an hour stops a loop, never a person.
create function public.app_feedback_rate() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (select count(*) from public.app_feedback
      where user_id = new.user_id and created_at > now() - interval '1 hour') >= 10 then
    raise exception 'Too much feedback at once. Try again later.' using errcode = '54000';
  end if;
  return new;
end $$;
revoke all on function public.app_feedback_rate() from public, anon, authenticated;

create trigger app_feedback_rate before insert on public.app_feedback
  for each row execute function public.app_feedback_rate();

create index app_feedback_user_time on public.app_feedback(user_id, created_at);

commit;
