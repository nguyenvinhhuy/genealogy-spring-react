import { cn } from '@/shared/lib/utils'

/** Props of {@link Logo}. */
interface LogoProps {
  className?: string
}

/**
 * Draws the app's mark: a red seal holding a family tree, after the con dấu stamped on an old gia phả.
 *
 * @param props extra classes for sizing
 * @returns the mark
 */
export function Logo({ className }: LogoProps) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden className={cn('size-8', className)}>
      <rect x="1.5" y="1.5" width="29" height="29" rx="4" className="fill-seal" />
      <rect
        x="4"
        y="4"
        width="24"
        height="24"
        rx="2"
        fill="none"
        strokeWidth="1"
        className="stroke-seal-foreground/70"
      />
      <g className="stroke-seal-foreground" strokeWidth="1.6" strokeLinecap="round" fill="none">
        <path d="M16 9v5M10 14h12M10 14v4M22 14v4M16 14v4M10 18v3M22 18v3" />
      </g>
      <g className="fill-seal-foreground">
        <circle cx="16" cy="8.5" r="2" />
        <circle cx="10" cy="22" r="1.7" />
        <circle cx="16" cy="19.5" r="1.7" />
        <circle cx="22" cy="22" r="1.7" />
      </g>
    </svg>
  )
}
