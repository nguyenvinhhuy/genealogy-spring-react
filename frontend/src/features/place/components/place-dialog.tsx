import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MapPinPlus, Pencil, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  createPlace,
  fetchAllPlaces,
  invalidatePlaceQueries,
  PLACE_QUERY_KEY,
  updatePlace,
} from '@/features/place/api'
import { PlaceSelect } from '@/features/place/components/place-select'
import { buildPlaceSchema, type PlaceFormValues } from '@/features/place/lib/schemas'
import { type Place, PLACE_TYPES } from '@/features/place/types'
import { CoordinateFields } from '@/shared/components/coordinate-fields'
import { useVersionAtOpen } from '@/shared/hooks/use-version-at-open'
import { subtreeOf } from '@/shared/lib/hierarchy'
import { coordinateText, parseCoordinate } from '@/shared/lib/coordinates'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link PlaceDialog}. */
interface PlaceDialogProps {
  // Omitted to create a place; passed to rename, re-level or move an existing one.
  place?: Place
  // "inline" is the small trigger inside a place picker, so a missing xã is added without leaving the form.
  variant?: 'page' | 'inline'
  // Told about the saved place, so an inline create can select it straight away.
  onSaved?: (place: Place) => void
}

/**
 * Creates a place, or renames, re-levels or moves an existing one, recording why.
 *
 * @param props the place being edited, the trigger style and the save callback
 * @returns the dialog with its trigger button
 */
export function PlaceDialog({ place, variant = 'page', onSaved }: PlaceDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const version = useVersionAtOpen(place?.version ?? null, open)
  const schema = useMemo(() => buildPlaceSchema(t), [t])
  const defaults: PlaceFormValues = {
    name: place?.name ?? '',
    type: place?.type ?? 'WARD',
    parentId: place?.parentId ?? null,
    latitude: coordinateText(place?.latitude ?? null),
    longitude: coordinateText(place?.longitude ?? null),
    changeNote: '',
  }

  const { control, register, handleSubmit, reset, setValue, formState } = useForm<PlaceFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })
  // useWatch rather than watch(): the compiler cannot memoise a component that calls watch().
  const [latitude, longitude, type] = useWatch({ control, name: ['latitude', 'longitude', 'type'] })

  // Only when editing: the whole list is the page's own cached query, so this costs no request there.
  const everyPlace = useQuery({
    queryKey: [...PLACE_QUERY_KEY, 'all'],
    queryFn: fetchAllPlaces,
    enabled: open && place != null,
  })
  const ownSubtree = useMemo(
    () => (place && everyPlace.data ? subtreeOf(everyPlace.data, place.id) : new Set<number>(place ? [place.id] : [])),
    [place, everyPlace.data],
  )

  const mutation = useMutation({
    mutationFn: (values: PlaceFormValues) => {
      const payload = {
        name: values.name.trim(),
        type: values.type,
        parentId: values.parentId,
        latitude: parseCoordinate(values.latitude),
        longitude: parseCoordinate(values.longitude),
        changeNote: values.changeNote.trim() || null,
        version,
      }
      return place ? updatePlace(place.id, payload) : createPlace(payload)
    },
    onSuccess: async (saved) => {
      await invalidatePlaceQueries(queryClient)
      toast.success(t(place ? 'place.updated' : 'place.created'))
      setOpen(false)
      onSaved?.(saved)
    },
  })

  const idPrefix = place ? `place-${place.id}` : `place-new-${variant}`

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) {
          reset(defaults)
        }
      }}
    >
      <DialogTrigger asChild>
        {place ? (
          <Button variant="ghost" size="sm">
            <Pencil aria-hidden />
            {t('common.edit')}
          </Button>
        ) : variant === 'inline' ? (
          <Button type="button" variant="link" size="sm" className="h-auto px-0">
            <MapPinPlus aria-hidden />
            {t('place.addMissing')}
          </Button>
        ) : (
          <Button size="sm">
            <Plus aria-hidden />
            {t('place.create')}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(place ? 'place.editTitle' : 'place.create')}</DialogTitle>
        </DialogHeader>
        {/* stopPropagation: inside another form (an event dialog), a submit here would also submit that one. */}
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.stopPropagation()
            void handleSubmit((values) => mutation.mutate(values))(event)
          }}
        >
          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-name`}>{t('place.name')}</Label>
            <Input id={`${idPrefix}-name`} {...register('name')} />
            {formState.errors.name && <p className="text-destructive text-xs">{formState.errors.name.message}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-type`}>{t('place.type')}</Label>
            <Controller
              control={control}
              name="type"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id={`${idPrefix}-type`} className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PLACE_TYPES.map((type) => (
                      <SelectItem key={type} value={type}>
                        {t(`place.types.${type}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-parent`}>{t('place.parent')}</Label>
            <Controller
              control={control}
              name="parentId"
              render={({ field }) => (
                <PlaceSelect
                  id={`${idPrefix}-parent`}
                  value={field.value}
                  onChange={field.onChange}
                  excludeIds={ownSubtree}
                  childType={type}
                />
              )}
            />
            <p className="text-muted-foreground text-xs">{t('place.parentHint')}</p>
          </div>

          <CoordinateFields
            idPrefix={idPrefix}
            latitude={latitude}
            longitude={longitude}
            onChange={(nextLatitude, nextLongitude) => {
              setValue('latitude', nextLatitude, { shouldValidate: formState.isSubmitted })
              setValue('longitude', nextLongitude, { shouldValidate: formState.isSubmitted })
            }}
            latitudeError={formState.errors.latitude?.message}
            longitudeError={formState.errors.longitude?.message}
          />

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-change-note`}>{t('common.changeNote')}</Label>
            <Textarea
              id={`${idPrefix}-change-note`}
              placeholder={t('common.changeNotePlaceholder')}
              {...register('changeNote')}
            />
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? t('common.saving') : t('common.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
