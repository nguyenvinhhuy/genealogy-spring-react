import { Download, FileImage, FileType, Printer } from 'lucide-react'
import type { RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  chartFileName,
  downloadPng,
  downloadSvg,
  exportChart,
  printChart,
} from '@/features/tree/lib/export-chart'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'

/** Props of {@link ExportChartMenu}. */
interface ExportChartMenuProps {
  // The element the chart is drawn in; what it shows is what is exported.
  chartRef: RefObject<HTMLDivElement | null>
  // The person at the centre, who names the file and the printed page.
  personName: string
}

/**
 * Offers the chart on screen as a PNG image, an SVG file, or a printed page.
 *
 * @param props where the chart is drawn and who is at its centre
 * @returns the menu with its trigger button
 */
export function ExportChartMenu({ chartRef, personName }: ExportChartMenuProps) {
  // Exported from the drawing on screen, so it holds only what this viewer may see and no whole-tree request (§3.5).
  const { t } = useTranslation()

  const chart = () => {
    const exported = chartRef.current ? exportChart(chartRef.current) : null
    if (!exported) {
      toast.error(t('tree.export.nothing'))
    }
    return exported
  }

  const savePng = async () => {
    const exported = chart()
    if (!exported) {
      return
    }
    try {
      await downloadPng(exported, chartFileName(personName))
    } catch {
      toast.error(t('tree.export.failed'))
    }
  }

  const saveSvg = () => {
    const exported = chart()
    if (exported) {
      downloadSvg(exported, chartFileName(personName))
    }
  }

  const print = () => {
    const exported = chart()
    if (exported && !printChart(exported, t('tree.export.printTitle', { name: personName }))) {
      toast.warning(t('tree.export.popupBlocked'))
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline">
          <Download aria-hidden />
          {t('tree.export.button')}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-52">
        <DropdownMenuItem onSelect={() => void savePng()}>
          <FileImage aria-hidden />
          {t('tree.export.png')}
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={saveSvg}>
          <FileType aria-hidden />
          {t('tree.export.svg')}
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={print}>
          <Printer aria-hidden />
          {t('tree.export.print')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
