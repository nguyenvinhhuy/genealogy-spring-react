import { PlaceDialog } from '@/features/place/components/place-dialog'
import { PlaceSelect } from '@/features/place/components/place-select'
import { usePermissions } from '@/shared/hooks/use-permissions'

/** Props of {@link PlacePicker}. */
interface PlacePickerProps {
  id: string
  value: number | null
  onChange: (value: number | null) => void
}

/**
 * Picks a recorded place by searching its name, and lets an editor add a missing one without leaving the form.
 *
 * @param props the field id, the chosen place id and the change handler
 * @returns the picker element
 */
export function PlacePicker({ id, value, onChange }: PlacePickerProps) {
  // Inline, because a place that has to be created on another page first is a place nobody records (§6.1).
  const { mayEdit } = usePermissions()
  return (
    <div className="space-y-1">
      <PlaceSelect id={id} value={value} onChange={onChange} />
      {mayEdit && <PlaceDialog variant="inline" onSaved={(place) => onChange(place.id)} />}
    </div>
  )
}
