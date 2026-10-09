import { PLACE_TYPES, type PlaceType } from '@/features/place/types'

/**
 * Reports whether a place of one level may sit inside a place of another, as the server's PlaceType.fitsInside does.
 *
 * @param child the level of the place being placed
 * @param parent the level of the proposed parent
 * @returns true when the parent is wider, or when either side is OTHER
 */
export function fitsInside(child: PlaceType, parent: PlaceType): boolean {
  // OTHER has no fixed rank: a tổng sat between xã and huyện, a phủ between huyện and trấn.
  return child === 'OTHER' || parent === 'OTHER' || PLACE_TYPES.indexOf(parent) > PLACE_TYPES.indexOf(child)
}
