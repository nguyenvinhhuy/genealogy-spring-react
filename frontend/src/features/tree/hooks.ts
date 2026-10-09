import { select } from 'd3-selection'
import { zoom, zoomIdentity, type D3ZoomEvent, type ZoomBehavior, type ZoomTransform } from 'd3-zoom'
import { useEffect, useRef, useState, type RefObject } from 'react'

/** What a pannable chart needs to draw itself. */
interface PanZoom {
  svgRef: RefObject<SVGSVGElement | null>
  transform: ZoomTransform
  size: { width: number; height: number }
}

/**
 * Wires d3-zoom and a resize observer onto one SVG element.
 *
 * @param minScale the furthest the chart may zoom out
 * @param maxScale the furthest it may zoom in
 * @param resetKey any value that changes when the chart shows something new, which recentres it
 * @returns the ref to attach, the current transform and the element's measured size
 */
export function usePanZoom(minScale: number, maxScale: number, resetKey: unknown): PanZoom {
  // d3 owns the gesture and React the rendering; the transform lives in state so the two never fight.
  const svgRef = useRef<SVGSVGElement>(null)
  const behaviourRef = useRef<ZoomBehavior<SVGSVGElement, unknown> | null>(null)
  const [transform, setTransform] = useState<ZoomTransform>(zoomIdentity)
  const [size, setSize] = useState({ width: 0, height: 0 })

  useEffect(() => {
    const svg = svgRef.current
    if (!svg) {
      return
    }
    const observer = new ResizeObserver(([entry]) => {
      setSize({ width: entry.contentRect.width, height: entry.contentRect.height })
    })
    observer.observe(svg)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    const svg = svgRef.current
    if (!svg) {
      return
    }
    const behaviour = zoom<SVGSVGElement, unknown>()
      .scaleExtent([minScale, maxScale])
      .on('zoom', (event: D3ZoomEvent<SVGSVGElement, unknown>) => setTransform(event.transform))
    behaviourRef.current = behaviour
    select(svg).call(behaviour)
    return () => {
      select(svg).on('.zoom', null)
    }
  }, [minScale, maxScale])

  useEffect(() => {
    const svg = svgRef.current
    const behaviour = behaviourRef.current
    // A new focus person is laid out around the origin again, so an old pan would leave them off-screen.
    if (svg && behaviour) {
      behaviour.transform(select(svg), zoomIdentity)
    }
  }, [resetKey])

  return { svgRef, transform, size }
}
