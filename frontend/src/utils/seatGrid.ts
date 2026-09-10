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
 * Xây dựng ma trận lưới 2D (Seat Grid) từ danh sách ghế 1D trả về từ Backend.
 * 
 * Các nguyên tắc & Thuật toán hiển thị:
 * 1. Tọa độ logic (seatNumber): Đại diện cho vị trí cột bắt đầu (1-indexed) trên lưới hiển thị.
 * 2. Độ rộng ô (Seat Span) phụ thuộc vào sức chứa (seat.capacity):
 *    - Ghế thường / VIP (capacity = 1): Chiếm span = 1 cột.
 *    - Ghế đôi Couple (capacity = 2): Chiếm span = 2 cột (span = 2).
 * 3. Chèn khoảng trống thông minh (Empty Spacers):
 *    - Các cột không có ghế (ví dụ: lối đi giữa rạp hoặc ghế bị xóa do ghế đôi chiếm chỗ)
 *      được tự động lấp đầy bằng các ô GridCellType = 'empty' để giữ nguyên độ căn chỉnh thẳng hàng.
 * 4. Chống vỡ giao diện (Collision Clamping):
 *    - Nếu ghế đôi có nguy cơ tràn quá số cột tối đa hoặc đè lên một ghế khác đã tồn tại,
 *      thuật toán tự động thu hẹp span về 1 để bảo toàn bố cục lưới.
 * 5. Bất biến dữ liệu: Tuyệt đối không thay đổi mã ghế hay ID gửi lên payload đặt vé.
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

  // 1. Gom nhóm danh sách ghế theo nhãn hàng (rowLabel)
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

  // 2. Xác định tổng số cột (columnsCount): ưu tiên cấu hình phòng chiếu, nếu không suy ra từ maxSeatNumber
  let columnsCount = configuredColumnsCount && configuredColumnsCount > 0
    ? configuredColumnsCount
    : Math.max(maxSeatNumber, 1)

  // Đảm bảo số cột tối thiểu phải đủ lớn để chứa số ghế lớn nhất
  if (maxSeatNumber > columnsCount) {
    columnsCount = maxSeatNumber
  }

  const columnNumbers = Array.from({ length: columnsCount }, (_, i) => i + 1)

  // 3. Sắp xếp thứ tự các hàng theo bảng chữ cái (A, B, C... Z)
  const sortedRowLabels = Array.from(rowMap.keys()).sort((a, b) =>
    a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' })
  )

  let hasOverlapWarning = false

  // 4. Xây dựng các ô lưới (grid cells) cho từng hàng
  const rows: GridRow<T>[] = sortedRowLabels.map((rowLabel) => {
    const seatsInRow = rowMap.get(rowLabel)!
    // Ánh xạ số thứ tự ghế (cột) sang thực thể ghế: seatNumber -> seat
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
          // Kiểm tra hợp lệ: ghế mở rộng không được vượt quá số cột tối đa của hàng
          if (col + span - 1 <= columnsCount) {
            // Kiểm tra xung đột: các cột tiếp theo trong phạm vi span có bị ghế độc lập khác chiếm chỗ không
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
        // Vị trí lối đi hoặc khoảng trống logic (aisle / empty cell)
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

