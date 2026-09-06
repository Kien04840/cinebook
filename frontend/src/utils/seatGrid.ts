export interface BaseSeatItem {
  id: string
  rowLabel: string
  seatNumber: number
  seatTypeName?: string
  seatTypeCode?: string
  capacity?: number
  colorToken?: string
  icon?: string
  seatCode?: string
}

export type GridCellType = 'seat' | 'empty'

export interface GridCell<T extends BaseSeatItem = BaseSeatItem> {
  type: GridCellType
  column: number // 1-indexed logical column position
  span: number   // driven by seat.capacity (1 for normal/empty, 2 for couple, etc.)
  seat?: T       // Defined if type === 'seat'
}

export interface GridRow<T extends BaseSeatItem = BaseSeatItem> {
  rowLabel: string
  cells: GridCell<T>[]
}

export interface SeatGridResult<T extends BaseSeatItem = BaseSeatItem> {
  columnsCount: number
  columnNumbers: number[]
  rows: GridRow<T>[]
  hasOverlapWarning: boolean
}

/**
 * Builds a logical 2D seat grid representation from an array of seats and an optional columnsCount.
 * 
 * Invariants:
 * 1. seatNumber represents the 1-indexed starting logical column.
 * 2. Seat span is driven by domain capacity: normal (capacity=1) has span=1, couple (capacity=2) has span=2.
 * 3. Multi-capacity seats occupy span = capacity if space permits (col + span - 1 <= columnsCount and tracks are free).
 * 4. Missing seat numbers are preserved as non-interactive empty positions (spacers).
 * 5. Neither seat IDs, booking payloads, nor seat numbers are mutated.
 */
export function buildSeatGrid<T extends BaseSeatItem>(
  seats: T[],
  configuredColumnsCount?: number
): SeatGridResult<T> {
  if (!seats || seats.length === 0) {
    const colCount = Math.max(configuredColumnsCount || 0, 1)
    return {
      columnsCount: colCount,
      columnNumbers: Array.from({ length: colCount }, (_, i) => i + 1),
      rows: [],
      hasOverlapWarning: false,
    }
  }

  // 1. Group seats by rowLabel
  const rowMap = new Map<string, T[]>()
  let maxSeatNumber = 0

  for (const seat of seats) {
    const row = seat.rowLabel || '?'
    if (!rowMap.has(row)) {
      rowMap.set(row, [])
    }
    rowMap.get(row)!.push(seat)
    if (seat.seatNumber && seat.seatNumber > maxSeatNumber) {
      maxSeatNumber = seat.seatNumber
    }
  }

  // 2. Determine columnsCount: use configured if available, else derive from maxSeatNumber
  let columnsCount = configuredColumnsCount && configuredColumnsCount > 0
    ? configuredColumnsCount
    : Math.max(maxSeatNumber, 1)

  // Ensure columnsCount is at least as large as any seat's starting column
  if (maxSeatNumber > columnsCount) {
    columnsCount = maxSeatNumber
  }

  const columnNumbers = Array.from({ length: columnsCount }, (_, i) => i + 1)

  // 3. Sort row labels alphabetically (A, B, C... Z)
  const sortedRowLabels = Array.from(rowMap.keys()).sort((a, b) =>
    a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' })
  )

  let hasOverlapWarning = false

  // 4. Build grid cells for each row
  const rows: GridRow<T>[] = sortedRowLabels.map((rowLabel) => {
    const seatsInRow = rowMap.get(rowLabel)!
    // Map seatNumber -> seat
    const seatByCol = new Map<number, T>()
    for (const seat of seatsInRow) {
      seatByCol.set(seat.seatNumber, seat)
    }

    const cells: GridCell<T>[] = []
    let col = 1

    while (col <= columnsCount) {
      const seat = seatByCol.get(col)

      if (seat) {
        const capacity = seat.capacity && seat.capacity > 0 ? seat.capacity : 1
        let span = capacity

        if (span > 1) {
          // Validate: seatNumber + span - 1 <= columnsCount
          if (col + span - 1 <= columnsCount) {
            // Check if any subsequent column in the span already has an independent seat
            let hasCollision = false
            for (let offset = 1; offset < span; offset++) {
              if (seatByCol.has(col + offset)) {
                hasCollision = true
                break
              }
            }

            if (hasCollision) {
              hasOverlapWarning = true
              console.warn(
                `[SeatGrid] Data overlap in row ${rowLabel}: Multi-capacity seat (capacity=${capacity}) at col ${col} collides with another seat record. Clamping span to 1 to prevent visual collision.`
              )
              span = 1
            }
          } else {
            console.warn(
              `[SeatGrid] Seat in row ${rowLabel} at col ${col} with span ${span} exceeds columnsCount ${columnsCount}. Clamping span to 1.`
            )
            span = 1
          }
        }

        cells.push({
          type: 'seat',
          column: col,
          span,
          seat,
        })

        col += span
      } else {
        // Empty logical position
        cells.push({
          type: 'empty',
          column: col,
          span: 1,
        })
        col += 1
      }
    }

    return {
      rowLabel,
      cells,
    }
  })

  return {
    columnsCount,
    columnNumbers,
    rows,
    hasOverlapWarning,
  }
}

