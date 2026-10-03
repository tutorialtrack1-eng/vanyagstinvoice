-- API keys for the MCP server (run in the SQL Editor after schema.sql; safe to run again).
-- The Edge Function server/supabase/functions/mcp lets an AI assistant (Claude and other MCP clients) work with an
-- account's books. It is opened with an API key the account makes in the portal (AI Access): the key is shown once,
-- only its SHA-256 is kept here. scope is what the key may do: 'read' only looks, 'write' can also add invoices,
-- contacts and items. Deleting the row revokes the key.
create table if not exists public.api_keys (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users (id) on delete cascade,
  name         text not null,
  prefix       text not null,                  -- the first characters of the key, to tell keys apart in the list
  key_hash     text not null unique,           -- sha256 of the key, hex
  scope        text not null default 'read' check (scope in ('read', 'write')),
  created_at   timestamptz not null default now(),
  last_used_at timestamptz
);
create index if not exists api_keys_user on public.api_keys (user_id);

alter table public.api_keys enable row level security;
drop policy if exists "own api keys" on public.api_keys;
create policy "own api keys" on public.api_keys for select to authenticated using (auth.uid() = user_id);
drop policy if exists "revoke own api keys" on public.api_keys;
create policy "revoke own api keys" on public.api_keys for delete to authenticated using (auth.uid() = user_id);
-- Clients list and delete their keys; they never read the hash and never insert a key themselves
revoke all on public.api_keys from anon, authenticated;
grant select (id, user_id, name, prefix, scope, created_at, last_used_at), delete on public.api_keys to authenticated;

-- Makes a key for the signed-in account: {"id": ..., "key": "bbk_...", "name": ..., "scope": ..., "prefix": ...}.
-- The key itself is only ever in this answer. {"error": "..."} when not signed in or at the limit of 10 keys.
create or replace function public.create_api_key(name_in text, scope_in text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  k text := 'bbk_' || replace(gen_random_uuid()::text, '-', '') || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16);
  n text := left(coalesce(nullif(trim(name_in), ''), 'API key'), 60);
  s text := case when lower(coalesce(scope_in, '')) = 'write' then 'write' else 'read' end;
  new_id uuid;
begin
  if auth.uid() is null then return jsonb_build_object('error', 'Sign in first'); end if;
  if (select count(*) from public.api_keys where user_id = auth.uid()) >= 10 then
    return jsonb_build_object('error', 'An account can have 10 API keys. Revoke one you no longer use first.');
  end if;
  insert into public.api_keys (user_id, name, prefix, key_hash, scope)
  values (auth.uid(), n, left(k, 12), encode(sha256(convert_to(k, 'UTF8')), 'hex'), s)
  returning id into new_id;
  return jsonb_build_object('id', new_id, 'key', k, 'name', n, 'scope', s, 'prefix', left(k, 12));
end $$;
revoke all on function public.create_api_key(text, text) from public, anon;
grant execute on function public.create_api_key(text, text) to authenticated;
