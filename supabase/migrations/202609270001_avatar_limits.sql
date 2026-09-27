-- Profile photos may only be pictures, only small, and only the one file the app writes.
--
-- The bucket is public to read, and before this anyone signed in could put any file of any
-- size in their own folder — an HTML page or a script included — and have it served from this
-- project's address. The app only ever writes `<user id>/avatar.jpg`, so that is now the only
-- name a person can write, the storage server refuses anything that is not declared as an
-- image, and 2 MB is far above the app's own re-encoded 512 px JPEG.
begin;

update storage.buckets
set file_size_limit = 2097152,
    allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp']
where id = 'avatars';

drop policy if exists avatars_write on storage.objects;
drop policy if exists avatars_update on storage.objects;

create policy avatars_write on storage.objects
  for insert to authenticated
  with check (bucket_id = 'avatars' and name = (select auth.uid())::text || '/avatar.jpg');

create policy avatars_update on storage.objects
  for update to authenticated
  using (bucket_id = 'avatars' and name = (select auth.uid())::text || '/avatar.jpg')
  with check (bucket_id = 'avatars' and name = (select auth.uid())::text || '/avatar.jpg');

-- Delete stays folder-wide, so anything written before these rules can still be cleaned up by
-- its owner.

-- The address the app shows for a person's photo. Only ever an https address: never a
-- `javascript:` or `data:` value, whatever a modified client writes here. `not valid` checks
-- every write from now on without refusing to apply over a row written before the rule.
alter table public.user_settings
  add constraint user_settings_avatar_url_https
  check (avatar_url is null or (avatar_url ~ '^https://' and length(avatar_url) <= 2048)) not valid;

commit;
