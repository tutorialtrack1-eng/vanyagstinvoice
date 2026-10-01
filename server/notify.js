/* BlitzBook sync server - sends the one-time passwords (OTP) for registration and password reset by SMS and
   by email. No dependencies: SMTP is spoken directly (TLS on port 465, or STARTTLS on 587 / 25), SMS goes
   through the HTTP API of the provider named in the settings.

   Settings come from environment variables, or from <data folder>/config.json with the same names:
     SMTP_HOST, SMTP_PORT (465 or 587), SMTP_USER, SMTP_PASS, SMTP_FROM        email (Gmail: smtp.gmail.com, 465,
                                                                              the Gmail address, an App Password)
     SMS_PROVIDER = fast2sms | msg91 | twilio | textlocal | http               SMS, with
       fast2sms:  SMS_API_KEY
       msg91:     SMS_API_KEY (authkey), SMS_TEMPLATE_ID (a DLT-approved OTP template with the ##OTP## variable)
       twilio:    SMS_ACCOUNT_SID, SMS_AUTH_TOKEN, SMS_FROM (the Twilio number)
       textlocal: SMS_API_KEY, SMS_SENDER (6-letter sender id)
       http:      SMS_URL with {phone}, {otp} and {message} placeholders (GET), SMS_HEADERS as JSON (optional)
   With neither configured the server runs in test mode: the OTP is returned to the app / portal, which show it
   on screen, as before. */
'use strict';
const tls = require('tls');
const net = require('net');
const fs = require('fs');
const path = require('path');

let settings = {};
function load(dataDir) {
  let file = {};
  try { file = JSON.parse(fs.readFileSync(path.join(dataDir, 'config.json'), 'utf8')); } catch (e) { /* no file: environment only */ }
  settings = Object.assign({}, file, Object.fromEntries(Object.entries(process.env).filter(([k]) => /^(SMTP|SMS)_/.test(k))));
  return status();
}
const get = (k) => String(settings[k] == null ? '' : settings[k]).trim();
function emailReady() { return !!(get('SMTP_HOST') && get('SMTP_USER') && get('SMTP_PASS')); }
function smsReady() {
  const p = get('SMS_PROVIDER').toLowerCase();
  if (p === 'fast2sms' || p === 'textlocal') return !!get('SMS_API_KEY');
  if (p === 'msg91') return !!(get('SMS_API_KEY') && get('SMS_TEMPLATE_ID'));
  if (p === 'twilio') return !!(get('SMS_ACCOUNT_SID') && get('SMS_AUTH_TOKEN') && get('SMS_FROM'));
  if (p === 'http') return !!get('SMS_URL');
  return false;
}
function status() { return { email: emailReady(), sms: smsReady(), provider: get('SMS_PROVIDER').toLowerCase() }; }

// ------------------------------------------------------------ SMTP
// One message to one address. Resolves when the server has accepted it, rejects with the server's reply otherwise.
function sendMail(to, subject, text) {
  const host = get('SMTP_HOST'), port = parseInt(get('SMTP_PORT'), 10) || 465, user = get('SMTP_USER'), pass = get('SMTP_PASS'), from = get('SMTP_FROM') || user;
  return new Promise((resolve, reject) => {
    let sock, buf = '', done = false, secure = port === 465;
    const fail = (e) => { if (done) return; done = true; try { sock.destroy(); } catch (x) { /* closed */ } reject(e instanceof Error ? e : new Error(String(e))); };
    const timer = setTimeout(() => fail(new Error('SMTP timeout')), 30000);
    const waiters = [];
    // A reply ends at a line "NNN text"; "NNN-text" lines before it belong to the same reply
    const onData = (d) => {
      buf += d.toString('utf8');
      for (;;) {
        const m = /^\d{3} [^\n]*\n/m.exec(buf);
        if (!m) return;
        const end = m.index + m[0].length, lines = buf.slice(0, end).split(/\r?\n/).filter(Boolean);
        buf = buf.slice(end);
        const w = waiters.shift(); if (w) w({ code: +m[0].slice(0, 3), text: lines.join('\n') });
      }
    };
    const expect = (okCodes) => new Promise((res, rej) => waiters.push((r) => okCodes.includes(r.code) ? res(r) : rej(new Error('SMTP: ' + r.text))));
    const cmd = (line, okCodes) => { sock.write(line + '\r\n'); return expect(okCodes); };
    const attach = (s) => { sock = s; s.on('data', onData); s.on('error', fail); s.on('close', () => { if (!done) fail(new Error('SMTP connection closed')); }); };
    const talk = async () => {
      await expect([220]);
      let ehlo = await cmd('EHLO blitzbook', [250]);
      if (!secure && /STARTTLS/i.test(ehlo.text)) {
        await cmd('STARTTLS', [220]);
        const plain = sock; plain.removeAllListeners('data'); plain.removeAllListeners('close');
        await new Promise((res, rej) => { const t = tls.connect({ socket: plain, servername: host }, () => res()); t.on('error', rej); attach(t); secure = true; });
        ehlo = await cmd('EHLO blitzbook', [250]);
      } else if (!secure) throw new Error('SMTP server offers no STARTTLS; use port 465');
      await cmd('AUTH LOGIN', [334]);
      await cmd(Buffer.from(user).toString('base64'), [334]);
      await cmd(Buffer.from(pass).toString('base64'), [235]);
      await cmd('MAIL FROM:<' + addr(from) + '>', [250]);
      await cmd('RCPT TO:<' + addr(to) + '>', [250, 251]);
      await cmd('DATA', [354]);
      const headers = ['From: ' + from, 'To: ' + to, 'Subject: ' + subject, 'Date: ' + new Date().toUTCString(), 'MIME-Version: 1.0', 'Content-Type: text/plain; charset=utf-8', 'Message-ID: <' + Date.now() + '.' + Math.random().toString(36).slice(2) + '@blitzbook>'];
      const body = text.replace(/\r?\n/g, '\r\n').replace(/^\./gm, '..');
      await cmd(headers.join('\r\n') + '\r\n\r\n' + body + '\r\n.', [250]);
      try { await cmd('QUIT', [221]); } catch (e) { /* some servers close first */ }
      done = true; clearTimeout(timer); sock.end(); resolve();
    };
    const start = () => talk().catch(fail);
    if (secure) { const s = tls.connect({ host, port, servername: host }, start); attach(s); }
    else { const s = net.connect({ host, port }, start); attach(s); }
  });
}
function addr(s) { const m = /<([^>]+)>/.exec(s); return (m ? m[1] : s).trim(); }

