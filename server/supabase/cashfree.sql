-- Payments through Cashfree (run in the SQL Editor after schema.sql; safe to run again).
-- The Edge Function server/supabase/functions/cashfree creates a Cashfree payment link for a plan and records it in
-- payments; when Cashfree reports the link paid (webhook, or the client asking for the status) the function writes a
-- grant: the days or invoices the account has bought. Clients collect their grants with claim_grants() (on login,
-- after every sync round and on return from the payment page) and extend the validity or the invoice pack with
-- them, exactly as they do with an activation code. Nothing here is written by clients directly.
create table if not exists public.payments (
  link_id    text primary key,
  user_id    uuid not null references auth.users (id) on delete cascade,
  plan       text not null,
  days       int not null default 0,
  invoices   int not null default 0,
  amount     numeric(10,2) not null,
  status     text not null default 'created',   -- created | paid | failed
  link_url   text,
  created_at timestamptz not null default now(),
  paid_at    timestamptz,
  raw        jsonb
);
alter table public.payments enable row level security;
drop policy if exists "own payments" on public.payments;
create policy "own payments" on public.payments for select to authenticated using (auth.uid() = user_id);

create table if not exists public.grants (
  id         bigserial primary key,
  user_id    uuid not null references auth.users (id) on delete cascade,
  link_id    text unique references public.payments (link_id) on delete set null,
  days       int not null default 0,
  invoices   int not null default 0,
  note       text,
  created_at timestamptz not null default now(),
  claimed_at timestamptz
);
alter table public.grants enable row level security;
drop policy if exists "own grants" on public.grants;
create policy "own grants" on public.grants for select to authenticated using (auth.uid() = user_id);

-- The signed-in account's unclaimed grants, marked claimed: [{"id":1,"days":30,"invoices":0,"note":"..."}]
create or replace function public.claim_grants() returns jsonb
language plpgsql security definer set search_path = public as $$
declare out jsonb;
begin
  if auth.uid() is null then return '[]'::jsonb; end if;
  with c as (
    update public.grants set claimed_at = now()
    where user_id = auth.uid() and claimed_at is null
    returning id, days, invoices, note
  )
  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'days', days, 'invoices', invoices, 'note', note)), '[]'::jsonb) into out from c;
  return out;
end $$;
grant execute on function public.claim_grants() to authenticated;
grant select on public.payments, public.grants to authenticated;
