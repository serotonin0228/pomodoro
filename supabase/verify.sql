-- Transaction-only integration checks. No users or workspaces survive the rollback.
begin;
select set_config('pomodoro.test_user_a', gen_random_uuid()::text, true);
select set_config('pomodoro.test_user_b', gen_random_uuid()::text, true);
insert into auth.users(id, aud, role, email, created_at, updated_at)
values
  (current_setting('pomodoro.test_user_a')::uuid, 'authenticated', 'authenticated', 'pomodoro-test-a-' || current_setting('pomodoro.test_user_a') || '@example.invalid', now(), now()),
  (current_setting('pomodoro.test_user_b')::uuid, 'authenticated', 'authenticated', 'pomodoro-test-b-' || current_setting('pomodoro.test_user_b') || '@example.invalid', now(), now());

set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub', current_setting('pomodoro.test_user_a'), 'role', 'authenticated')::text, true);
do $$
declare result jsonb; payload jsonb := '{"timer":{},"settings":{},"records":[],"segments":[]}';
begin
  result := public.save_workspace(0, payload, 'integration-device-a');
  assert (result->>'revision')::bigint = 1, 'Initial revision must be 1';
  assert result->>'user_id' = current_setting('pomodoro.test_user_a'), 'Incorrect ownership';
  result := public.save_workspace(1, payload, 'integration-device-b');
  assert (result->>'revision')::bigint = 2, 'Revision must increment';
  begin
    perform public.save_workspace(1, payload, 'integration-device-a');
    raise exception 'Stale write was incorrectly accepted';
  exception when sqlstate 'P0001' then
    if sqlerrm <> 'SYNC_CONFLICT' then raise; end if;
  end;
  assert public.server_time() > 0, 'Server clock must be accessible';
end $$;

select set_config('request.jwt.claims', json_build_object('sub', current_setting('pomodoro.test_user_b'), 'role', 'authenticated')::text, true);
do $$
declare changed bigint; payload jsonb := '{"timer":{},"settings":{},"records":[],"segments":[]}';
begin
  assert (select count(*) from public.workspaces) = 0, 'Account B can read account A';
  update public.workspaces set writer = 'unauthorized' where user_id = current_setting('pomodoro.test_user_a')::uuid;
  get diagnostics changed = row_count;
  assert changed = 0, 'Account B can update account A';
  begin
    insert into public.workspaces(user_id,payload,writer) values(current_setting('pomodoro.test_user_a')::uuid,payload,'unauthorized');
    raise exception 'Cross-account insert was accepted';
  exception when insufficient_privilege then null;
  end;
  perform public.save_workspace(0, payload, 'integration-device-b');
  assert (select count(*) from public.workspaces) = 1, 'Account B should see only its workspace';
end $$;

reset role;
do $$ begin
  assert not has_table_privilege('anon', 'public.workspaces', 'SELECT'), 'Anon must not read workspaces';
  assert not has_table_privilege('authenticated', 'public.workspaces', 'TRUNCATE'), 'Authenticated must not truncate workspaces';
  assert not has_function_privilege('anon', 'public.save_workspace(bigint,jsonb,text)', 'EXECUTE'), 'Anon must not call save_workspace';
  assert (select relrowsecurity from pg_class where oid='public.workspaces'::regclass), 'RLS must be enabled';
  assert exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and tablename='workspaces'), 'Realtime publication missing';
end $$;
rollback;
select 'PASS: create, update, stale-write rejection, cross-account read/write isolation, anonymous restrictions, RLS and realtime publication' as verification;
