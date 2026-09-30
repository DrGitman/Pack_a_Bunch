import React from 'react';
import Logo from './App'; // Adjust based on your logo export path

export default function PrivacyPolicy() {
  return (
    <div className="legal-page-wrapper">
      <header className="container site-header">
        <a className="logo" href="/" aria-label="Pack-a-Bunch home">
          <img src="/assets/wordmark.png" alt="Pack-a-Bunch" width="1027" height="213"/>
        </a>
        <a className="button button-secondary" href="/">Back to site</a>
      </header>

      <main className="legal-container">
        <h1>Privacy policy</h1>
        <p className="legal-meta">Last updated 30 September 2026</p>

        <h2>The short version</h2>
        <p>We ask for your email address so you can sign in. We store the packs you make so you can open them on another phone. That is all.</p>
        <p>We do not show adverts. We do not track you. We do not sell anything about you to anyone.</p>

        <h2>What we keep</h2>
        <p>Your email address, so you can sign in and reset your password.</p>
        <p>Your packs: the spaces you measure, the items you add, their sizes, and the plans the app works out for you.</p>
        <p>Your settings, such as whether you measure in centimetres or inches.</p>

        <h2>What never leaves your phone</h2>
        <p>Photos you take of your items. They are saved on your phone only, and are never uploaded to us or to anyone else.</p>
        <p>Camera scans. When you measure with the camera, the app reads the shape of what is in front of you and keeps only the measurements. No video or picture of the room is recorded or sent.</p>
        <p>Item recognition. When the app suggests a name for something you photograph, that happens on your phone.</p>
        <p>"It doesn't fit" reports. When you tell us why a planned item didn't fit, we keep the reason you picked and the sizes of the item and the space, so we can make plans better. Never the item's name or anything you typed.</p>

        <h2>Who else is involved</h2>
        <p>Supabase stores your account and your packs for us. They hold the data on their servers so that your packs are still there if you lose your phone.</p>
        <p>Google Play and RevenueCat handle payment if you buy Pack a Bunch Pro. They tell us only whether your subscription is active. We never see your card details.</p>
        <p>If you choose to sign in with Google, Google confirms who you are. We receive your email address and nothing more.</p>

        <h2>How long we keep it</h2>
        <p>Your packs stay until you delete them or delete your account.</p>
        <p>You can delete every saved pack from Settings. You can delete your whole account and everything in it from your profile. It is deleted 30 days after you ask, with your packs on our side; signing in again before then stops it. After that it cannot be undone.</p>

        <h2>Children</h2>
        <p>This app is not aimed at children under 13, and we do not knowingly keep any information about them. If you believe a child has made an account, write to us and we will remove it.</p>

        <h2>Changes</h2>
        <p>If we change what we collect, we will update this page and change the date at the top. If the change is a big one, the app will tell you before it takes effect.</p>

        <h2>Contact</h2>
        <p>Write to support@packabunch.site with any question about your information, or to ask for a copy of it. Pack a Bunch publishes this app.</p>
      </main>
    </div>
  );
}
