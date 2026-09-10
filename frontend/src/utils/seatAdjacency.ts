import type { ShowtimeSeatStatusResponse } from '@/types/showtime.types'

export interface SeatAdjacencyResult {
  isValid: boolean
  violatingRows: string[]
  message?: string
}

/**
 * Validates seat adjacency to prevent leaving a single orphan seat (No Single Orphan Seat Rule).
 * 
 * Rules:
 * 1. Evaluated row-by-row for rows that contain newly selected seats.
 * 2. Within each row, seats are partitioned into contiguous physical blocks (where seat[i+1].seatNumber == seat[i].seatNumber + seat[i].capacity, and seat is not broken/blocked).
 * 3. In each block, available runs of seats are identified before and after the selection.
 *    - An available run is a maximal sequence of consecutive available seats.
 *    - An orphan run is a run whose total capacity equals exactly 1 (e.g. 1 standard seat alone).
 *    - Couple seats (capacity = 2) have capacity 2, so an empty couple seat never counts as an orphan seat.
 * 4. If orphanCountAfter > orphanCountBefore, the user created at least one new orphan seat in this row -> INVALID.
 */
export function validateSeatAdjacency(
  allSeats: ShowtimeSeatStatusResponse[],
  selectedSeatIds: string[]
): SeatAdjacencyResult {
  if (!selectedSeatIds || selectedSeatIds.length === 0) {
    return { isValid: true, violatingRows: [] }
  }

  const selectedSet = new Set(selectedSeatIds)
  const selectedSeats = allSeats.filter((s) => selectedSet.has(s.id))
  const affectedRowLabels = Array.from(new Set(selectedSeats.map((s) => s.rowLabel))).filter(Boolean)

  const violatingRows: string[] = []

  // Group all seats by row
  const rowSeatsMap = new Map<string, ShowtimeSeatStatusResponse[]>()
  for (const s of allSeats) {
    if (!s.rowLabel) continue
    if (!rowSeatsMap.has(s.rowLabel)) {
      rowSeatsMap.set(s.rowLabel, [])
    }
    rowSeatsMap.get(s.rowLabel)!.push(s)
  }

  for (const rowLabel of affectedRowLabels) {
    const seatsInRow = [...(rowSeatsMap.get(rowLabel) || [])]
    // Sort seats in row by seatNumber ascending
    seatsInRow.sort((a, b) => a.seatNumber - b.seatNumber)

    // Partition seats into contiguous physical blocks:
    // A gap/aisle (gap between seat numbers) or a broken/blocked seat breaks physical adjacency.
    const blocks: ShowtimeSeatStatusResponse[][] = []
    let currentBlock: ShowtimeSeatStatusResponse[] = []

    for (const seat of seatsInRow) {
      const isBroken = seat.availabilityStatus === 'BLOCKED'
      if (isBroken) {
        if (currentBlock.length > 0) {
          blocks.push(currentBlock)
          currentBlock = []
        }
        continue
      }

      if (currentBlock.length === 0) {
        currentBlock.push(seat)
      } else {
        const prevSeat = currentBlock[currentBlock.length - 1]
        const prevCap = prevSeat.capacity && prevSeat.capacity > 0 ? prevSeat.capacity : 1
        if (prevSeat.seatNumber + prevCap === seat.seatNumber) {
          // Contiguous
          currentBlock.push(seat)
        } else {
          // Aisle or gap in between
          blocks.push(currentBlock)
          currentBlock = [seat]
        }
      }
    }
    if (currentBlock.length > 0) {
      blocks.push(currentBlock)
    }

    // Calculate orphan count before and after selection for this row
    let orphanBefore = 0
    let orphanAfter = 0

    for (const block of blocks) {
      orphanBefore += countOrphanRuns(block, (s) => s.availabilityStatus === 'AVAILABLE')
      orphanAfter += countOrphanRuns(
        block,
        (s) => s.availabilityStatus === 'AVAILABLE' && !selectedSet.has(s.id)
      )
    }

    if (orphanAfter > orphanBefore) {
      violatingRows.push(rowLabel)
    }
  }

  return {
    isValid: violatingRows.length === 0,
    violatingRows,
  }
}

function countOrphanRuns(
  block: ShowtimeSeatStatusResponse[],
  isAvailableFn: (seat: ShowtimeSeatStatusResponse) => boolean
): number {
  let orphanCount = 0
  let currentRunCapacity = 0

  for (const seat of block) {
    const cap = seat.capacity && seat.capacity > 0 ? seat.capacity : 1
    if (isAvailableFn(seat)) {
      currentRunCapacity += cap
    } else {
      if (currentRunCapacity === 1) {
        orphanCount++
      }
      currentRunCapacity = 0
    }
  }

  if (currentRunCapacity === 1) {
    orphanCount++
  }

  return orphanCount
}

