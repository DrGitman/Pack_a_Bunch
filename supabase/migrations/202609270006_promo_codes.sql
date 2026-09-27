-- Promo codes that switch Pack Plus on for a set number of days — for judges and testers,
-- without a Google Play purchase. Codes are stored only as SHA-256 hashes, so reading this
-- table (which nobody but the project owner can) still does not reveal a code.
begin;

create table public.promo_codes (
  code_hash  text primary key,
  label      text,
  plus_days  integer not null default 30 check (plus_days between 1 and 365),
  max_uses   integer not null default 50 check (max_uses >= 1),
  uses       integer not null default 0,
  expires_at timestamptz
);
create table public.plus_grants (
  user_id uuid primary key references auth.users(id) on delete cascade,
  until   timestamptz not null,
  source  text
);
create table public.promo_attempts (
  user_id uuid not null references auth.users(id) on delete cascade,
  at      timestamptz not null default now()
);
alter table public.promo_codes enable row level security;
alter table public.plus_grants enable row level security;
alter table public.promo_attempts enable row level security;
revoke all on public.promo_codes, public.plus_grants, public.promo_attempts from anon, authenticated;

create function public.redeem_promo_code(p_code text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  c public.promo_codes%rowtype;
  new_until timestamptz;
begin
  if me is null then raise exception 'Not signed in'; end if;
  -- Ten tries an hour: enough for typos, useless for guessing.
  if (select count(*) from public.promo_attempts where user_id = me and at > now() - interval '1 hour') >= 10 then
    raise exception 'Too many tries. Wait an hour and try again.' using errcode = '54000';
  end if;
  insert into public.promo_attempts(user_id) values (me);
  select * into c from public.promo_codes
    where code_hash = encode(sha256(convert_to(upper(trim(p_code)), 'UTF8')), 'hex') for update;
  if not found or (c.expires_at is not null and c.expires_at < now()) or c.uses >= c.max_uses then
    return jsonb_build_object('ok', false);
  end if;
  update public.promo_codes set uses = uses + 1 where code_hash = c.code_hash;
  insert into public.plus_grants(user_id, until, source)
    values (me, now() + make_interval(days => c.plus_days), coalesce(c.label, 'promo'))
    on conflict (user_id) do update
      set until = greatest(public.plus_grants.until, now()) + make_interval(days => c.plus_days), source = excluded.source
    returning until into new_until;
  return jsonb_build_object('ok', true, 'until', new_until);
end $$;

create function public.my_plus_grant() returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('until', (select until from public.plus_grants where user_id = auth.uid()))
$$;

revoke all on function public.redeem_promo_code(text), public.my_plus_grant() from public, anon;
grant execute on function public.redeem_promo_code(text), public.my_plus_grant() to authenticated;
commit;
