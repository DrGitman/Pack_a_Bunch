import React from 'react';

export default function TermsOfUse() {
  return (
    <div className="legal-page-wrapper">
      <header className="container site-header">
        <a className="logo" href="/" aria-label="Pack-a-Bunch home">
          <img src="/assets/wordmark.png" alt="Pack-a-Bunch" width="1027" height="213"/>
        </a>
        <a className="button button-secondary" href="/">Back to site</a>
      </header>

      <main className="legal-container">
        <h1>Terms of use</h1>
        <p className="legal-meta">Last updated 30 September 2026</p>

        <h2>The short version</h2>
        <p>Use the app to plan your packing. Measure carefully, and check the plan against the real thing before you rely on it. Be fair to other people who use it.</p>

        <h2>Your account</h2>
        <p>You need an account so your packs can follow you to another phone. Keep your password to yourself, and tell us if somebody else gets into your account.</p>
        <p>You must be old enough to agree to these terms where you live. If you are under 18, ask a parent first.</p>

        <h2>What the app can and cannot do</h2>
        <p>The app works out an arrangement from the sizes you give it. Those sizes come from a tape measure or from the camera, and the camera is an estimate.</p>
        <p>A plan is a good starting point, not a promise. Boxes bend, corners get in the way and a fridge door is not where you think it is. Check before you commit, and never use a plan as proof that something will fit in a van, a boot or a flight.</p>
        <p>We are not responsible for damage caused by packing something the way a plan suggested. You are the one looking at the real objects.</p>

        <h2>Pack a Bunch Pro</h2>
        <p>The free plan keeps up to five saved packs, with up to 20 pieces each. Pack a Bunch Pro lifts those caps.</p>
        <p>If you subscribe, Google Play takes the payment and renews it each month until you cancel. You can cancel at any time in Google Play, and you keep Pro until the month you paid for runs out.</p>
        <p>Refunds are handled by Google Play under their rules, not by us.</p>
        <p>If the price changes, Google Play will tell you before you are charged the new one.</p>

        <h2>Fair use</h2>
        <p>Do not try to break the app, get into other people's accounts, or use it to do anything against the law. We can close an account that does.</p>

        <h2>If something goes wrong</h2>
        <p>We do our best to keep the app working and your packs safe, but we cannot promise it will never be unavailable or never lose data. Keep your own note of anything you cannot afford to lose.</p>
        <p>Where the law allows us to limit what we owe you, our responsibility is limited to what you have paid us in the last twelve months.</p>

        <h2>Ending it</h2>
        <p>You can stop using the app at any time and delete your account from your profile.</p>
        <p>We can close an account that breaks these terms. If we do, we will tell you why where we are allowed to.</p>

        <h2>Changes and law</h2>
        <p>If we change these terms, the date at the top changes, and the app tells you when the change matters.</p>
        <p>These terms follow the law of Namibia.</p>
        <p>Questions go to support@packabunch.site.</p>
      </main>
    </div>
  );
}
