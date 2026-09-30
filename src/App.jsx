import { useEffect, useRef, useState } from 'react';
import Phone from './Phone';
import Download from './Download';
import InfoDialog from './InfoDialog';
import useReveal from './useReveal';

import PrivacyPolicy from './PrivacyPolicy';
import TermsOfUse from './TermsOfUse';

const steps = [
  ['Define your space', 'Add the inside dimensions of your boot, box or shelf.'],
  ['Add your things', 'Set sizes, quantities and which items must stay upright.'],
  ['Pack with a plan', 'Inspect the 3D layout, then follow the guide item by item.']
];

const questions = [
  ['Do I need a depth-scanning phone?', 'No. You can enter dimensions by hand. Camera support varies by device.'],
  ['Can I leave a pack and come back?', 'Yes. Return to your saved pack and continue from your progress.'],
  ['Does a plan guarantee everything will fit?', 'Plans use your dimensions and settings. Check the real fit as you pack.']
];

function Logo() { 
  return <a className="logo" href="#top" aria-label="Pack-a-Bunch home"><img src="/assets/wordmark.png" alt="Pack-a-Bunch" width="1027" height="213"/></a>; 
}

function Header() {
  const [open, setOpen] = useState(false); 
  const toggle = useRef(null);

  useEffect(() => { 
    const close = event => { if(event.key === 'Escape' && open) { setOpen(false); toggle.current?.focus(); } }; 
    document.addEventListener('keydown', close); 
    return () => document.removeEventListener('keydown', close); 
  }, [open]);

  return (
    <header className="site-header container">
      <Logo/>
      <button ref={toggle} className="menu-toggle" aria-expanded={open} aria-controls="navigation" aria-label={open ? 'Close navigation' : 'Open navigation'} onClick={() => setOpen(!open)}>
        <span/><span/>
      </button>
      <nav id="navigation" aria-label="Main navigation" className={open ? 'is-open' : ''} onClick={e => { if (e.target.closest('a')) setOpen(false); }}>
        <a href="#product">Product</a>
        <a href="#how-it-works">How it works</a>
        <a href="#plans">Plans</a>
        {/* Keeping the node inside the nav container so your mobile sidebar styles remain perfectly unbroken */}
        <a className="button nav-download" href="#download">Get the app</a>
      </nav>
    </header>
  );
}

export default function App() {
  useReveal(); 
  const [topic, setTopic] = useState(null);
  const [currentPath, setCurrentPath] = useState(window.location.pathname);

  // 🌟 Sync state dynamically when users press Back/Forward in browsers
  useEffect(() => {
    const handleLocationChange = () => setCurrentPath(window.location.pathname);
    window.addEventListener('popstate', handleLocationChange);
    return () => window.removeEventListener('popstate', handleLocationChange);
  }, []);

  const handleNavigation = (path) => {
    window.history.pushState({}, '', path);
    window.dispatchEvent(new Event('popstate'));
    window.scrollTo(0, 0); // Scroll cleanly back to the top of the viewport
  };

  if (currentPath === '/privacy') {
    return <PrivacyPolicy onNavigate={handleNavigation} />;
  }
  if (currentPath === '/terms') {
    return <TermsOfUse onNavigate={handleNavigation} />;
  }

  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <div id="top"/>
      <Header/>
      <main id="main">
        <section className="hero container" aria-labelledby="hero-title">
          <p className="badge">LESS GUESSWORK. MORE ROOM.</p>
          <h1 id="hero-title">A place for everything.<br/>A plan to get it there.</h1>
          <p className="hero-description">Turn your space and your things into a clear 3D packing plan.<br className="desktop-break"/> Then follow it, one item at a time.</p>
          <div className="hero-actions">
            <a className="button" href="#how-it-works">Explore how it works</a>
            <a className="button button-secondary" href="#plans">Compare plans</a>
          </div>
          <div id="product" className="product-showcase">
            <div className="product-copy">
              <h2>See the plan.<br/>Pack with confidence.</h2>
              <p>A clear view of what goes where.<br/>A simple guide for what comes next.</p>
            </div>
            <Phone screen="plan" eager/>
          </div>
          <p className="hero-caption">Made for moving days, weekends away and everyday storage.</p>
        </section>

        <section className="steps-section" id="how-it-works">
          <div className="container">
            <div className="section-heading" data-reveal>
              <p className="eyebrow">A CLEARER WAY TO PACK</p>
              <h2>From a pile of things<br/>to a plan that makes sense.</h2>
            </div>
            <div className="steps-grid">
              {steps.map(([title,description],i) => (
                <article className={`step-card step-${i}`} key={title} data-reveal>
                  <span className="step-number">0{i+1}</span>
                  <h3>{title}</h3>
                  <p>{description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="saved-section container" aria-labelledby="saved-title">
          <div className="saved-showcase" data-reveal><Phone screen="projects"/></div>
          <div className="saved-copy" data-reveal>
            <p className="eyebrow">KEEP YOUR NEXT PACK SIMPLE</p>
            <h2 id="saved-title">Your things.<br/>Ready for next time.</h2>
            <p>Keep your packs organised and return to an unfinished plan right where you left off.</p>
            <ul className="benefits">
              <li>Reuse saved items</li>
              <li>Find your previous packs</li>
              <li>Continue packing where you stopped</li>
            </ul>
          </div>
        </section>

        <section className="measurement-strip">
          <div className="container">
            <h2>Camera when supported.<br/>Manual measurements anytime.</h2>
            <p>Review dimensions before you pack. Camera measurement depends on your phone and conditions.</p>
          </div>
        </section>

        <section className="plans-section container" id="plans">
          <div className="section-heading" data-reveal>
            <p className="eyebrow">ROOM TO GROW</p>
            <h2>Start simple. Pack more.</h2>
            <p>Choose the plan that fits the way you pack.</p>
          </div>
          <div className="plans-grid">
            <article className="plan-card" data-reveal>
              <h3>Free</h3>
              <p>For your next pack.</p>
              <ul>
                <li>A basic packing plan</li>
                <li>Step-by-step packing guide</li>
                <li>Saved progress</li>
              </ul>
              <a href="#download">Free to get started</a>
            </article>
            <article className="plan-card plan-plus" data-reveal>
              <h3>Pack Plus</h3>
              <p>For the regular packer.</p>
              <ul>
                <li>More room for your projects</li>
                <li>A reusable item library</li>
                <li>Extra capacity as you grow</li>
              </ul>
              <span className="plan-note">Launch pricing coming soon</span>
            </article>
          </div>
        </section>

        <Download/>

        <section className="faq-section">
          <div className="container" data-reveal>
            <h2>A few things to know.</h2>
            {questions.map(([q,a]) => (
              <article className="faq-row" key={q}>
                <h3>{q}</h3>
                <p>{a}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="closing">
          <div className="container" data-reveal>
            <h2>Make room for what matters.</h2>
            <p>Pack-a-Bunch · Coming soon on Android</p>
            <a className="button" href="#product">Back to the product</a>
          </div>
        </section>
      </main>

      <footer className="container site-footer">
        <div>
          <Logo/>
          <p>Plan it. Pack it. Keep going.</p>
        </div>
        <nav aria-label="Information">
          <button onClick={() => setTopic('support')}>Support</button>
          <button onClick={() => handleNavigation('/privacy')}>Privacy</button>
          <button onClick={() => handleNavigation('/terms')}>Terms</button>
          <button onClick={() => setTopic('deletion')}>Delete account</button>
        </nav>
      </footer>

      <InfoDialog topic={topic} onClose={() => setTopic(null)}/>
    </>
  );
}
