import LegalPage, { Section } from './LegalPage';

export default function PrivacyPolicy({ onNavigate }) {
  return (
    <LegalPage title="Privacy policy" updated="1 October 2026" onNavigate={onNavigate}>
      <Section heading="The short version">
        <p>We ask for your email address so you can sign in. We store the packs you make so you can open them on another phone. That is all.</p>
        <p>We do not show adverts. We do not track you. We do not sell anything about you to anyone.</p>
      </Section>

      <Section heading="What we keep">
        <p>Your email address, so you can sign in and reset your password.</p>
        <p>Your packs, so they are on every phone you sign in on: the spaces you measure, the items you add with their names, sizes and the shapes the scan measured, and the plans the app works out for you. Item photos are not part of this; they stay on the phone they were taken on.</p>
        <p>Your profile picture, if you add one.</p>
        <p>Your settings, such as whether you measure in centimetres or inches.</p>
      </Section>

      <Section heading="What never leaves your phone">
        <p>Your item and scan photos. A photo you take or pick to measure is measured on your phone, and the item photos are saved there only. They are not backed up with your packs, and the whole photo is never uploaded to us or to anyone else.</p>
        <p>Photos of spaces. When you measure a space, the app works out its size on your phone and keeps only the measurements. No picture of the space is sent.</p>
        <p>Item recognition on the phone. The app's own suggestions for what something is are worked out on your phone. The one exception is the online lookup below.</p>
        <p>“It doesn’t fit” reports. When you tell us why a planned item didn’t fit, we keep the reason you picked and the sizes of the item and the space, so we can make plans better. Never the item’s name or anything you typed.</p>
      </Section>

      <Section heading="Looking items up online">
        <p>When you scan items, the app can look each one up to find its real size. It is on unless you switch off “Look up items online” in Settings, and it only works when you are signed in.</p>
        <p>For each item it found, the app sends a small cut-out of that item, never the whole photo, with the name it guessed and the size it measured. Our server passes them to Google’s Gemini service, which says what the item is and how big it usually is. We do not keep the cut-outs.</p>
        <p>Google handles them under the Gemini API terms, which may allow Google to use them to improve its services.</p>
      </Section>

      <Section heading="Who else is involved">
        <p>Supabase stores your account and your packs for us. They hold the data on their servers so that your packs are still there if you lose your phone.</p>
        <p>Google Play and RevenueCat handle payment if you buy Pack Plus. They tell us only whether your subscription is active. We never see your card details.</p>
        <p>If you choose to sign in with Google, Google confirms who you are. We receive your email address and nothing more.</p>
        <p>Google’s Gemini service identifies items for the online lookup, as described above.</p>
      </Section>

      <Section heading="How long we keep it">
        <p>Your packs stay until you delete them or delete your account.</p>
        <p>You can delete every saved pack from Settings. You can delete your whole account and everything in it from your profile. It is deleted 30 days after you ask, with your packs on our side; signing in again before then stops it. After that it cannot be undone.</p>
      </Section>

      <Section heading="Children">
        <p>This app is not aimed at children under 13, and we do not knowingly keep any information about them. If you believe a child has made an account, write to us and we will remove it.</p>
      </Section>

      <Section heading="Changes">
        <p>If we change what we collect, we will update this page and change the date at the top. If the change is a big one, the app will tell you before it takes effect.</p>
      </Section>

      <Section heading="Contact">
        <p>Write to <a href="mailto:support@packabunch.site">support@packabunch.site</a> with any question about your information, or to ask for a copy of it. Pack a Bunch publishes this app.</p>
      </Section>
    </LegalPage>
  );
}
