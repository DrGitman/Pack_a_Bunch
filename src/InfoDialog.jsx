import { useEffect, useRef } from 'react';
import { siteConfig } from './config';
const content = {
  support: ['Support', 'For help testing the preview, check the installation steps in the download section. Camera measurement depends on your phone; you can always enter sizes by hand.'],
  privacy: ['Website privacy', 'This website has no advertising, analytics scripts or tracking cookies. The download is served by the website host, which may process standard request information such as your IP address. The Android app has its own privacy policy: read it in the app before creating an account.'],
  terms: ['Preview terms', 'This Android download is a testing preview, not a Google Play release. Features may be incomplete. Packing plans depend on the dimensions you provide: check the real fit, stability and weight before packing. The app’s full terms are available inside the app.'],
  deletion: ['Delete your account', 'In Pack-a-Bunch, open your profile and choose Delete account. The app schedules deletion after 30 days; signing in during that period cancels the request.']
};
export default function InfoDialog({ topic, onClose }) {
  const ref = useRef(null);
  useEffect(() => { if (topic) ref.current.showModal(); }, [topic]);
  const [title, body] = content[topic] || ['', ''];
  return <dialog ref={ref} onClose={onClose} onClick={event => { if (event.target === ref.current) ref.current.close(); }} aria-labelledby="dialog-title">
    <div className="dialog-content"><button className="dialog-close" onClick={() => ref.current.close()} aria-label="Close information">×</button><h2 id="dialog-title">{title}</h2><p>{body}</p>
      {(topic === 'support' || topic === 'deletion') && (siteConfig.supportEmail ? <a className="button" href={`mailto:${siteConfig.supportEmail}`}>Email support</a> : <p className="small">The support email is being set up. {topic === 'deletion' ? 'Web deletion requests will be available once the contact address is confirmed.' : 'Please use your existing contact with the developer for preview feedback.'}</p>)}
    </div>
  </dialog>;
}
