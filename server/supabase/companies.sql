-- Companies, company groups and members with roles (run in the SQL Editor after schema.sql; safe to run again).
--
-- Until now one account was one set of books: public.books is keyed by (user_id, k). From here on books.user_id is
-- the id of the COMPANY a record belongs to. An account's first company keeps the account's own id, so every row
-- that exists today, every older app and portal, and the MCP server go on working unchanged. Further companies an
-- account creates get an id of their own in public.companies, with the owner, a name and a group name (the group
-- is what the consolidated statements are drawn for).
--
-- Other accounts join a company as members with a role (public.company_members):
--   owner       the account that made the company: everything, including members and deleting the company
--   admin       everything in the books, the company profile and the members
--   accountant  every record of the books (invoices, purchases, expenses, journal, receipts, payments, parties,
--               items); not the company profile, members or subscription
--   sales       sales invoices, delivery challans, credit / debit notes, receipts, customers and items only
--   manager     the HR records (emp:, att:, ts:, rb:, pay:, hr) and, in the portal, approves timesheets and reimbursements
--   hr          the HR records only (employees, attendance, timesheets, reimbursements, payroll, HR settings); reads
--               nothing else of the books but the company profile and the subscription
--   viewer      looks at everything, changes nothing
-- The rules below enforce the role on the server; the app and the portal hide what a role cannot do.
--
-- A client working on a company other than its own sends the header "X-Company: <company id>" (and filters by
-- user_id = that id). Without the header an account sees only its own books, so a device running an older
-- version never receives another company's records by accident.
--
-- The subscription stays with the OWNER's account: everybody working in a company runs on the owner's plan. A
-- member may read the owner's "sub" record while the header names one of the owner's companies.

create table if not exists public.companies (
  id         uuid primary key default gen_random_uuid(),
  owner_id   uuid not null references auth.users (id) on delete cascade,
  name       text not null default '',
  group_name text not null default '',
  created_at timestamptz not null default now()
);
create index if not exists companies_owner on public.companies (owner_id);
alter table public.companies enable row level security;

create table if not exists public.company_members (
  company_id uuid not null references public.companies (id) on delete cascade,
  user_id    uuid not null references auth.users (id) on delete cascade,
  role       text not null check (role in ('admin', 'accountant', 'sales', 'manager', 'hr', 'viewer')),
  added_by   uuid,
  created_at timestamptz not null default now(),
  primary key (company_id, user_id)
);
create index if not exists company_members_user on public.company_members (user_id);
alter table public.company_members enable row level security;
-- The HR role (app 1.10): added to the check of a table made by an earlier version of this file
alter table public.company_members drop constraint if exists company_members_role_check;
alter table public.company_members add constraint company_members_role_check check (role in ('admin', 'accountant', 'sales', 'manager', 'hr', 'viewer'));

-- Clients reach both tables through the functions below only
revoke all on public.companies, public.company_members from anon, authenticated;

-- books.user_id may now be a company id, which is not an auth user: the foreign key has to go. A company's books
-- are removed with the company (trigger below); an account's own books go with the account the same way, since
-- the account's first company row carries the account's id and is removed with it (owner_id cascade).
alter table public.books drop constraint if exists books_user_id_fkey;

create or replace function public.companies_cleanup() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  delete from public.books where user_id = old.id;
  return old;
end $$;
drop trigger if exists companies_cleanup on public.companies;
create trigger companies_cleanup after delete on public.companies
  for each row execute function public.companies_cleanup();

-- Accounts made before this file had no company row: when one is deleted, its books go too
create or replace function public.users_cleanup_books() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  delete from public.books where user_id = old.id;
  return old;
end $$;
drop trigger if exists users_cleanup_books on auth.users;
create trigger users_cleanup_books after delete on auth.users
  for each row execute function public.users_cleanup_books();

-- ---------------------------------------------------------------- who may do what

-- The company named in the request header X-Company, null when there is none (or it is not a uuid)
create or replace function public.current_company() returns uuid
language plpgsql stable as $$
declare h text;
begin
  h := nullif(trim(coalesce(current_setting('request.headers', true)::json ->> 'x-company', '')), '');
  if h is null then return null; end if;
  return h::uuid;
exception when others then return null;
end $$;

