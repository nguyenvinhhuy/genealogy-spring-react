import { saveBlob } from '@/shared/lib/download'

// Breathing room around the drawing, in the chart's own units.
const PADDING = 32
// Twice the drawn size, so a printed or zoomed PNG stays sharp.
const PNG_SCALE = 2

// Every look the chart takes from its classes; copied inline because a saved SVG has no stylesheet.
const STYLE_PROPERTIES = [
  'fill',
  'fill-opacity',
  'stroke',
  'stroke-width',
  'stroke-dasharray',
  'stroke-opacity',
  'opacity',
  'font-family',
  'font-size',
  'font-weight',
  'font-style',
  'text-anchor',
  'dominant-baseline',
] as const

/** A chart drawn out of the page, ready to be saved or printed. */
export interface ExportedChart {
  svg: string
  width: number
  height: number
}

/**
 * Copies the chart inside a container into a stand-alone SVG: styles inlined, the pan and zoom undone.
 *
 * @param container the element the chart is drawn in
 * @returns the SVG text and its size, or null when there is no chart to export
 */
export function exportChart(container: HTMLElement): ExportedChart | null {
  const source = container.querySelector('svg')
  // The outermost group carries the viewer's pan and zoom; its contents are the whole drawing.
  const zoomLayer = source?.querySelector(':scope > g')
  if (!source || !(zoomLayer instanceof SVGGElement)) {
    return null
  }
  const box = zoomLayer.getBBox()
  const width = Math.ceil(box.width + 2 * PADDING)
  const height = Math.ceil(box.height + 2 * PADDING)

  const clone = source.cloneNode(true) as SVGSVGElement
  // Always the light palette, ivory paper and brown ink: a dark-mode export printed as a sheet of black.
  const root = document.documentElement
  const wasDark = root.classList.contains('dark')
  root.classList.remove('dark')
  let paper: string
  try {
    inlineStyles(source, clone)
    paper = getComputedStyle(document.body).backgroundColor
  } finally {
    // Restored in the same task, so the page never paints in the other theme.
    root.classList.toggle('dark', wasDark)
  }
  clone.querySelector(':scope > g')?.removeAttribute('transform')
  clone.removeAttribute('class')
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  clone.setAttribute('viewBox', `${box.x - PADDING} ${box.y - PADDING} ${width} ${height}`)
  clone.setAttribute('width', String(width))
  clone.setAttribute('height', String(height))

  // The paper colour behind the drawing, or a PNG is transparent.
  const background = document.createElementNS('http://www.w3.org/2000/svg', 'rect')
  background.setAttribute('x', String(box.x - PADDING))
  background.setAttribute('y', String(box.y - PADDING))
  background.setAttribute('width', String(width))
  background.setAttribute('height', String(height))
  background.setAttribute('fill', paper)
  clone.insertBefore(background, clone.firstChild)

  return { svg: new XMLSerializer().serializeToString(clone), width, height }
}

/**
 * Copies the computed look of every element of a drawing onto its clone, walking both trees together.
 *
 * @param source the element as drawn on the page
 * @param clone its copy
 */
function inlineStyles(source: Element, clone: Element): void {
  const computed = getComputedStyle(source)
  const style = STYLE_PROPERTIES.map((property) => `${property}:${computed.getPropertyValue(property)}`).join(
    ';',
  )
  clone.setAttribute('style', style)
  clone.removeAttribute('class')
  for (let index = 0; index < source.children.length; index++) {
    inlineStyles(source.children[index], clone.children[index])
  }
}

/**
 * Saves a chart as an SVG file.
 *
 * @param chart the exported chart
 * @param fileName the name to save it under, without extension
 */
export function downloadSvg(chart: ExportedChart, fileName: string): void {
  saveBlob(new Blob([chart.svg], { type: 'image/svg+xml;charset=utf-8' }), `${fileName}.svg`)
}

/**
 * Saves a chart as a PNG image, drawn at twice its size.
 *
 * @param chart the exported chart
 * @param fileName the name to save it under, without extension
 * @returns once the image has been handed to the browser
 */
export async function downloadPng(chart: ExportedChart, fileName: string): Promise<void> {
  const url = URL.createObjectURL(new Blob([chart.svg], { type: 'image/svg+xml;charset=utf-8' }))
  try {
    const image = new Image()
    image.src = url
    await image.decode()
    const canvas = document.createElement('canvas')
    canvas.width = chart.width * PNG_SCALE
    canvas.height = chart.height * PNG_SCALE
    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('canvas unavailable')
    }
    context.scale(PNG_SCALE, PNG_SCALE)
    context.drawImage(image, 0, 0, chart.width, chart.height)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'))
    if (!blob) {
      throw new Error('png encoding failed')
    }
    saveBlob(blob, `${fileName}.png`)
  } finally {
    URL.revokeObjectURL(url)
  }
}

/**
 * Opens a chart alone in a new window and asks the browser to print it, which also saves to PDF.
 *
 * @param chart the exported chart
 * @param title the title the printed page carries
 * @returns false when the browser blocked the new window
 */
export function printChart(chart: ExportedChart, title: string): boolean {
  const printWindow = window.open('', '_blank')
  if (!printWindow) {
    return false
  }
  const page = printWindow.document
  page.title = title
  const style = page.createElement('style')
  // Landscape, as a family spreads sideways; the drawing scales to the page instead of running off it.
  style.textContent =
    '@page { size: landscape; margin: 12mm } body { margin: 0 } svg { width: 100%; height: auto }'
  page.head.appendChild(style)
  page.body.innerHTML = chart.svg
  printWindow.focus()
  printWindow.print()
  return true
}

/**
 * Turns a person's name into a file name, so "Nguyễn Văn Cả" saves as "cay-pha-he-nguyen-van-ca".
 *
 * @param name the name of the person at the centre
 * @returns a file name without extension
 */
export function chartFileName(name: string): string {
  const ascii = name
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .replace(/[đĐ]/g, 'd')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
  return ascii ? `cay-pha-he-${ascii}` : 'cay-pha-he'
}
