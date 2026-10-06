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
-- too) to be used within pack_days of entering the code. invoices and pack_days are 0 for a time plan, days is 0
-- for an invoice pack.
alter table public.activation_codes add column if not exists invoices int not null default 0;
alter table public.activation_codes add column if not exists pack_days int not null default 0;
-- A Full access code (accounts + HR & payroll): a time plan that also sets full_until on the clients
alter table public.activation_codes add column if not exists "full" boolean not null default false;

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

-- The same for clients that know both kinds: {"days": 30, "invoices": 0, "pack_days": 0, "full": false} or {"days": 0, "invoices": 15, "pack_days": 90, "full": false};
-- full is true for a Full access code (clients that do not know it treat the code as a plain time plan);
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
  return jsonb_build_object('days', c.days, 'invoices', c.invoices, 'pack_days', c.pack_days, 'full', c."full");
end $$;
grant execute on function public.redeem_code_v2(text) to authenticated;

-- The codes handed out (made 02/10/2026): ten of each plan. Hand one out per customer; it works once. Add more rows
-- the same way (16 letters / digits, no dashes in the table; the app and the portal ignore dashes when a code is typed).
-- Plans: monthly 30 days, yearly 365, 2 years 730, 5 years 1825; packs of 15 invoices (3 months) and 40 invoices
-- (6 months). Plan codes loaded by an earlier version of this file stay valid.
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

-- Invoice packs (made 03/10/2026): ten of 15 invoices, to be used within 3 months (90 days), and ten of 40 invoices,
-- within 6 months (180 days). Every saved invoice, credit note or debit note uses one of the pack's invoices; what is
-- not used by then lapses. The packs of 20 and 50 invoices are no longer sold: their unused codes are withdrawn
-- (a pack already entered on an account stays as it is).
delete from public.activation_codes where invoices in (20, 50) and used_by is null;
insert into public.activation_codes (code, days, invoices, pack_days, note) values
  ('Q2FT669XMBSSRA9W', 0, 15, 90, '15 invoices pack - Q2FT-669X-MBSS-RA9W'),
  ('8P4CFUGJD34PT4JV', 0, 15, 90, '15 invoices pack - 8P4C-FUGJ-D34P-T4JV'),
  ('KG9MGCEPNAN93UNJ', 0, 15, 90, '15 invoices pack - KG9M-GCEP-NAN9-3UNJ'),
  ('C9ZY6XLFYYEJGAD9', 0, 15, 90, '15 invoices pack - C9ZY-6XLF-YYEJ-GAD9'),
  ('CUPKQSHBQNEE6UH8', 0, 15, 90, '15 invoices pack - CUPK-QSHB-QNEE-6UH8'),
  ('RWGQPTXWLRMFGABS', 0, 15, 90, '15 invoices pack - RWGQ-PTXW-LRMF-GABS'),
  ('3T5TW54U9KPKQB4N', 0, 15, 90, '15 invoices pack - 3T5T-W54U-9KPK-QB4N'),
  ('39XK3BF2TPNRDR3G', 0, 15, 90, '15 invoices pack - 39XK-3BF2-TPNR-DR3G'),
  ('FYGBTCTE7N4TQBQL', 0, 15, 90, '15 invoices pack - FYGB-TCTE-7N4T-QBQL'),
  ('JXYLSL9E9GNG9ZPS', 0, 15, 90, '15 invoices pack - JXYL-SL9E-9GNG-9ZPS'),
  ('MES5QRZZ8J8DGNZS', 0, 40, 180, '40 invoices pack - MES5-QRZZ-8J8D-GNZS'),
  ('P33LFWX6YFBRJ65W', 0, 40, 180, '40 invoices pack - P33L-FWX6-YFBR-J65W'),
  ('94PT8YPKBPYQEHHE', 0, 40, 180, '40 invoices pack - 94PT-8YPK-BPYQ-EHHE'),
  ('CYTF3GZS6F7UP97L', 0, 40, 180, '40 invoices pack - CYTF-3GZS-6F7U-P97L'),
  ('57W4F8XSQGTT5K69', 0, 40, 180, '40 invoices pack - 57W4-F8XS-QGTT-5K69'),
  ('JFX6GP8CU9E3GFXJ', 0, 40, 180, '40 invoices pack - JFX6-GP8C-U9E3-GFXJ'),
  ('JUR6MSW2T3DVYQCV', 0, 40, 180, '40 invoices pack - JUR6-MSW2-T3DV-YQCV'),
  ('VTHQPF7A3UZU6LL4', 0, 40, 180, '40 invoices pack - VTHQ-PF7A-3UZU-6LL4'),
  ('U2CHC9BWKG3TXFJ7', 0, 40, 180, '40 invoices pack - U2CH-C9BW-KG3T-XFJ7'),
  ('YQSNFAZ5VFEJNAMM', 0, 40, 180, '40 invoices pack - YQSN-FAZ5-VFEJ-NAMM')
on conflict (code) do nothing;

-- Full access for 6 months (made 07/10/2026): five codes of 180 days that also open HR & payroll (full_until on the
-- clients) for those days. The days follow the current validity like any time plan.
insert into public.activation_codes (code, days, "full", note) values
  ('GV74SLW357DEYD9Q', 180, true, 'Full access 6 months - GV74-SLW3-57DE-YD9Q'),
  ('QXH3J5CBK47K9R5R', 180, true, 'Full access 6 months - QXH3-J5CB-K47K-9R5R'),
  ('Z8NXX9JCEQA6X7L3', 180, true, 'Full access 6 months - Z8NX-X9JC-EQA6-X7L3'),
  ('KWMJ89RYR52KKJ4K', 180, true, 'Full access 6 months - KWMJ-89RY-R52K-KJ4K'),
  ('8EY9GC54K4DBRM49', 180, true, 'Full access 6 months - 8EY9-GC54-K4DB-RM49')
on conflict (code) do nothing;
