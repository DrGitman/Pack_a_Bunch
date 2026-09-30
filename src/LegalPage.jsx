/**
 * The frame the privacy and terms pages share: site header, a warm title band,
 * the prose column, and the same footer as the home page.
 */
export default function LegalPage({ title, updated, onNavigate, children }) {
  const goHome = event => {
    if (!onNavigate) return;
    event.preventDefault();
    onNavigate('/');
  };

  return (
    <div className="legal-page-wrapper">
      <header className="container site-header">
        <a className="logo" href="/" aria-label="Pack-a-Bunch home" onClick={goHome}>
          <img src="/assets/wordmark.png" alt="Pack-a-Bunch" width="1027" height="213"/>
        </a>
        <a className="button button-secondary" href="/" onClick={goHome}>Back to site</a>
      </header>

      <main>
        <div className="legal-hero">
          <div className="container">
            <p className="eyebrow">PACK-A-BUNCH</p>
            <h1>{title}</h1>
            <p className="legal-meta">Last updated {updated}</p>
          </div>
        </div>

        <div className="legal-container">{children}</div>
      </main>

      <footer className="container site-footer">
        <div>
          <a className="logo" href="/" aria-label="Pack-a-Bunch home" onClick={goHome}>
            <img src="/assets/wordmark.png" alt="Pack-a-Bunch" width="1027" height="213"/>
          </a>
          <p>Plan it. Pack it. Keep going.</p>
        </div>
        <nav aria-label="Information">
          <a href="/privacy" onClick={e => { if (onNavigate) { e.preventDefault(); onNavigate('/privacy'); } }}>Privacy</a>
          <a href="/terms" onClick={e => { if (onNavigate) { e.preventDefault(); onNavigate('/terms'); } }}>Terms</a>
          <a href="mailto:support@packabunch.site">Support</a>
        </nav>
      </footer>
    </div>
  );
}

/** One heading and its paragraphs, boxed so a long policy scans in blocks. */
export function Section({ heading, children }) {
  return (
    <section className="legal-section">
      <h2>{heading}</h2>
      {children}
    </section>
  );
}
