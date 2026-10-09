// Checks the deployed invite edge function on the real project: a throwaway owner (made with the service key, deleted
// afterwards) invites an address no account has; the function must answer invited + mailed, and refuse a call without
// a token.  SUPABASE_TOKEN=sbp_... node server/supabase/invite-live-test.js [address]   (default padduwithyou+invite@gmail.com)
const crypto = require('crypto');
const token = process.env.SUPABASE_TOKEN, ref = 'cufdskrmhdenppoxfhnk', URL_ = 'https://' + ref + '.supabase.co';
const TO = process.argv[2] || 'padduwithyou+invite@gmail.com';
const pwHash = (p) => crypto.createHash('sha256').update('bb|' + p).digest('hex');
let failures = 0;
const check = (n, c, d) => { if (c) console.log('  ok   ' + n); else { failures++; console.log('  FAIL ' + n + (d === undefined ? '' : '  -> ' + JSON.stringify(d))); } };
const call = async (p, method, headers, body) => { const r = await fetch(URL_ + p, { method, headers: Object.assign({ 'Content-Type': 'application/json' }, headers), body: body === undefined ? undefined : JSON.stringify(body) }); const t = await r.text(); let j = null; try { j = t ? JSON.parse(t) : null; } catch (e) { j = t; } return { status: r.status, json: j }; };
(async () => {
  if (!token) throw new Error('SUPABASE_TOKEN missing');
  const keys = await (await fetch('https://api.supabase.com/v1/projects/' + ref + '/api-keys?reveal=true', { headers: { Authorization: 'Bearer ' + token } })).json();
  const service = keys.find(k => k.name === 'service_role').api_key, anon = (keys.find(k => k.name === 'anon') || {}).api_key || keys.find(k => /publishable/.test(k.name)).api_key;
  const adminH = { apikey: service, Authorization: 'Bearer ' + service };
  const email = 'blitzbook-invite-test-' + Date.now().toString(36) + '@example.com', pw = pwHash('Test@123');
  const u = (await call('/auth/v1/admin/users', 'POST', adminH, { email, password: pw, email_confirm: true, user_metadata: { name: 'Invite Tester' } })).json;
  check('throwaway owner made', !!u.id, u);
  try {
    const s = (await call('/auth/v1/token?grant_type=password', 'POST', { apikey: anon }, { email, password: pw })).json;
    check('signed in', !!s.access_token, s);
    const userH = { apikey: anon, Authorization: 'Bearer ' + s.access_token };
    await call('/rest/v1/profiles', 'POST', Object.assign({ Prefer: 'resolution=merge-duplicates,return=minimal' }, userH), { id: u.id, name: 'Invite Tester', email });
    await call('/rest/v1/books?on_conflict=user_id,k', 'POST', Object.assign({ Prefer: 'resolution=merge-duplicates,return=minimal' }, userH), [{ user_id: u.id, k: 'company', d: { company_name: 'Invite Test Co' } }]);
    const mine = (await call('/rest/v1/rpc/my_companies', 'POST', userH, {})).json;
    check('own company listed', Array.isArray(mine) && mine.length === 1 && mine[0].name === 'Invite Test Co', mine);
    const r = await call('/functions/v1/invite', 'POST', userH, { cid: u.id, identity: TO, role: 'accountant' });
    check('the function invites and mails ' + TO, r.status === 200 && r.json && r.json.ok && r.json.invited && r.json.mailed === true && r.json.company === 'Invite Test Co' && r.json.by === 'Invite Tester', r);
    const lm = (await call('/rest/v1/rpc/list_members', 'POST', userH, { cid: u.id })).json;
    check('the invitation is listed', Array.isArray(lm) && lm.some(m => m.invited && m.email === TO && m.role === 'accountant'), lm);
    const r2 = await call('/functions/v1/invite', 'POST', { apikey: anon }, { cid: u.id, identity: TO, role: 'viewer' });
    check('without a token the function refuses', r2.status === 401, r2);
  } finally {
    const d = await call('/auth/v1/admin/users/' + u.id, 'DELETE', adminH);
    console.log('deleted ' + email + ' ' + d.status);
    const left = (await call('/rest/v1/company_invites?select=identity&identity=eq.' + encodeURIComponent(TO), 'GET', adminH)).json;
    check('no invitation left behind', Array.isArray(left) && left.length === 0, left);
  }
  console.log(failures ? failures + ' FAILED' : 'all passed');
  process.exitCode = failures ? 1 : 0;
})().catch(e => { console.error(e); process.exitCode = 1; });
