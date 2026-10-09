import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FileText, Images } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { deleteMedia, fetchMedia, MEDIA_QUERY_KEY, MEDIA_REFRESH_MS, mediaQueryKey } from '@/features/media/api'
import { MediaEditDialog } from '@/features/media/components/media-edit-dialog'
import { MediaUploadDialog } from '@/features/media/components/media-upload-dialog'
import type { Media, MediaTargetType } from '@/features/media/types'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { formatDateOnly } from '@/shared/lib/format-moment'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'

// The square every tile is drawn in, image or not, so a PDF sits in the grid like a photo.
const TILE_FRAME =
  'bg-muted relative flex aspect-square w-full items-center justify-center overflow-hidden rounded-md border'

/** Props of {@link MediaCard} and {@link MediaGallery}. */
interface MediaCardProps {
  targetType: MediaTargetType
  targetId: number
}

/**
 * Shows, uploads, edits and removes the photos and scans of one record, as a card of its own.
 *
 * @param props which record the files belong to
 * @returns the card element
 */
export function MediaCard({ targetType, targetId }: MediaCardProps) {
  const { t } = useTranslation()
  const { mayEdit } = usePermissions()

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>{t(targetType === 'SOURCE' ? 'media.scansTitle' : 'media.title')}</CardTitle>
        {mayEdit && <MediaUploadDialog targetType={targetType} targetId={targetId} />}
      </CardHeader>
      <CardContent>
        <MediaGallery targetType={targetType} targetId={targetId} />
      </CardContent>
    </Card>
  )
}

/**
 * A button that opens one record's gallery in place, for a panel that holds several records.
 *
 * @param props which record the files belong to
 * @returns the toggle and, when open, the gallery
 */
export function MediaToggle({ targetType, targetId }: MediaCardProps) {
  // Fetched on open, like CitationToggle beside it: a person with four unions would be four requests up front.
  const { t } = useTranslation()
  const { mayEdit } = usePermissions()
  const [open, setOpen] = useState(false)
  return (
    <div className="space-y-2">
      <Button
        variant="ghost"
        size="sm"
        className="-ml-2"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <Images aria-hidden />
        {open ? t('media.hide') : t(targetType === 'SOURCE' ? 'media.scansTitle' : 'media.title')}
      </Button>
      {open && (
        <div className="space-y-2 rounded-md border border-dashed p-3">
          {mayEdit && <MediaUploadDialog targetType={targetType} targetId={targetId} />}
          <MediaGallery targetType={targetType} targetId={targetId} />
        </div>
      )}
    </div>
  )
}

/**
 * A button that opens one record's gallery in a wide dialog, for a table row or a line with no room for it.
 *
 * @param props which record the files belong to, and what the dialog is titled
 * @returns the dialog with its trigger button
 */
export function MediaDialog({ targetType, targetId, title }: MediaCardProps & { title: string }) {
  // The source page and a citation both reach a source's scans this way (§8.9 D2).
  const { t } = useTranslation()
  const { mayEdit } = usePermissions()
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm">
          <Images aria-hidden />
          {t(targetType === 'SOURCE' ? 'media.scansTitle' : 'media.title')}
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-[min(96vw,900px)] sm:max-w-[min(96vw,900px)]">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{t(targetType === 'SOURCE' ? 'media.scansTitle' : 'media.title')}</DialogDescription>
        </DialogHeader>
        {mayEdit && (
          <div>
            <MediaUploadDialog targetType={targetType} targetId={targetId} />
          </div>
        )}
        <div className="max-h-[70vh] overflow-y-auto">
          <MediaGallery targetType={targetType} targetId={targetId} />
        </div>
      </DialogContent>
    </Dialog>
  )
}

/**
 * The gallery of one record's files, for a card or a panel that already has its own heading.
 *
 * @param props which record the files belong to
 * @returns the gallery element
 */
