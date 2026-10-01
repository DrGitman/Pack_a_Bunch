import LegalPage, { Section } from './LegalPage';

export default function TermsOfUse({ onNavigate }) {
  return (
    <LegalPage title="Terms of use" updated="30 September 2026" onNavigate={onNavigate}>
      <Section heading="The short version">
        <p>Use the app to plan your packing. Measure carefully, and check the plan against the real thing before you rely on it. Be fair to other people who use it.</p>
      </Section>

      <Section heading="Your account">
        <p>You need an account so your packs can follow you to another phone. Keep your password to yourself, and tell us if somebody else gets into your account.</p>
        <p>You must be old enough to agree to these terms where you live. If you are under 18, ask a parent first.</p>
      </Section>

      <Section heading="What the app can and cannot do">
        <p>The app works out an arrangement from the sizes you give it. Those sizes come from a tape measure or from the camera, and the camera is an estimate.</p>
        <p>A plan is a good starting point, not a promise. Boxes bend, corners get in the way and a fridge door is not where you think it is. Check before you commit, and never use a plan as proof that something will fit in a van, a boot or a flight.</p>
        <p>We are not responsible for damage caused by packing something the way a plan suggested. You are the one looking at the real objects.</p>
      </Section>

      <Section heading="Pack Plus">
        <p>The free plan keeps up to five saved packs, with up to 20 pieces each. Pack Plus lifts those caps.</p>
        <p>If you subscribe, Google Play takes the payment and renews it each month until you cancel. You can cancel at any time in Google Play, and you keep Pack Plus until the month you paid for runs out.</p>
        <p>Refunds are handled by Google Play under their rules, not by us.</p>
        <p>If the price changes, Google Play will tell you before you are charged the new one.</p>
      </Section>

      <Section heading="Fair use">
        <p>Do not try to break the app, get into other people’s accounts, or use it to do anything against the law. We can close an account that does.</p>
      </Section>

      <Section heading="If something goes wrong">
        <p>We do our best to keep the app working and your packs safe, but we cannot promise it will never be unavailable or never lose data. Keep your own note of anything you cannot afford to lose.</p>
        <p>Where the law allows us to limit what we owe you, our responsibility is limited to what you have paid us in the last twelve months.</p>
      </Section>

      <Section heading="Ending it">
        <p>You can stop using the app at any time and delete your account from your profile.</p>
        <p>We can close an account that breaks these terms. If we do, we will tell you why where we are allowed to.</p>
      </Section>

      <Section heading="Changes and law">
        <p>If we change these terms, the date at the top changes, and the app tells you when the change matters.</p>
        <p>These terms follow the law of Namibia.</p>
        <p>Questions go to <a href="mailto:support@packabunch.site">support@packabunch.site</a>.</p>
      </Section>
    </LegalPage>
  );
}
