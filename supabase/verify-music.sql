begin;
-- Run as the database owner against a project with at least one music track.
select set_config('test.music_owner', (select user_id::text from public.music_tracks order by sort_order limit 1), true);
select set_config('test.music_track', (select id::text from public.music_tracks order by sort_order limit 1), true);
select set_config('test.other_user', gen_random_uuid()::text, true);
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub', current_setting('test.music_owner'), 'role', 'authenticated')::text, true);
do $$declare item record; audio bytea; begin
  assert (select count(*) from public.music_tracks) > 0, 'Owner catalog missing';
  for item in select id,audio_revision,bytes,md5,sha256 from public.music_tracks loop
    audio := public.download_music(item.id,item.audio_revision);
    assert octet_length(audio)=item.bytes and md5(audio)=item.md5 and encode(sha256(audio),'hex')=item.sha256, 'Audio mismatch';
  end loop;
end $$;
select set_config('request.jwt.claims', json_build_object('sub', current_setting('test.other_user'), 'role', 'authenticated')::text, true);
do $$begin
  assert (select count(*) from public.music_tracks)=0, 'Cross-account catalog leak';
  begin
    perform public.download_music(current_setting('test.music_track')::uuid,1);
    raise exception 'Cross-account audio leak';
  exception when sqlstate 'PT404' then null; end;
end $$;
reset role;
do $$declare item record; begin
  assert not has_function_privilege('anon','public.download_music(uuid,integer)','EXECUTE'), 'Anonymous audio execution';
  assert not has_table_privilege('anon','public.music_tracks','SELECT'), 'Anonymous catalog access';
  select id,audio_revision,bytes into item from public.music_tracks order by sort_order limit 1;
  update private.music_audio set data=data || decode('00','hex') where track_id=item.id;
  assert (select audio_revision=item.audio_revision+1 and bytes=item.bytes+1 from public.music_tracks where id=item.id), 'Audio version trigger failed';
end $$;
rollback;
select 'PASS: owner downloads, full audio checksums, cross-account isolation, anonymous denial, automatic audio version updates; all test edits rolled back' as verification;
