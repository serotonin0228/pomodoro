-- Additive music catalog. Existing workspaces and auth data are untouched.
create table public.music_tracks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
  title text not null check (char_length(title) between 1 and 200),
  artist text not null default '',
  storage_path text not null unique check (storage_path like user_id::text || '/%'),
  audio_revision integer not null default 1 check (audio_revision > 0),
  bytes bigint not null check (bytes > 0 and bytes <= 30000000),
  sha256 text not null check (sha256 ~ '^[a-f0-9]{64}$'),
  md5 text not null check (md5 ~ '^[a-f0-9]{32}$'),
  sort_order integer not null default 0,
  enabled boolean not null default true,
  updated_at timestamptz not null default now()
);
create index music_tracks_owner_order on public.music_tracks(user_id, sort_order, id);
alter table public.music_tracks enable row level security;
revoke all on public.music_tracks from public, anon, authenticated;
grant select on public.music_tracks to authenticated;
create policy "Read own enabled music" on public.music_tracks for select to authenticated
  using ((select auth.uid()) = user_id and enabled);
create function public.version_music_track() returns trigger
language plpgsql security invoker set search_path = '' as $$
begin
  if new.storage_path is distinct from old.storage_path or new.sha256 is distinct from old.sha256
    or new.md5 is distinct from old.md5 or new.bytes is distinct from old.bytes then
    new.audio_revision := greatest(old.audio_revision + 1, new.audio_revision);
  else new.audio_revision := greatest(old.audio_revision, new.audio_revision); end if;
  new.updated_at := clock_timestamp();
  return new;
end $$;
revoke all on function public.version_music_track() from public, anon, authenticated;
create trigger version_music_track before update on public.music_tracks
  for each row execute function public.version_music_track();
create policy "Download own catalog music" on storage.objects for select to authenticated
  using (bucket_id = 'pomodoro-music' and (storage.foldername(name))[1] = (select auth.uid())::text
    and exists (select 1 from public.music_tracks m where m.storage_path = name and m.enabled));
alter publication supabase_realtime add table public.music_tracks;