-- The signed-in account's role in a company: 'owner', a member's role, or null
create or replace function public.company_role(cid uuid) returns text
language plpgsql stable security definer set search_path = public as $$
declare r text;
begin
  if auth.uid() is null or cid is null then return null; end if;
  if cid = auth.uid() then return 'owner'; end if;
  if exists (select 1 from public.companies where id = cid and owner_id = auth.uid()) then return 'owner'; end if;
  select role into r from public.company_members where company_id = cid and user_id = auth.uid();
  return r;
end $$;
grant execute on function public.company_role(uuid) to authenticated;

-- The owner of a company (clients have no direct access to the companies table, so the rules look it up here)
create or replace function public.company_owner(cid uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select owner_id from public.companies where id = cid
$$;
grant execute on function public.company_owner(uuid) to authenticated;

-- Whether the signed-in account may write record k (with content d, null for a deletion) in company cid
create or replace function public.books_may_write(cid uuid, k text, d jsonb) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare r text := public.company_role(cid);
begin
  if r is null or k = 'sub' then return false; end if;       -- the subscription lives with the owner's account
  if r in ('owner', 'admin') then return true; end if;
  if r = 'accountant' then return k <> 'company'; end if;
  if r in ('hr', 'manager') then return k like 'emp:%' or k like 'att:%' or k like 'ts:%' or k like 'rb:%' or k like 'pay:%' or k = 'hr'; end if;
  if r = 'sales' then
    return k like 'inv:%' or k like 'dc:%' or k like 'note:%' or k like 'contact:%' or k like 'item:%'
        or (k like 'jrn:%' and (d is null or coalesce(d ->> 'kind', '') = 'Receipt'));
  end if;
  return false;
end $$;

-- Whether the signed-in account may read record k of company cid: every member reads the books, except the HR
-- role, which sees the HR records, the company profile and the subscription only
create or replace function public.books_may_read(cid uuid, k text) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare r text := public.company_role(cid);
begin
  if r is null then return false; end if;
  if r in ('hr', 'manager') then return k in ('company', 'sub', 'hr') or k like 'emp:%' or k like 'att:%' or k like 'ts:%' or k like 'rb:%' or k like 'pay:%'; end if;
  return true;
end $$;

drop policy if exists "own books" on public.books;
drop policy if exists "books select" on public.books;
drop policy if exists "books insert" on public.books;
drop policy if exists "books update" on public.books;
drop policy if exists "books delete" on public.books;
create policy "books select" on public.books for select to authenticated using (
  user_id = auth.uid()
  or (user_id = public.current_company() and public.books_may_read(user_id, k))
  or (k = 'sub' and public.company_role(public.current_company()) is not null
      and user_id = public.company_owner(public.current_company()))
);
create policy "books insert" on public.books for insert to authenticated with check (
  user_id = auth.uid() or (user_id = public.current_company() and public.books_may_write(user_id, k, d))
);
create policy "books update" on public.books for update to authenticated
  using (user_id = auth.uid() or (user_id = public.current_company() and public.books_may_write(user_id, k, d)))
  with check (user_id = auth.uid() or (user_id = public.current_company() and public.books_may_write(user_id, k, d)));
create policy "books delete" on public.books for delete to authenticated using (
  user_id = auth.uid() or (user_id = public.current_company() and public.books_may_write(user_id, k, d))
);

-- The company's name in public.companies follows its company profile record
create or replace function public.books_company_name() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.k = 'company' and new.d is not null then
    update public.companies set name = left(coalesce(new.d ->> 'company_name', ''), 120) where id = new.user_id;
  end if;
  return new;
end $$;
drop trigger if exists books_company_name on public.books;
create trigger books_company_name after insert or update on public.books
  for each row execute function public.books_company_name();

-- Whether an account is on a yearly plan or longer (its "sub" record: a yearly date, or more than 300 days of validity
-- left, which only a yearly or longer plan gives). Companies, groups and members come with such a plan.
create or replace function public.is_yearly(uid uuid) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare d jsonb; now_ms bigint := (extract(epoch from now()) * 1000)::bigint;
begin
  select b.d into d from public.books b where b.user_id = uid and b.k = 'sub';
  if d is null then return false; end if;
  return coalesce((d ->> 'yearly_until')::bigint, 0) > now_ms or coalesce((d ->> 'valid_until')::bigint, 0) - now_ms > 300::bigint * 24 * 3600 * 1000;
exception when others then return false;
end $$;

-- ---------------------------------------------------------------- the account's companies

-- Makes sure the signed-in account has its first company row (id = the account id)
create or replace function public.ensure_primary_company() returns void
language plpgsql security definer set search_path = public as $$
declare nm text;
begin
  if auth.uid() is null then return; end if;
  if exists (select 1 from public.companies where id = auth.uid()) then return; end if;
  select left(coalesce(d ->> 'company_name', ''), 120) into nm from public.books where user_id = auth.uid() and k = 'company';
  insert into public.companies (id, owner_id, name) values (auth.uid(), auth.uid(), coalesce(nm, '')) on conflict (id) do nothing;
end $$;

-- Every company the signed-in account owns or is a member of:
-- [{"id", "name", "group_name", "owner_id", "owner_name", "role", "primary", "members"}], own companies first
create or replace function public.my_companies() returns jsonb
language plpgsql security definer set search_path = public as $$
declare out jsonb;
begin
  if auth.uid() is null then return '[]'::jsonb; end if;
  perform public.ensure_primary_company();
  select coalesce(jsonb_agg(jsonb_build_object(
      'id', c.id, 'name', c.name, 'group_name', c.group_name, 'owner_id', c.owner_id,
      'owner_name', coalesce((select p.name from public.profiles p where p.id = c.owner_id), ''),
      'role', case when c.owner_id = auth.uid() then 'owner' else m.role end,
      'primary', c.id = c.owner_id,
      'members', (select count(*) from public.company_members x where x.company_id = c.id),
      'created_at', c.created_at) order by (c.owner_id = auth.uid()) desc, (c.id = c.owner_id) desc, c.group_name, c.name), '[]'::jsonb)
  into out
  from public.companies c
  left join public.company_members m on m.company_id = c.id and m.user_id = auth.uid()
  where c.owner_id = auth.uid() or m.user_id is not null;
  return out;
end $$;
grant execute on function public.my_companies() to authenticated;

-- A new company owned by the signed-in account: {"id", "name", "group_name"} or {"error"}. An account may own 25.
create or replace function public.create_company(name_in text, group_in text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare nm text := left(trim(coalesce(name_in, '')), 120); g text := left(trim(coalesce(group_in, '')), 80); new_id uuid;
begin
  if auth.uid() is null then return jsonb_build_object('error', 'Sign in first'); end if;
  if nm = '' then return jsonb_build_object('error', 'Enter the company name'); end if;
  if not public.is_yearly(auth.uid()) then return jsonb_build_object('error', 'Companies, groups and members come with the yearly plan and longer'); end if;
  perform public.ensure_primary_company();
  if (select count(*) from public.companies where owner_id = auth.uid()) >= 25 then
    return jsonb_build_object('error', 'An account can own 25 companies');
  end if;
  insert into public.companies (owner_id, name, group_name) values (auth.uid(), nm, g) returning id into new_id;
  -- The company profile record, so the name shows on every device straight away
  insert into public.books (user_id, k, d) values (new_id, 'company', jsonb_build_object('company_name', nm))
    on conflict (user_id, k) do nothing;
  return jsonb_build_object('id', new_id, 'name', nm, 'group_name', g);
end $$;
grant execute on function public.create_company(text, text) to authenticated;

-- Renames a company or moves it to another group (owner or admin). The name is only applied when the
-- company has no profile record yet; otherwise the profile's name wins (see books_company_name).
create or replace function public.update_company(cid uuid, name_in text, group_in text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare nm text := left(trim(coalesce(name_in, '')), 120); g text := left(trim(coalesce(group_in, '')), 80);
begin
  if public.company_role(cid) not in ('owner', 'admin') then return jsonb_build_object('error', 'Only the owner or an admin can change the company'); end if;
  update public.companies set group_name = g, name = case when nm <> '' and not exists (select 1 from public.books b where b.user_id = cid and b.k = 'company' and coalesce(b.d ->> 'company_name', '') <> '') then nm else name end
  where id = cid;
  return jsonb_build_object('ok', true);
end $$;
grant execute on function public.update_company(uuid, text, text) to authenticated;

-- Deletes a company and all its books (owner only; never the account's first company)
create or replace function public.delete_company(cid uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null or cid = auth.uid() then return jsonb_build_object('error', 'The first company of an account cannot be deleted'); end if;
  if not exists (select 1 from public.companies where id = cid and owner_id = auth.uid()) then return jsonb_build_object('error', 'Only the owner can delete a company'); end if;
  delete from public.companies where id = cid;   -- books go with it (companies_cleanup)
  return jsonb_build_object('ok', true);
end $$;
grant execute on function public.delete_company(uuid) to authenticated;

-- ---------------------------------------------------------------- members

-- The people with access to a company (any member may look): [{"user_id", "name", "phone", "email", "role"}], the owner first
create or replace function public.list_members(cid uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare out jsonb; own uuid;
begin
  if public.company_role(cid) is null then return jsonb_build_object('error', 'Not a member of this company'); end if;
  select owner_id into own from public.companies where id = cid;
  if own is null then own := cid; end if;
  select coalesce(jsonb_agg(x order by (x ->> 'role' = 'owner') desc, x ->> 'name'), '[]'::jsonb) into out from (
    select jsonb_build_object('user_id', p.id, 'name', coalesce(p.name, ''), 'phone', coalesce(p.phone, ''), 'email', coalesce(p.email, ''), 'role', 'owner') as x
    from public.profiles p where p.id = own
    union all
    select jsonb_build_object('user_id', m.user_id, 'name', coalesce(p.name, ''), 'phone', coalesce(p.phone, ''), 'email', coalesce(p.email, ''), 'role', m.role)
    from public.company_members m left join public.profiles p on p.id = m.user_id where m.company_id = cid
  ) s;
  return out;
end $$;
grant execute on function public.list_members(uuid) to authenticated;

-- Gives an account (found by its mobile number or email) a role in a company, or changes the role it has
-- (owner or admin). The account must already be registered with BlitzBook. {"ok": true, "name": ...} or {"error"}.
create or replace function public.set_member(cid uuid, identity text, role_in text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare p public.profiles%rowtype; r text := lower(trim(coalesce(role_in, ''))); own uuid;
begin
  if public.company_role(cid) not in ('owner', 'admin') then return jsonb_build_object('error', 'Only the owner or an admin can manage members'); end if;
  if not public.is_yearly(coalesce((select owner_id from public.companies where id = cid), cid)) then return jsonb_build_object('error', 'Members come with the yearly plan and longer (the owner of the company has to be on it)'); end if;
  if r not in ('admin', 'accountant', 'sales', 'manager', 'hr', 'viewer') then return jsonb_build_object('error', 'Role must be admin, accountant, sales, manager, hr or viewer'); end if;
  select * into p from public.profiles where phone = trim(identity) or lower(email) = lower(trim(identity)) limit 1;
  if not found then return jsonb_build_object('error', 'No BlitzBook account with that mobile number or email. Ask them to register first.'); end if;
  select owner_id into own from public.companies where id = cid;
  if p.id = coalesce(own, cid) then return jsonb_build_object('error', 'That is the owner of the company'); end if;
  if (select count(*) from public.company_members where company_id = cid) >= 50 and not exists (select 1 from public.company_members where company_id = cid and user_id = p.id) then
    return jsonb_build_object('error', 'A company can have 50 members');
  end if;
  insert into public.company_members (company_id, user_id, role, added_by) values (cid, p.id, r, auth.uid())
    on conflict (company_id, user_id) do update set role = excluded.role;
  return jsonb_build_object('ok', true, 'user_id', p.id, 'name', coalesce(p.name, ''), 'role', r);
end $$;
grant execute on function public.set_member(uuid, text, text) to authenticated;

-- Removes a member (owner or admin), or oneself from a company one was invited to
create or replace function public.remove_member(cid uuid, member uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then return jsonb_build_object('error', 'Sign in first'); end if;
  if member <> auth.uid() and public.company_role(cid) not in ('owner', 'admin') then return jsonb_build_object('error', 'Only the owner or an admin can remove members'); end if;
  delete from public.company_members where company_id = cid and user_id = member;
  return jsonb_build_object('ok', true);
end $$;
grant execute on function public.remove_member(uuid, uuid) to authenticated;
