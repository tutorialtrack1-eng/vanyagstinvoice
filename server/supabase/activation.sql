-- Activation codes redeemed through Supabase (run in the SQL Editor after schema.sql; safe to run again).
-- A code is a 16-character string worth a number of days and can be used once, by any account. The validity
-- is added to the end of the account's current validity (or starts the day the code is entered when it has lapsed). The table is not readable by clients;
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

-- Codes come in two kinds: a plan for a number of days, or a pack of invoices (credit and debit notes count
-- too) with no end date. invoices is 0 for a time plan, days is 0 for an invoice pack.
alter table public.activation_codes add column if not exists invoices int not null default 0;

-- Returns the plan length in days; -1 unknown code, -2 already used, -3 not signed in. Older clients: an invoice
-- pack answers 0 here, which they treat as an unknown code, so they do not swallow a pack they cannot count.
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
  if c.days <= 0 then return -1; end if;
  update public.activation_codes set used_by = auth.uid(), used_at = now() where code = k;
  return c.days;
end $$;
grant execute on function public.redeem_code(text) to authenticated;

-- The same for clients that know both kinds: {"days": 30, "invoices": 0} or {"days": 0, "invoices": 20};
-- {"error": -1} unknown code, {"error": -2} already used, {"error": -3} not signed in
create or replace function public.redeem_code_v2(code_in text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  k text := upper(regexp_replace(coalesce(code_in, ''), '[^A-Za-z0-9]', '', 'g'));
  c public.activation_codes%rowtype;
begin
  if auth.uid() is null then return jsonb_build_object('error', -3); end if;
  select * into c from public.activation_codes where code = k;
  if not found then return jsonb_build_object('error', -1); end if;
  if c.used_by is not null then return jsonb_build_object('error', -2); end if;
  update public.activation_codes set used_by = auth.uid(), used_at = now() where code = k;
  return jsonb_build_object('days', c.days, 'invoices', c.invoices);
end $$;
grant execute on function public.redeem_code_v2(text) to authenticated;

-- The codes handed out (made 02/10/2026): ten of each plan. Hand one out per customer; it works once. Add more rows
-- the same way (16 letters / digits, no dashes in the table; the app and the portal ignore dashes when a code is typed).
-- Plans: monthly 30 days, yearly 365, 2 years 730, 5 years 1825; packs of 20 and 50 invoices. Codes loaded by an earlier
-- version of this file stay valid.
insert into public.activation_codes (code, days, note) values
  ('LVH63LWVPB5FY42U',   30, 'Monthly plan - LVH6-3LWV-PB5F-Y42U'),
  ('P8MT3SD4TXQJ5PAK',   30, 'Monthly plan - P8MT-3SD4-TXQJ-5PAK'),
  ('F9F8VSUFK6BZS9PH',   30, 'Monthly plan - F9F8-VSUF-K6BZ-S9PH'),
  ('DF74JR2GUU69VC54',   30, 'Monthly plan - DF74-JR2G-UU69-VC54'),
  ('56XWW5M52TR3XF2T',   30, 'Monthly plan - 56XW-W5M5-2TR3-XF2T'),
  ('YX27QWYXGLUCLVQB',   30, 'Monthly plan - YX27-QWYX-GLUC-LVQB'),
  ('7VZS4HSS64EYP7UN',   30, 'Monthly plan - 7VZS-4HSS-64EY-P7UN'),
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

-- Invoice packs (made 02/10/2026): ten of 20 invoices and ten of 50. A pack has no end date; every saved invoice,
-- credit note or debit note uses one of its invoices.
insert into public.activation_codes (code, days, invoices, note) values
  ('6Q28EPU7S2CU68MA', 0, 20, '20 invoices pack - 6Q28-EPU7-S2CU-68MA'),
  ('7CPH6XGZ2YHMYQZ5', 0, 20, '20 invoices pack - 7CPH-6XGZ-2YHM-YQZ5'),
  ('XEW8GXCRKZ9HMTWY', 0, 20, '20 invoices pack - XEW8-GXCR-KZ9H-MTWY'),
  ('Z3L2LARM7MYPPN2E', 0, 20, '20 invoices pack - Z3L2-LARM-7MYP-PN2E'),
  ('ZRWBG7DRHDJ27LJ9', 0, 20, '20 invoices pack - ZRWB-G7DR-HDJ2-7LJ9'),
  ('5WGRKY9S2J4P8PKR', 0, 20, '20 invoices pack - 5WGR-KY9S-2J4P-8PKR'),
  ('8R8MFVB2J38ANZSE', 0, 20, '20 invoices pack - 8R8M-FVB2-J38A-NZSE'),
  ('FRWENKCRW44QLU4F', 0, 20, '20 invoices pack - FRWE-NKCR-W44Q-LU4F'),
  ('P6CSJENTV56SPV86', 0, 20, '20 invoices pack - P6CS-JENT-V56S-PV86'),
  ('AUKLQ4LY5RAPKVW8', 0, 20, '20 invoices pack - AUKL-Q4LY-5RAP-KVW8'),
  ('S2LEAZ4ZE6VEAXAG', 0, 50, '50 invoices pack - S2LE-AZ4Z-E6VE-AXAG'),
  ('LKQ5KZRJDVKM2ME6', 0, 50, '50 invoices pack - LKQ5-KZRJ-DVKM-2ME6'),
  ('SWHPKWKHA5S6ZZ4W', 0, 50, '50 invoices pack - SWHP-KWKH-A5S6-ZZ4W'),
  ('QGAN7SDSA853PJC4', 0, 50, '50 invoices pack - QGAN-7SDS-A853-PJC4'),
  ('X4Q8TEFV4NUZBSR5', 0, 50, '50 invoices pack - X4Q8-TEFV-4NUZ-BSR5'),
  ('WTP3MUV3XGGEJPTJ', 0, 50, '50 invoices pack - WTP3-MUV3-XGGE-JPTJ'),
  ('QCJ7JWX5LJAF8HK3', 0, 50, '50 invoices pack - QCJ7-JWX5-LJAF-8HK3'),
  ('C6MYQWD2PQBTNA88', 0, 50, '50 invoices pack - C6MY-QWD2-PQBT-NA88'),
  ('TQR82Q6THAJM6DT3', 0, 50, '50 invoices pack - TQR8-2Q6T-HAJM-6DT3'),
  ('SNHDZ832QSU24LP9', 0, 50, '50 invoices pack - SNHD-Z832-QSU2-4LP9')
on conflict (code) do nothing;
