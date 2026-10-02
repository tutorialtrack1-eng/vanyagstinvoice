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

-- The codes handed out (made 02/10/2026): ten of each plan. Hand one out per customer; it works once. Add more rows
-- the same way (16 letters / digits, no dashes in the table; the app and the portal ignore dashes when a code is typed).
-- Plans: monthly 30 days, yearly 365, 2 years 730, 5 years 1825. Codes loaded by an earlier version of this file stay valid.
insert into public.activation_codes (code, days, note) values
  ('LVH63LWVPB5FY42U',   30, 'Monthly plan - LVH6-3LWV-PB5F-Y42U'),
  ('P8MT3SD4TXQJ5PAK',   30, 'Monthly plan - P8MT-3SD4-TXQJ-5PAK'),
  ('F9F8VSUFK6BZS9PH',   30, 'Monthly plan - F9F8-VSUF-K6BZ-S9PH'),
  ('DF74JR2GUU69VC54',   30, 'Monthly plan - DF74-JR2G-UU69-VC54'),
  ('56XWW5M52TR3XF2T',   30, 'Monthly plan - 56XW-W5M5-2TR3-XF2T'),
  ('YX27QWYXGLUCLVQB',   30, 'Monthly plan - YX27-QWYX-GLUC-LVQB'),
  ('7VZS4EXS64EYPU2N',   30, 'Monthly plan - 7VZS-4EXS-64EY-PU2N'),
  ('LNF8SASDVTXLK5QH',   30, 'Monthly plan - LNF8-SASD-VTXL-K5QH'),
  ('NRURYVSV7BF3HPDS',   30, 'Monthly plan - NRUR-YVSV-7BF3-HPDS'),
  ('NFQMACX8TPNU52CA',   30, 'Monthly plan - NFQM-ACX8-TPNU-52CA'),
  ('ZJYKB74NQ4U72E9J',  365, 'Yearly plan - ZJYK-B74N-Q4U7-2E9J'),
  ('QHZNRWGN7FAKSVPH',  365, 'Yearly plan - QHZN-RWGN-7FAK-SVPH'),
  ('EFVQATTYUXB7JJ52',  365, 'Yearly plan - EFVQ-ATTY-UXB7-JJ52'),
  ('K78TLFZZYL3VDEBH',  365, 'Yearly plan - K78T-LFZZ-YL3V-DEBH'),
  ('CFQ5LN4GF74QGX5E',  365, 'Yearly plan - CFQ5-LN4G-F74Q-GX5E'),
  ('HHA6ULUEVLRE2C3Z',  365, 'Yearly plan - HHA6-ULUE-VLRE-2C3Z'),
  ('ULZGY2UMJHN2668V',  365, 'Yearly plan - ULZG-Y2UM-JHN2-668V'),
  ('TNL2CHXYVSLQ59U4',  365, 'Yearly plan - TNL2-CHXY-VSLQ-59U4'),
  ('FPM2G77VCFJ7Z3P8',  365, 'Yearly plan - FPM2-G77V-CFJ7-Z3P8'),
  ('K555U6ZL4W5N2A36',  365, 'Yearly plan - K555-U6ZL-4W5N-2A36'),
  ('6GTDSRLTMPL2QRHR',  730, '2 years plan - 6GTD-SRLT-MPL2-QRHR'),
  ('JVU2TQJMWP434ZNF',  730, '2 years plan - JVU2-TQJM-WP43-4ZNF'),
  ('9GF29XA5ARFBAP3Q',  730, '2 years plan - 9GF2-9XA5-ARFB-AP3Q'),
  ('JCATKZZKZT22BUZF',  730, '2 years plan - JCAT-KZZK-ZT22-BUZF'),
  ('7EQB7GCR7H8DKFHP',  730, '2 years plan - 7EQB-7GCR-7H8D-KFHP'),
  ('XHHESFSZW4844FA9',  730, '2 years plan - XHHE-SFSZ-W484-4FA9'),
  ('STBNR3YDH6CJNE38',  730, '2 years plan - STBN-R3YD-H6CJ-NE38'),
  ('HG4LHFQLSUWGKH5W',  730, '2 years plan - HG4L-HFQL-SUWG-KH5W'),
  ('3QQ6U5ED9GWATV5R',  730, '2 years plan - 3QQ6-U5ED-9GWA-TV5R'),
  ('HBXG7Q5H5737TJY4',  730, '2 years plan - HBXG-7Q5H-5737-TJY4'),
  ('R7L6FG9TWA2XSNMG', 1825, '5 years plan - R7L6-FG9T-WA2X-SNMG'),
  ('XD6E8VALDC9BQ7XV', 1825, '5 years plan - XD6E-8VAL-DC9B-Q7XV'),
  ('KBAFUYWKL37KBGAN', 1825, '5 years plan - KBAF-UYWK-L37K-BGAN'),
  ('LL8G2Y42QMA935SJ', 1825, '5 years plan - LL8G-2Y42-QMA9-35SJ'),
  ('FHKNGZ4HQ6K6LG5R', 1825, '5 years plan - FHKN-GZ4H-Q6K6-LG5R'),
  ('4LDDNPCPSRWAB9FL', 1825, '5 years plan - 4LDD-NPCP-SRWA-B9FL'),
  ('JQ3K6KEFY2DKKEG7', 1825, '5 years plan - JQ3K-6KEF-Y2DK-KEG7'),
  ('JSX2YZFUPMCQMYGX', 1825, '5 years plan - JSX2-YZFU-PMCQ-MYGX'),
  ('6CV6BFLT5YQG9DMB', 1825, '5 years plan - 6CV6-BFLT-5YQG-9DMB'),
  ('EVYBQC3XDLB8JTBA', 1825, '5 years plan - EVYB-QC3X-DLB8-JTBA')
on conflict (code) do nothing;
