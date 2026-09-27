-- Live pack updates for the app, and no throwaway email addresses at sign-up.
begin;

-- The app used to ask for changes every thirty seconds. It now listens: Supabase Realtime
-- sends a nudge when a pack row changes, and every change to a pack's items or plan stamps
-- that row, so publishing `packs` alone is enough. Row-level security still applies to what
-- each listener receives, so a person only ever hears about their own packs.
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (select 1 from pg_publication_tables
                     where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'packs') then
    alter publication supabase_realtime add table public.packs;
  end if;
end $$;

-- Domains of disposable inboxes: addresses that work for ten minutes, so an account made with
-- one is somebody dodging the one-pack free plan or testing what they can break. Add to this
-- table as new ones turn up; nothing else needs changing.
create table public.blocked_email_domains (domain text primary key check (domain = lower(domain)));
alter table public.blocked_email_domains enable row level security;
revoke all on public.blocked_email_domains from anon, authenticated;

insert into public.blocked_email_domains(domain) values
  ('10minutemail.com'), ('10minutemail.net'), ('20minutemail.com'), ('temp-mail.org'), ('temp-mail.io'),
  ('tempmail.com'), ('tempmail.net'), ('tempmailo.com'), ('tempr.email'), ('guerrillamail.com'),
  ('guerrillamail.net'), ('guerrillamail.org'), ('guerrillamailblock.com'), ('sharklasers.com'), ('grr.la'),
  ('mailinator.com'), ('mailinator.net'), ('maildrop.cc'), ('yopmail.com'), ('yopmail.net'), ('yopmail.fr'),
  ('getnada.com'), ('nada.email'), ('dispostable.com'), ('trashmail.com'), ('trashmail.de'), ('throwawaymail.com'),
  ('fakeinbox.com'), ('mintemail.com'), ('mohmal.com'), ('emailondeck.com'), ('burnermail.io'), ('spamgourmet.com'),
  ('mailnesia.com'), ('mytemp.email'), ('tempinbox.com'), ('discard.email'), ('moakt.com'), ('inboxkitten.com'),
  ('mail.tm'), ('mail.gw'), ('1secmail.com'), ('1secmail.org'), ('1secmail.net'), ('dropmail.me'),
  ('emailfake.com'), ('crazymailing.com'), ('tempail.com'), ('fexpost.com'), ('linshiyouxiang.net')
on conflict do nothing;

-- Supabase's "Before User Created" auth hook. It must also be switched on in the dashboard
-- (Authentication → Hooks); until then this function exists but nothing calls it.
create function public.hook_block_disposable_email(event jsonb) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
  email text := lower(coalesce(event->'user'->>'email', ''));
  email_domain text := split_part(email, '@', 2);
begin
  -- Google sign-in and every other address pass straight through.
  if email_domain <> '' and exists (
    select 1 from public.blocked_email_domains b
    where email_domain = b.domain or email_domain like '%.' || b.domain
  ) then
    return jsonb_build_object('error', jsonb_build_object(
      'http_code', 400,
      'message', 'Please sign up with an email address you keep. Temporary inboxes can''t be used.'));
  end if;
  return '{}'::jsonb;
end $$;

revoke all on function public.hook_block_disposable_email(jsonb) from public, anon, authenticated;
do $$
begin
  if exists (select 1 from pg_roles where rolname = 'supabase_auth_admin') then
    grant execute on function public.hook_block_disposable_email(jsonb) to supabase_auth_admin;
    grant select on public.blocked_email_domains to supabase_auth_admin;
  end if;
end $$;

commit;
