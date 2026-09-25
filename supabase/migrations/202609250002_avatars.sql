-- Profile photos.
--
-- The bucket is public to read, because an avatar is shown next to a name and signing every
-- read would buy nothing. Writing is restricted to the one folder named after the person's own
-- id, so nobody can overwrite somebody else's picture.
begin;

insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do nothing;

create policy avatars_read on storage.objects
  for select to public using (bucket_id = 'avatars');

create policy avatars_write on storage.objects
  for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy avatars_update on storage.objects
  for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy avatars_delete on storage.objects
  for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- Where the app remembers which picture is yours. Null means "use the one from Google, or the
-- initial", which is why it is nullable rather than defaulted.
alter table public.user_settings add column if not exists avatar_url text;

commit;
