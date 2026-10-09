import { zodResolver } from '@hookform/resolvers/zod'
import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { deleteGrave, fetchGrave, saveGrave } from '@/features/grave/api'
import { buildGraveSchema, type GraveFormValues } from '@/features/grave/lib/schemas'
import { type Grave, GRAVE_KINDS } from '@/features/grave/types'
import { MediaCard } from '@/features/media/components/media-card'
import { PlaceName } from '@/features/place/components/place-name'
import { PlacePicker } from '@/features/place/components/place-picker'
import { CitationList } from '@/features/source/components/citation-card'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'
import { CoordinateFields } from '@/shared/components/coordinate-fields'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { coordinateText, parseCoordinate } from '@/shared/lib/coordinates'
import { voidSubmit } from '@/shared/lib/forms'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link GraveCard}. */
interface GraveCardProps {
  personId: number
}

/**
 * Refetches everything a grave write can change: the grave, and the owner's living flag everywhere it shows.
 *
 * @param queryClient the app's query client
 * @param personId whose grave changed
 */
async function invalidateAfterGraveChange(queryClient: QueryClient, personId: number): Promise<void> {
  // A mộ is a recorded death, so saving or removing one can flip "còn sống" on every screen (§8.8 D2).
  await Promise.all(
    [
      ['grave', personId],
      ['person', personId],
      ['persons'],
      ['tree'],
      ['revisions'],
      ['media'],
      ['citations'],
      ['sources'],
    ].map((queryKey) => queryClient.invalidateQueries({ queryKey })),
  )
}

/**
 * Shows and edits a person's mộ phần, with its photos and its citations.
 *
 * @param props the person whose grave this is
 * @returns the card element
 */
export function GraveCard({ personId }: GraveCardProps) {
  // Coordinates are optional but only useful together; the map link is what a family needs for tảo mộ.
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { mayEdit, mayDelete } = usePermissions()
  const [editing, setEditing] = useState(false)

  const { data: grave, isLoading, isError, error } = useQuery({
    queryKey: ['grave', personId],
    queryFn: () => fetchGrave(personId),
  })

  const removal = useMutation({
    mutationFn: (changeNote: string) => deleteGrave(personId, changeNote),
    onSuccess: async () => {
      await invalidateAfterGraveChange(queryClient, personId)
      toast.success(t('grave.deleted'))
    },
  })

  const mapUrl =
    grave?.latitude != null && grave.longitude != null
      ? `https://www.google.com/maps/search/?api=1&query=${grave.latitude},${grave.longitude}`
      : null

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>{t('grave.title')}</CardTitle>
        <div className="flex items-center gap-1">
          {/* Not offered after a failed read: saving then would overwrite a grave nobody could see (§8.8 #22). */}
          {mayEdit && !isError && !isLoading && (
            <Button variant="outline" size="sm" onClick={() => setEditing((current) => !current)}>
              {editing ? null : grave ? <Pencil aria-hidden /> : <Plus aria-hidden />}
              {editing ? t('common.cancel') : grave ? t('common.edit') : t('grave.add')}
            </Button>
          )}
          {mayDelete && grave && !editing && (
            <ConfirmDeleteDialog
              label={t('common.delete')}
              title={t('grave.deleteTitle')}
              description={t('grave.deleteDescription')}
              pending={removal.isPending}
              onConfirm={(note) => removal.mutateAsync(note)}
            />
          )}
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {isLoading && <p className="text-muted-foreground text-sm">{t('common.loading')}</p>}
        {isError && <p className="text-destructive text-sm">{problemMessage(error, t('common.unexpectedError'))}</p>}

        {!isLoading && !isError && !editing && !grave && (
          <p className="text-muted-foreground text-sm">{t('grave.empty')}</p>
        )}

        {!editing && grave && (
          <div className="space-y-1 text-sm">
            <Badge variant={grave.kind === 'LIVING_PLOT' ? 'secondary' : 'outline'}>
              {t(`grave.kinds.${grave.kind}`)}
            </Badge>
            {grave.placeId != null && (
              <p>
                <PlaceName id={grave.placeId} />
              </p>
            )}
            {grave.plot && <p>{grave.plot}</p>}
            {grave.notes && <p className="text-muted-foreground whitespace-pre-line">{grave.notes}</p>}
            {mapUrl && (
              <a
                className="text-primary underline-offset-4 hover:underline"
                href={mapUrl}
                target="_blank"
                rel="noreferrer"
              >
                {t('grave.openMap')}
              </a>
            )}
          </div>
        )}

        {editing && !isLoading && !isError && (
          // Keyed on the loaded record, so the fields start from it instead of being synced by an effect.
          <GraveForm
            key={grave?.id ?? 'new'}
            personId={personId}
            grave={grave ?? null}
            onSaved={() => setEditing(false)}
          />
        )}

        {/* The bia mộ is a source, and the tảo mộ photos belong to the grave, not to the person (§8.8 #18, #21). */}
        {grave && !editing && (
          <div className="space-y-4 border-t pt-4">
            <div className="space-y-2">
              <h3 className="text-sm font-medium">{t('source.citations')}</h3>
              <CitationList targetType="GRAVE" targetId={grave.id} />
            </div>
          </div>
        )}
      </CardContent>
      {grave && !editing && (
        <CardContent>
          <MediaCard targetType="GRAVE" targetId={grave.id} />
        </CardContent>
      )}
    </Card>
  )
}

