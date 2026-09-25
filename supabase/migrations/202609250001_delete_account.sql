-- In-app account deletion, which Google Play requires.
--
-- A signed-in person can only ever delete themselves: the function takes no argument and reads
-- auth.uid(), so there is no id to tamper with. It runs as the definer because deleting from
-- auth.users is not something a normal user may do; every pack and setting goes with it through
-- the existing owner_id and user_id cascades.
begin;

create or replace function public.delete_account() returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null then
    raise exception 'Not signed in';
  end if;

  delete from public.packs where owner_id = me;
  delete from public.user_settings where user_id = me;
  delete from auth.users where id = me;
end;
$$;

revoke all on function public.delete_account() from public, anon;
grant execute on function public.delete_account() to authenticated;

commit;
