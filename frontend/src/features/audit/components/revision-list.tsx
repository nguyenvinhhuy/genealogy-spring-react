import { useInfiniteQuery } from '@tanstack/react-query'
import { History } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'

import { fetchRevisions } from '@/features/audit/api'
import type { AuditEntityType, Revision } from '@/features/audit/types'
import { formatMoment } from '@/shared/lib/format-moment'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Separator } from '@/shared/ui/separator'

/** Which record a history belongs to. */
export interface RevisionTarget {
  entityType: AuditEntityType
  entityId: number
}

/**
 * Lists who changed one record, when, and on what basis (CLAUDE.md §3.8).
 *
 * @param props which record the history belongs to
 * @returns the list element
 */
export function RevisionList({ entityType, entityId }: RevisionTarget) {
  const { t } = useTranslation()

  const { data, isLoading, isError, error, hasNextPage, fetchNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ['revisions', entityType, entityId],
    queryFn: ({ pageParam }) => fetchRevisions(entityType, entityId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page.number + 1 < last.page.totalPages ? last.page.number + 1 : undefined),
  })
  // Pages are appended: replacing the list with the next page hid the newest entries behind "Xem thêm".
  const revisions = data?.pages.flatMap((page) => page.content) ?? []

  if (isLoading) {
    return <p className="text-muted-foreground text-sm">{t('common.loading')}</p>
  }
  // A trail that failed to load must not read as "never changed", so failure gets its own state.
  if (isError) {
    return <p className="text-destructive text-sm">{problemMessage(error, t('common.unexpectedError'))}</p>
  }
  if (revisions.length === 0) {
    return <p className="text-muted-foreground text-sm">{t('audit.empty')}</p>
  }
  return (
    <div className="space-y-3">
      {revisions.map((revision, index) => (
        <div key={revision.id} className="space-y-1 text-sm">
          {/* Entries are multi-line, so spacing alone let one entry's note read as the entry above's. */}
          {index > 0 && <Separator className="mb-3" />}
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={revision.action === 'DELETE' ? 'destructive' : 'outline'}>
              {t(`audit.actions.${revision.action}`)}
            </Badge>
            <span className="text-muted-foreground">
              {formatMoment(revision.changedAt)}
              {revision.changedByName && ` · ${revision.changedByName}`}
            </span>
          </div>
          {revision.note && <p className="text-muted-foreground border-l-2 pl-3 italic">{revision.note}</p>}
          <ChangeSummary revision={revision} />
        </div>
      ))}
      {/* A page at a time, not the whole trail: a long-lived record can carry hundreds of revisions. */}
      {hasNextPage && (
        <Button variant="ghost" size="sm" disabled={isFetchingNextPage} onClick={() => void fetchNextPage()}>
          {t('audit.loadMore')}
        </Button>
      )}
    </div>
  )
}

/**
 * A "Lịch sử" button that opens a record's history below it, for records too small to carry a card of their own.
 *
 * @param props which record the history belongs to
 * @returns the button, and the history below it once opened
 */
export function RevisionToggle({ entityType, entityId }: RevisionTarget) {
  // Closed by default and fetched only when opened: a page of unions and events would otherwise load every trail.
  const { t } = useTranslation()
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
        <History aria-hidden />
        {t(open ? 'audit.hide' : 'audit.show')}
      </Button>
      {open && (
        <div className="rounded-md border border-dashed p-3">
          <RevisionList entityType={entityType} entityId={entityId} />
        </div>
      )}
    </div>
  )
}

/**
 * Shows what the record read as on each side of the change, when that changed.
 *
 * @param props the revision to summarise
 * @returns the summary element, or nothing when the summary is unchanged
 */
function ChangeSummary({ revision }: { revision: Revision }) {
  const { t } = useTranslation()
  const before = summaryOf(revision.entityType, revision.beforeData, t)
  const after = summaryOf(revision.entityType, revision.afterData, t)
  if (before === after) {
    return null
  }
  return (
    <p className="text-muted-foreground text-xs">
      {before ?? t('audit.nothing')} → {after ?? t('audit.nothing')}
    </p>
  )
}

/**
 * Reads a one-line summary out of a stored payload: a person's name, an event's type and date, a union's state.
 *
 * @param entityType which kind of record the payload is
 * @param payload the JSON the trail recorded, or null
 * @param t the translation function
 * @returns the summary, or null when absent or unreadable
 */
function summaryOf(entityType: AuditEntityType, payload: string | null, t: TFunction): string | null {
  const record = parse(payload)
  if (!record) {
    return null
  }
  switch (entityType) {
    case 'PERSON':
      return text(record.displayName)
    case 'EVENT': {
      const date = record.date as Record<string, unknown> | null | undefined
      const type = text(record.type)
      return [type && t(`event.types.${type}`), text(date?.display)].filter(Boolean).join(' ') || null
    }
    case 'FAMILY': {
      const status = text(record.status)
      const children = Array.isArray(record.children) ? record.children.length : 0
      return status ? t('audit.unionSummary', { status: t(`family.statuses.${status}`), count: children }) : null
    }
    case 'BRANCH':
      return text(record.name)
    // A place's path, not its name: a rename or a move is exactly what the path shows changing (§8.8 D1).
    case 'PLACE':
      return text(record.path) ?? text(record.name)
    case 'SOURCE':
      return text(record.title)
    case 'CITATION':
      return [text(record.sourceTitle), text(record.locator)].filter(Boolean).join(' — ') || null
    case 'GRAVE': {
      const kind = text(record.kind)
      return [kind && t(`grave.kinds.${kind}`), text(record.plot)].filter(Boolean).join(': ') || null
    }
    // Kind and caption: making a portrait, or rewording what is written under a photo, is what an edit changes.
    case 'MEDIA': {
      const kind = text(record.kind)
      return [kind && t(`media.kinds.${kind}`), text(record.caption) ?? text(record.filename)]
        .filter(Boolean)
        .join(': ') || null
    }
    case 'SUGGESTION': {
      const status = text(record.status)
      const kind = text(record.kind)
      return [kind && t(`suggestion.kinds.${kind}`), status && t(`suggestion.statuses.${status}`)]
        .filter(Boolean)
        .join(' · ') || null
    }
    // Role and status, which are what an account edit changes; a password change shows as the note alone.
    case 'MEMBER': {
      const role = text(record.role)
      const status = record.active === false ? t('member.inactive') : t('member.active')
      return [text(record.fullName), role && t(`role.${role}`), status].filter(Boolean).join(' · ') || null
    }
    default:
      return null
  }
}

/**
 * Parses a stored payload into an object, without assuming anything about its shape.
 *
 * @param payload the JSON the trail recorded, or null
 * @returns the object, or null when absent, unreadable or not an object
 */
function parse(payload: string | null): Record<string, unknown> | null {
  if (!payload) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(payload)
    // The payload is whatever the service recorded, and an error marker is an object too (§3.8).
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : null
  } catch {
    return null
  }
}

/**
 * Narrows an unknown payload value to a non-empty string.
 *
 * @param value the value read from a payload
 * @returns the string, or null when it is not one
 */
function text(value: unknown): string | null {
  return typeof value === 'string' && value ? value : null
}