export function MediaGallery({ targetType, targetId }: MediaCardProps) {
  // Refetched well inside the signed URL's hour, so a page left open still opens its photos (§8.9 #30).
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { mayEdit, mayDelete } = usePermissions()

  const { data: files = [], isLoading, isError } = useQuery({
    queryKey: mediaQueryKey(targetType, targetId),
    queryFn: () => fetchMedia(targetType, targetId),
    staleTime: MEDIA_REFRESH_MS,
    refetchInterval: MEDIA_REFRESH_MS,
  })

  const refresh = () => {
    // Every gallery: a new portrait demotes the old one, and a merge may have drawn this file elsewhere.
    void queryClient.invalidateQueries({ queryKey: [MEDIA_QUERY_KEY] })
    void queryClient.invalidateQueries({ queryKey: ['revisions'] })
  }

  const remove = useMutation({
    mutationFn: ({ id, changeNote }: { id: number; changeNote: string }) => deleteMedia(id, changeNote),
    onSuccess: () => {
      toast.success(t('media.removed'))
      refresh()
    },
  })

  if (isLoading) {
    return <p className="text-muted-foreground text-sm">{t('common.loading')}</p>
  }
  // Its own state: an error read as "no photos yet" invites uploading the same scans a second time.
  if (isError) {
    return <p className="text-destructive text-sm">{t('media.loadFailed')}</p>
  }
  if (files.length === 0) {
    return <p className="text-muted-foreground text-sm">{t('media.empty')}</p>
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {files.map((file) => (
          <MediaTile
            key={file.id}
            file={file}
            mayEdit={mayEdit}
            mayDelete={mayDelete}
            onRemove={(changeNote) => remove.mutateAsync({ id: file.id, changeNote })}
            removing={remove.isPending}
          />
        ))}
      </div>
      {targetType === 'PERSON' && <p className="text-muted-foreground text-xs">{t('media.portraitHint')}</p>}
    </div>
  )
}

/** Props of {@link MediaTile}. */
interface MediaTileProps {
  file: Media
  mayEdit: boolean
  mayDelete: boolean
  onRemove: (changeNote: string) => Promise<unknown>
  removing: boolean
}

/**
 * One file in the gallery, opening large when clicked, with the controls the reader is allowed to use.
 *
 * @param props the file and what may be done to it
 * @returns the tile element
 */
function MediaTile({ file, mayEdit, mayDelete, onRemove, removing }: MediaTileProps) {
  const { t } = useTranslation()
  const isImage = file.contentType.startsWith('image/')
  const label = file.caption ?? file.filename

  return (
    <figure className="space-y-1">
      <MediaViewer file={file}>
        <button
          type="button"
          aria-label={t('media.open', { name: label })}
          className={TILE_FRAME}
        >
          {isImage ? (
            <img
              src={file.thumbnailUrl}
              alt={label}
              className="size-full object-cover"
              // Lazy: a source with eighty A3 scans otherwise asks for all eighty at once.
              loading="lazy"
              width={320}
              height={320}
            />
          ) : (
            <span className="text-muted-foreground flex flex-col items-center gap-1 px-2 text-center text-xs">
              <FileText className="size-8" aria-hidden />
              {file.filename}
            </span>
          )}
          {file.kind === 'PORTRAIT' && <Badge className="absolute top-1 left-1">{t('media.kinds.PORTRAIT')}</Badge>}
        </button>
      </MediaViewer>
      <figcaption className="space-y-0.5 text-xs">
        <p className="truncate">{label}</p>
        <p className="text-muted-foreground truncate">
          {t('media.uploadedBy', {
            name: file.uploadedByName ?? t('media.unknownUploader'),
            date: formatDateOnly(file.createdAt),
          })}
        </p>
      </figcaption>
      {/* Choosing the portrait is the edit dialog's kind field: one dialog per record's fields (§6.3). */}
      {(mayEdit || mayDelete) && (
        <div className="flex flex-wrap gap-1">
          {mayEdit && <MediaEditDialog file={file} />}
          {mayDelete && (
            <ConfirmDeleteDialog
              label={t('common.remove')}
              title={t('media.deleteTitle')}
              description={t('media.deleteDescription', { name: file.filename })}
              pending={removing}
              onConfirm={onRemove}
            />
          )}
        </div>
      )}
    </figure>
  )
}

/** Props of {@link MediaViewer}. */
interface MediaViewerProps {
  file: Media
  children: React.ReactNode
}

/**
 * Opens one file at full size: the original image, or a link to the PDF.
 *
 * @param props the file, and the element that opens it
 * @returns the dialog with its trigger
 */
function MediaViewer({ file, children }: MediaViewerProps) {
  // A 320 px square crop cannot be read, and a scan exists to be read (§8.9 #20).
  const { t } = useTranslation()
  const isImage = file.contentType.startsWith('image/')

  return (
    <Dialog>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-[min(96vw,1200px)] sm:max-w-[min(96vw,1200px)]">
        <DialogHeader>
          <DialogTitle>{file.caption ?? file.filename}</DialogTitle>
          <DialogDescription>{t(`media.kinds.${file.kind}`)}</DialogDescription>
        </DialogHeader>
        {isImage ? (
          <img src={file.url} alt={file.caption ?? file.filename} className="max-h-[75vh] w-full object-contain" />
        ) : (
          <p className="text-sm">{t('media.notPreviewable')}</p>
        )}
        <a
          href={file.url}
          target="_blank"
          rel="noreferrer"
          className="text-sm underline underline-offset-4"
        >
          {t('media.openOriginal')}
        </a>
      </DialogContent>
    </Dialog>
  )
}
