-- BlitzBook on Supabase: run this once in the project's SQL Editor (Dashboard -> SQL Editor -> New query -> Run).
-- It is safe to run again.
--
-- books    : every record of every account, keyed the way the sync server keys them ("inv:0007", "item:tea",
--            "contact:<id>", "company", "sub" ...). d is the record (null = deleted), r the revision stamped on
--            every write, so a device asks for "everything with r greater than what I last saw".
-- profiles : name, mobile number and email of an account, so that logging in with the mobile number finds the
--            email Supabase Auth knows, and so that one mobile number / email belongs to one account.

create table if not exists public.books (
  user_id    uuid not null references auth.users (id) on delete cascade,
  k          text not null,
  d          jsonb,
  r          bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key (user_id, k)
);
create sequence if not exists public.books_rev;
create index if not exists books_user_r on public.books (user_id, r);

create or replace function public.books_stamp() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  new.r := nextval('public.books_rev');
  new.updated_at := now();
  return new;
end $$;
drop trigger if exists books_stamp on public.books;
create trigger books_stamp before insert or update on public.books
  for each row execute function public.books_stamp();

alter table public.books enable row level security;
drop policy if exists "own books" on public.books;
create policy "own books" on public.books
  for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table if not exists public.profiles (
  id         uuid primary key references auth.users (id) on delete cascade,
  name       text,
  phone      text unique,
  email      text unique,
  created_at timestamptz not null default now()
);
alter table public.profiles enable row level security;
drop policy if exists "own profile" on public.profiles;
create policy "own profile" on public.profiles
  for all to authenticated using (auth.uid() = id) with check (auth.uid() = id);

-- What to sign in with behind a mobile number or an email: {"email": ..., "phone": ...} (the email or phone the
-- caller typed, or the account's other one), null when no account has it. Callable before signing in.
create or replace function public.identity_login(identity text) returns jsonb
language sql security definer stable set search_path = public as $$
  select jsonb_build_object('email', coalesce(p.email, ''), 'phone', coalesce(p.phone, '')) from public.profiles p
  where p.phone = trim(identity) or lower(p.email) = lower(trim(identity))
  limit 1
$$;
grant execute on function public.identity_login(text) to anon, authenticated;

-- Keep profiles in step with Auth once an account is confirmed (email changes, accounts made in the dashboard).
-- Unconfirmed sign-ups (an OTP that was never entered) get no profile, so the number / email stays free.
create or replace function public.profile_sync() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.email_confirmed_at is null and new.phone_confirmed_at is null then return new; end if;
  insert into public.profiles (id, name, phone, email)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'name', ''), nullif(new.raw_user_meta_data ->> 'phone', ''), lower(new.email))
  on conflict (id) do update set email = lower(excluded.email), name = coalesce(nullif(excluded.name, ''), public.profiles.name);
  return new;
end $$;
drop trigger if exists profile_sync on auth.users;
create trigger profile_sync after insert or update of email, raw_user_meta_data, email_confirmed_at, phone_confirmed_at on auth.users
  for each row execute function public.profile_sync();

grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on public.books, public.profiles to authenticated;
