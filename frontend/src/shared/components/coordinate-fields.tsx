import { useTranslation } from 'react-i18next'

import { splitCoordinatePair } from '@/shared/lib/coordinates'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

/** Props of {@link CoordinateFields}. */
interface CoordinateFieldsProps {
  idPrefix: string
  latitude: string
  longitude: string
  onChange: (latitude: string, longitude: string) => void
  latitudeError?: string
  longitudeError?: string
}

/**
 * Two side-by-side fields for a latitude and a longitude, accepting a pasted "lat, lng" pair in either.
 *
 * @param props the field values, their errors and the change handler
 * @returns the two fields and the hint beneath them
 */
export function CoordinateFields({
  idPrefix,
  latitude,
  longitude,
  onChange,
  latitudeError,
  longitudeError,
}: CoordinateFieldsProps) {
  const { t } = useTranslation()

  // Google Maps copies both at once, and pasting that into one field used to be refused as not a number.
  const onPaste = (event: React.ClipboardEvent<HTMLInputElement>) => {
    const pair = splitCoordinatePair(event.clipboardData.getData('text'))
    if (pair) {
      event.preventDefault()
      onChange(String(pair[0]), String(pair[1]))
    }
  }

  return (
    <div className="space-y-2">
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-lat`}>{t('coordinates.latitude')}</Label>
          <Input
            id={`${idPrefix}-lat`}
            inputMode="decimal"
            value={latitude}
            placeholder="21.028511"
            onPaste={onPaste}
            onChange={(event) => onChange(event.target.value, longitude)}
          />
          {latitudeError && <p className="text-destructive text-xs">{latitudeError}</p>}
        </div>
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-lng`}>{t('coordinates.longitude')}</Label>
          <Input
            id={`${idPrefix}-lng`}
            inputMode="decimal"
            value={longitude}
            placeholder="105.804817"
            onPaste={onPaste}
            onChange={(event) => onChange(latitude, event.target.value)}
          />
          {longitudeError && <p className="text-destructive text-xs">{longitudeError}</p>}
        </div>
      </div>
      <p className="text-muted-foreground text-xs">{t('coordinates.hint')}</p>
    </div>
  )
}
