-- Updating only the binary audio automatically updates the catalog and invalidates device caches.
create function private.sync_music_audio() returns trigger
language plpgsql security invoker set search_path = '' as $$
begin
  update public.music_tracks set bytes = octet_length(new.data),
    md5 = pg_catalog.md5(new.data), sha256 = encode(pg_catalog.sha256(new.data), 'hex')
    where id = new.track_id and user_id = new.user_id returning audio_revision into new.audio_revision;
  if not found then raise exception 'Music owner mismatch'; end if;
  return new;
end $$;
revoke all on function private.sync_music_audio() from public, anon, authenticated;
create trigger sync_music_audio before insert or update of data on private.music_audio
  for each row execute function private.sync_music_audio();
create or replace function public.download_music(track_id uuid, revision integer)
returns public."audio/mpeg" language plpgsql stable security invoker set search_path = '' as $$
declare audio bytea;
begin
  select a.data into audio from private.music_audio a join public.music_tracks m on m.id = a.track_id
    where a.track_id = $1 and m.audio_revision = $2 and m.enabled
      and a.user_id = auth.uid() and m.user_id = auth.uid();
  if audio is null then raise sqlstate 'PT404' using message = 'Music unavailable'; end if;
  return audio;
end $$;