// ------------------------------------------------------------ SMS
async function sendSms(phone, otp, message) {
  const p = get('SMS_PROVIDER').toLowerCase();
  const ok = async (r, test) => { let body = ''; try { body = await r.text(); } catch (e) { /* empty */ } let j = null; try { j = JSON.parse(body); } catch (e) { /* not json */ } if (!r.ok || (test && !test(j))) throw new Error('SMS provider ' + p + ' answered ' + r.status + ': ' + body.slice(0, 200)); };
  if (p === 'fast2sms') {
    const r = await fetch('https://www.fast2sms.com/dev/bulkV2', { method: 'POST', headers: { authorization: get('SMS_API_KEY'), 'Content-Type': 'application/json' }, body: JSON.stringify({ route: 'otp', variables_values: otp, numbers: phone }) });
    return ok(r, j => j && j.return === true);
  }
  if (p === 'msg91') {
    const r = await fetch('https://control.msg91.com/api/v5/otp?template_id=' + encodeURIComponent(get('SMS_TEMPLATE_ID')) + '&mobile=91' + phone + '&otp=' + otp, { method: 'POST', headers: { authkey: get('SMS_API_KEY'), 'Content-Type': 'application/json' }, body: JSON.stringify({ OTP: otp }) });
    return ok(r, j => j && String(j.type).toLowerCase() === 'success');
  }
  if (p === 'twilio') {
    const sid = get('SMS_ACCOUNT_SID');
    const r = await fetch('https://api.twilio.com/2010-04-01/Accounts/' + sid + '/Messages.json', { method: 'POST', headers: { Authorization: 'Basic ' + Buffer.from(sid + ':' + get('SMS_AUTH_TOKEN')).toString('base64'), 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ To: '+91' + phone, From: get('SMS_FROM'), Body: message }).toString() });
    return ok(r);
  }
  if (p === 'textlocal') {
    const r = await fetch('https://api.textlocal.in/send/', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ apikey: get('SMS_API_KEY'), numbers: '91' + phone, sender: get('SMS_SENDER') || 'TXTLCL', message }).toString() });
    return ok(r, j => j && j.status === 'success');
  }
  if (p === 'http') {
    const url = get('SMS_URL').replace('{phone}', encodeURIComponent(phone)).replace('{otp}', encodeURIComponent(otp)).replace('{message}', encodeURIComponent(message));
    let headers = {}; try { headers = JSON.parse(get('SMS_HEADERS') || '{}'); } catch (e) { /* no headers */ }
    return ok(await fetch(url, { headers }));
  }
  throw new Error('No SMS provider configured');
}

// ------------------------------------------------------------ one OTP to its destinations
// Returns which channels carried it: {sms: true/false, email: true/false}. Throws only when every configured
// channel failed, so a bad email address does not stop the SMS and vice versa.
async function sendOtp(phone, email, otp, purpose) {
  const message = 'Your BlitzBook OTP is ' + otp + '. It is valid for 10 minutes. Do not share it with anyone.';
  const subject = 'BlitzBook OTP: ' + otp + (purpose === 'reset' ? ' (password reset)' : ' (registration)');
  const text = 'Hello,\n\n' + message + '\n\n' + (purpose === 'reset' ? 'Use it to reset your BlitzBook password.' : 'Use it to finish registering your BlitzBook account.') + ' If you did not ask for this, ignore this email.\n\nBlitzBook';
  const sent = { sms: false, email: false }, errors = [];
  if (phone && smsReady()) { try { await sendSms(phone, otp, message); sent.sms = true; } catch (e) { errors.push(e.message); } }
  if (email && emailReady()) { try { await sendMail(email, subject, text); sent.email = true; } catch (e) { errors.push(e.message); } }
  if (!sent.sms && !sent.email && errors.length) throw new Error(errors.join('; '));
  return sent;
}

module.exports = { load, status, sendOtp, sendMail, sendSms, emailReady, smsReady };
