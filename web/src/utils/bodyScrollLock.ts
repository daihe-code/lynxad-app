const locks = new Set<symbol>();
let previousOverflow = '';

export function lockBodyScroll(): () => void {
  const owner = Symbol('body-scroll-lock');
  if (!locks.size) previousOverflow = document.body.style.overflow;
  locks.add(owner);
  document.body.style.overflow = 'hidden';

  return () => {
    if (!locks.delete(owner)) return;
    if (!locks.size) document.body.style.overflow = previousOverflow;
  };
}
