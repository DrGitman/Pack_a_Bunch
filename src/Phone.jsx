export default function Phone({ screen, eager = false }) {
  return <div className={`phone phone-${screen}`} role="img" aria-label={screen === 'plan' ? 'Pack-a-Bunch showing a 3D car boot packing plan and Start packing button' : 'Pack-a-Bunch showing saved car boot, shelf and cake box projects'}>
    <div className="phone-rim"><div className="phone-display">
      <div className="phone-status" aria-hidden="true"><span>9:41</span><i className="lens"/><i className="battery"/></div>
      <div className="phone-content"><img src={`/assets/${screen}.jpg`} alt="" width="540" height="1156" loading={eager ? 'eager' : 'lazy'} fetchPriority={eager ? 'high' : 'auto'}/></div>
      <div className="phone-bottom" aria-hidden="true"><i/></div>
    </div></div><i className="phone-volume"/><i className="phone-power"/>
  </div>;
}
