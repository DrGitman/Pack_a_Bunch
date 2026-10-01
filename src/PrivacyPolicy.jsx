import LegalPage, { Section } from './LegalPage';

export default function PrivacyPolicy({ onNavigate }) {
  return (
    <LegalPage title="Privacy policy" updated="30 September 2026" onNavigate={onNavigate}>
      <Section heading="The short version">
        <p>We ask for your email address so you can sign in. We store the packs you make so you can open them on another phone. That is all.</p>
        <p>We do not show adverts. We do not track you. We do not sell anything about you to anyone.</p>
      </Section>

      <Section heading="What we keep">
        <p>Your email address, so you can sign in and reset your password.</p>
        <p>Your packs: the spaces you measure, the items you add, their sizes, and the plans the app works out for you.</p>
        <p>Your settings, such as whether you measure in centimetres or inches.</p>
      </Section>

      <Section heading="What never leaves your phone">
        <p>Photos you take of your items. They are saved on your phone only, and are never uploaded to us or to anyone else.</p>
        <p>Camera scans. When you measure with the camera, the app reads the shape of what is in front of you and keeps only the measurements. No video or picture of the room is recorded or sent.</p>
        <p>Item recognition. When the app suggests a name for something you photograph, that happens on your phone.</p>
        <p>“It doesn’t fit” reports. When you tell us why a planned item didn’t fit, we keep the reason you picked and the sizes of the item and the space, so we can make plans better. Never the item’s name or anything you typed.</p>
      </Section>

      <Section heading="Who else is involved">
        <p>Supabase stores your account and your packs for us. They hold the data on their servers so that your packs are still there if you lose your phone.</p>
        <p>Google Play and RevenueCat handle payment if you buy Pack Plus. They tell us only whether your subscription is active. We never see your card details.</p>
        <p>If you choose to sign in with Google, Google confirms who you are. We receive your email address and nothing more.</p>
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
