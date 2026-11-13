-- Audio stays in a non-exposed schema. The RPC runs with the caller's own privileges.
create schema if not exists private;
grant usage on schema private to authenticated;
create table private.music_audio (
  track_id uuid primary key references public.music_tracks(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  audio_revision integer not null,
  data bytea not null
);
alter table private.music_audio enable row level security;
create index music_audio_owner on private.music_audio(user_id);
revoke all on private.music_audio from public, anon, authenticated;
grant select on private.music_audio to authenticated;
create policy "Read own music audio" on private.music_audio for select to authenticated
  using ((select auth.uid()) = user_id);
create domain public."audio/mpeg" as bytea;
create function public.download_music(track_id uuid, revision integer)
returns public."audio/mpeg" language plpgsql stable security invoker set search_path = '' as $$
declare audio bytea;
begin
  select a.data into audio from private.music_audio a join public.music_tracks m on m.id = a.track_id
    where a.track_id = $1 and a.audio_revision = $2 and m.audio_revision = $2 and m.enabled
      and a.user_id = auth.uid() and m.user_id = auth.uid();
  if audio is null then raise sqlstate 'PT404' using message = 'Music unavailable'; end if;
  return audio;
end $$;
revoke all on function public.download_music(uuid,integer) from public, anon;
grant execute on function public.download_music(uuid,integer) to authenticated;
-- The Storage upload route was not deployed; no Storage policy is needed.
drop policy "Download own catalog music" on storage.objects;
