import release from './release.json';
export default function Download() {
  return <section className="download-section" id="download" aria-labelledby="download-title"><div className="container download-grid" data-reveal>
    <div><p className="eyebrow">TRY IT FOR YOURSELF</p><h2 id="download-title">Your next pack.<br/>On your phone.</h2><p className="description">Download the Android preview and try a real packing plan.</p>
      <a className="button download-button" href={release.url} download="pack-a-bunch-preview.apk"><svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/></svg> Download Android APK</a>
      <p className="download-meta">v{release.version} · {(release.bytes / 1000000).toFixed(1)} MB<br/>Android 8+ · 64-bit Android device</p>
    </div>
    <div className="install-card"><h3>Ready in a few steps.</h3><ol><li><strong>Download on your Android phone.</strong><span>Open the APK from your browser’s downloads.</span></li><li><strong>Allow this installation if prompted.</strong><span>Android may ask you to allow installs from your browser. You can turn that permission off afterwards.</span></li><li><strong>Make your first pack.</strong><span>Sign up or sign in, enter your space and item sizes, then follow the packing guide. Or take one photo of your things and let the app measure them.</span></li></ol><details><summary>Build details</summary><p>Package: {release.applicationId}<br/>Build supplied: {release.builtAt}</p><p className="checksum">SHA-256<br/>{release.sha256}</p></details></div>
  </div></section>;
}