/** Props of {@link GraveForm}. */
interface GraveFormProps {
  personId: number
  grave: Grave | null
  onSaved: () => void
}

/**
 * The editable form, initialised once from whatever was loaded, sending every field back on save.
 *
 * @param props the person, the existing grave if any, and the save callback
 * @returns the form element
 */
function GraveForm({ personId, grave, onSaved }: GraveFormProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const schema = useMemo(() => buildGraveSchema(t), [t])
  // Fixed when the form opens: a refetch meanwhile would upgrade it while the fields still hold the old values.
  const [openedVersion] = useState(grave?.version ?? null)

  const { control, register, handleSubmit, setValue, formState } = useForm<GraveFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      kind: grave?.kind ?? 'GRAVE',
      placeId: grave?.placeId ?? null,
      plot: grave?.plot ?? '',
      latitude: coordinateText(grave?.latitude ?? null),
      longitude: coordinateText(grave?.longitude ?? null),
      notes: grave?.notes ?? '',
      changeNote: '',
    },
  })
  const [latitude, longitude] = useWatch({ control, name: ['latitude', 'longitude'] })

  const mutation = useMutation({
    // Every field, placeId included: the server replaces the whole record, and an omitted place was erased (#2).
    mutationFn: (values: GraveFormValues) =>
      saveGrave(personId, {
        kind: values.kind,
        placeId: values.placeId,
        plot: values.plot.trim() || null,
        latitude: parseCoordinate(values.latitude),
        longitude: parseCoordinate(values.longitude),
        notes: values.notes.trim() || null,
        changeNote: values.changeNote.trim() || null,
        version: openedVersion,
      }),
    onSuccess: async () => {
      await invalidateAfterGraveChange(queryClient, personId)
      toast.success(t('grave.saved'))
      onSaved()
    },
  })

  return (
    <form className="space-y-3" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
      <div className="space-y-2">
        <Label htmlFor="grave-kind">{t('grave.kind')}</Label>
        <Controller
          control={control}
          name="kind"
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger id="grave-kind" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {GRAVE_KINDS.map((kind) => (
                  <SelectItem key={kind} value={kind}>
                    {t(`grave.kinds.${kind}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
        <p className="text-muted-foreground text-xs">{t('grave.kindHint')}</p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="grave-place">{t('grave.place')}</Label>
        <Controller
          control={control}
          name="placeId"
          render={({ field }) => <PlacePicker id="grave-place" value={field.value} onChange={field.onChange} />}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="grave-plot">{t('grave.plot')}</Label>
        <Input id="grave-plot" placeholder={t('grave.plotPlaceholder')} {...register('plot')} />
        {formState.errors.plot && <p className="text-destructive text-xs">{formState.errors.plot.message}</p>}
      </div>

      <CoordinateFields
        idPrefix="grave"
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
        <Label htmlFor="grave-notes">{t('grave.notes')}</Label>
        <Textarea id="grave-notes" {...register('notes')} />
        {formState.errors.notes && <p className="text-destructive text-xs">{formState.errors.notes.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="grave-change-note">{t('common.changeNote')}</Label>
        <Textarea
          id="grave-change-note"
          placeholder={t('common.changeNotePlaceholder')}
          {...register('changeNote')}
        />
      </div>

      <Button type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? t('common.saving') : t('common.save')}
      </Button>
    </form>
  )
}
