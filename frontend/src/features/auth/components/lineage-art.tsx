/**
 * Draws the sign-in page's picture: a family tree spreading from one ancestor, over a lattice of cloud scrolls.
 *
 * @returns the illustration
 */
export function LineageArt() {
  // Plain SVG in theme colours, so it follows light and dark and needs no image file or network request.
  const generations = [
    [200],
    [120, 280],
    [70, 160, 240, 330],
    [40, 95, 135, 185, 215, 265, 305, 360],
  ]
  const rowY = [70, 150, 230, 310]

  return (
    <svg viewBox="0 0 400 400" className="h-auto w-full max-w-sm" aria-hidden>
      <defs>
        <pattern id="cloud-lattice" width="40" height="40" patternUnits="userSpaceOnUse">
          <path
            d="M0 20 Q10 10 20 20 T40 20 M20 0 Q30 10 20 20 T20 40"
            fill="none"
            strokeWidth="0.8"
            className="stroke-seal-foreground/10"
          />
        </pattern>
      </defs>
      <rect width="400" height="400" fill="url(#cloud-lattice)" />

      <g className="stroke-seal-foreground/70" strokeWidth="1.6" fill="none" strokeLinecap="round">
        {generations.slice(1).map((row, depth) =>
          row.map((x, index) => {
            const parentX = generations[depth][Math.floor(index / 2)]
            const fromY = rowY[depth] + 12
            const toY = rowY[depth + 1] - 12
            const midY = (fromY + toY) / 2
            return <path key={`${depth}-${x}`} d={`M${parentX} ${fromY} V${midY} H${x} V${toY}`} />
          }),
        )}
      </g>

      {generations.map((row, depth) =>
        row.map((x) => (
          <g key={`${depth}-${x}`}>
            <circle
              cx={x}
              cy={rowY[depth]}
              r={depth === 0 ? 14 : 12 - depth * 1.5}
              className={depth === 0 ? 'fill-bronze' : 'fill-seal-foreground'}
            />
            {depth === 0 && <circle cx={x} cy={rowY[0]} r="20" fill="none" strokeWidth="1" className="stroke-bronze" />}
          </g>
        )),
      )}
    </svg>
  )
}
