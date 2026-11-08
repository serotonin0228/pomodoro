-- pomodoro: one account-owned document with optimistic concurrency.
create table public.workspaces (
  user_id uuid primary key references auth.users(id) on delete cascade,
  revision bigint not null default 1 check (revision > 0),
  payload jsonb not null check (jsonb_typeof(payload) = 'object'),
  writer text not null check (char_length(writer) between 1 and 128),
  updated_at timestamptz not null default now()
);
alter table public.workspaces enable row level security;
revoke all on public.workspaces from public, anon, authenticated;
grant select, insert, update on public.workspaces to authenticated;
revoke delete on public.workspaces from authenticated;
create policy "Read own workspace" on public.workspaces for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "Insert own workspace" on public.workspaces for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "Update own workspace" on public.workspaces for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Invoker privileges keep RLS active, including inside this function.
create function public.save_workspace(expected_revision bigint, new_payload jsonb, device_id text)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare current_row public.workspaces; result_row public.workspaces;
begin
  if auth.uid() is null then raise exception 'Authentication required' using errcode = '42501'; end if;
  if expected_revision is null or expected_revision < 0 then raise exception 'Invalid revision'; end if;
  if device_id is null or char_length(device_id) not between 1 and 128 then raise exception 'Invalid device'; end if;
  if new_payload is null or jsonb_typeof(new_payload) <> 'object'
    or jsonb_typeof(new_payload->'timer') is distinct from 'object'
    or jsonb_typeof(new_payload->'settings') is distinct from 'object'
    or jsonb_typeof(new_payload->'records') is distinct from 'array'
    or jsonb_typeof(new_payload->'segments') is distinct from 'array'
    then raise exception 'Invalid workspace payload'; end if;
  if octet_length(new_payload::text) > 2000000 then raise exception 'Workspace limit reached; export or archive records'; end if;
  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text, 0));
  select * into current_row from public.workspaces where user_id = auth.uid() for update;
  if found then
    if current_row.revision <> expected_revision then raise exception 'SYNC_CONFLICT'; end if;
    update public.workspaces set payload = new_payload, revision = revision + 1, writer = device_id, updated_at = clock_timestamp()
    where user_id = auth.uid() returning * into result_row;
  else
    if expected_revision <> 0 then raise exception 'SYNC_CONFLICT'; end if;
    insert into public.workspaces(user_id, payload, writer) values(auth.uid(), new_payload, device_id) returning * into result_row;
  end if;
  return to_jsonb(result_row);
end $$;
revoke all on function public.save_workspace(bigint,jsonb,text) from public, anon;
grant execute on function public.save_workspace(bigint,jsonb,text) to authenticated;
create function public.server_time() returns double precision language sql volatile security invoker set search_path = '' as $$
  select extract(epoch from clock_timestamp())::double precision * 1000;
$$;
revoke all on function public.server_time() from public, anon;
grant execute on function public.server_time() to authenticated;
alter publication supabase_realtime add table public.workspaces;
