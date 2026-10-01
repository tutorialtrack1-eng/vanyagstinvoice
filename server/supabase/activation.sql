-- Activation codes redeemed through Supabase (run in the SQL Editor after schema.sql; safe to run again).
-- A code is a 16-character string worth a number of days and can be used once, by any account. The validity
-- starts the moment the code is entered and runs for that many days. The table is not readable by clients;
-- redeem_code() checks and marks a code for the signed-in account.
create table if not exists public.activation_codes (
  code       text primary key,
  days       int not null,
  note       text,
  created_at timestamptz not null default now(),
  used_by    uuid references auth.users (id) on delete set null,
  used_at    timestamptz
);
alter table public.activation_codes enable row level security;

-- Returns the plan length in days; -1 unknown code, -2 already used, -3 not signed in
create or replace function public.redeem_code(code_in text) returns int
language plpgsql security definer set search_path = public as $$
declare
  k text := upper(regexp_replace(coalesce(code_in, ''), '[^A-Za-z0-9]', '', 'g'));
  c public.activation_codes%rowtype;
begin
  if auth.uid() is null then return -3; end if;
  select * into c from public.activation_codes where code = k;
  if not found then return -1; end if;
  if c.used_by is not null then return -2; end if;
  update public.activation_codes set used_by = auth.uid(), used_at = now() where code = k;
  return c.days;
end $$;
grant execute on function public.redeem_code(text) to authenticated;

-- The first batch of codes (made 02/10/2026). Hand one out per customer; it works once. Add more rows the
-- same way (16 letters / digits, no dashes in the table; the app and the portal ignore dashes when a code is typed).
insert into public.activation_codes (code, days, note) values
  ('XSL34YPJUAFYASEH',  30, '1 month - XSL3-4YPJ-UAFY-ASEH'),
  ('U89HH4VMGXFD8456',  30, '1 month - U89H-H4VM-GXFD-8456'),
  ('VQ6CHCCM5SX5HE2H',  30, '1 month - VQ6C-HCCM-5SX5-HE2H'),
  ('HJVTK9L8BJHLV5EA',  30, '1 month - HJVT-K9L8-BJHL-V5EA'),
  ('G76UYLSWRFUCX7DN',  30, '1 month - G76U-YLSW-RFUC-X7DN'),
  ('688DWZY4S6R9KE4U',  90, '3 months - 688D-WZY4-S6R9-KE4U'),
  ('GUEPSTBQA57EX8JS',  90, '3 months - GUEP-STBQ-A57E-X8JS'),
  ('XV5YDC4QZZFH99ST',  90, '3 months - XV5Y-DC4Q-ZZFH-99ST'),
  ('2VDUDQSHCJ2XCSNU',  90, '3 months - 2VDU-DQSH-CJ2X-CSNU'),
  ('67TMKDKJEKGNBJTF',  90, '3 months - 67TM-KDKJ-EKGN-BJTF'),
  ('K8FZFN9B7X2ZD4RM', 180, '6 months - K8FZ-FN9B-7X2Z-D4RM'),
  ('9ANQTTHH4726TXPV', 180, '6 months - 9ANQ-TTHH-4726-TXPV'),
  ('HUHRSRDRJHL6LKMH', 180, '6 months - HUHR-SRDR-JHL6-LKMH'),
  ('D9VVMAR5GZ4PG4M3', 180, '6 months - D9VV-MAR5-GZ4P-G4M3'),
  ('LWCJKBGFCF38GJG5', 365, '1 year - LWCJ-KBGF-CF38-GJG5'),
  ('SM28S7MTLXD3GW8N', 365, '1 year - SM28-S7MT-LXD3-GW8N'),
  ('82KDL59W7F2E3A6X', 365, '1 year - 82KD-L59W-7F2E-3A6X'),
  ('79LW2328ZUUHML4Q', 365, '1 year - 79LW-2328-ZUUH-ML4Q'),
  ('XXPACULHJS9S35DE', 730, '2 years - XXPA-CULH-JS9S-35DE'),
  ('ESWW6ADNA28NVQLJ', 730, '2 years - ESWW-6ADN-A28N-VQLJ')
on conflict (code) do nothing;
