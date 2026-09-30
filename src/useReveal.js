import { useEffect } from 'react';
export default function useReveal() {
  useEffect(() => {
    const media = matchMedia('(prefers-reduced-motion: reduce)');
    const elements = [...document.querySelectorAll('[data-reveal]')];
    if (media.matches || !('IntersectionObserver' in window)) return;
    // Only hide offscreen content after enhancement succeeds; initial content stays visible.
    const observer = new IntersectionObserver(entries => entries.forEach(entry => {
      if (entry.isIntersecting) { entry.target.classList.remove('reveal-pending'); observer.unobserve(entry.target); }
    }), { threshold: 0.08 });
    elements.forEach(el => { if (el.getBoundingClientRect().top >= innerHeight) { el.classList.add('reveal-pending'); observer.observe(el); } });
    const showAll = () => { if (media.matches) elements.forEach(el => el.classList.remove('reveal-pending')); };
    media.addEventListener('change', showAll);
    return () => { observer.disconnect(); media.removeEventListener('change', showAll); elements.forEach(el => el.classList.remove('reveal-pending')); };
  }, []);
}
