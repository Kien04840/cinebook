# Chương 1. Khảo sát hệ thống

## 1.1 Giới thiệu về chung về hệ thống CineBook

Trong bối cảnh công nghệ thông tin và thương mại điện tử phát triển mạnh mẽ, chuyển đổi số trong lĩnh vực giải trí và dịch vụ điện ảnh đã trở thành xu thế tất yếu. Các cụm rạp chiếu phim hiện đại đòi hỏi một giải pháp quản lý và kinh doanh trực tuyến toàn diện nhằm nâng cao trải nghiệm của khán giả, đồng thời tối ưu hóa năng lực vận hành của rạp. Hệ thống đặt vé truyền thống qua quầy vé bộc lộ nhiều hạn chế lớn như tình trạng khán giả phải xếp hàng chờ đợi trong giờ cao điểm, nguy cơ hết vé hoặc không chọn được chỗ ngồi ưng ý, cũng như sự thiếu linh hoạt trong công tác điều phối lịch chiếu và quản lý doanh thu của ban điều hành rạp.

CineBook được nghiên cứu và xây dựng nhằm giải quyết triệt để các vấn đề trên. Đây là hệ thống website thương mại điện tử đặt vé xem phim trực tuyến hiện đại, được thiết kế theo mô hình nguyên khối phân lớp (Layered Monolith Architecture) với độ ổn định cao. Hệ thống cung cấp hai phân hệ cốt lõi: phân hệ công khai dành cho khách hàng với trải nghiệm tra cứu phim, lịch chiếu, chọn ghế trực quan và thanh toán trực tuyến tiện lợi; cùng phân hệ quản trị tập trung dành cho ban quản lý để giám sát toàn bộ hoạt động của rạp, từ quản trị danh mục phim, quản lý cụm rạp, phòng chiếu, cấu hình bảng giá vé động, lập lịch chiếu tự động thông minh cho đến đối soát đơn hàng và phân tích báo cáo thống kê doanh thu theo thời gian thực.

Về nền tảng công nghệ, CineBook được phát triển trên hệ sinh thái Java 21 kết hợp với Spring Boot 3 ở phía máy chủ, sử dụng hệ quản trị cơ sở dữ liệu quan hệ MySQL 8 làm nguồn lưu trữ sự thật duy nhất (Single Source of Truth). Giao diện người dùng được xây dựng trên nền tảng Vue 3 (Composition API), Vite, TypeScript và Tailwind CSS, mang lại tốc độ phản hồi nhanh chóng và trải nghiệm mượt mà trên nhiều kích thước màn hình. Hệ thống tích hợp trực tiếp với cổng thanh toán điện tử VNPay Sandbox phục vụ các giao dịch không dùng tiền mặt an toàn và cổng dữ liệu điện ảnh quốc tế TMDB (The Movie Database) API nhằm tự động hóa quy trình nhập liệu và đồng bộ hóa thông tin phim ảnh.

## 1.2 Khảo sát nghiệp vụ

### 1.2.1 Hoạt động quản lý tài khoản người dùng

Hoạt động quản lý tài khoản người dùng trong hệ thống CineBook được thiết kế nhằm bảo đảm tính bảo mật, tính toàn vẹn dữ liệu và phân định ranh giới trách nhiệm rõ ràng giữa các nhóm đối tượng sử dụng:

1. **Đăng ký và xác thực tài khoản**: Khách hàng có thể đăng ký tài khoản thành viên mới bằng cách cung cấp các thông tin cơ bản gồm họ tên, số điện thoại và địa chỉ thư điện tử (email). Địa chỉ email được sử dụng làm định danh duy nhất trong toàn hệ thống. Mật khẩu của người dùng được mã hóa bảo mật một chiều bằng thuật toán băm BCrypt với độ phức tạp cao trước khi lưu trữ vào cơ sở dữ liệu, bảo đảm an toàn thông tin ngay cả trong trường hợp dữ liệu bị rò rỉ.
2. **Cơ chế quản lý phiên làm việc không trạng thái (Stateless Session)**: Hệ thống sử dụng cặp mã thông báo JSON Web Token (JWT) để xác thực người dùng sau khi đăng nhập thành công. Cặp mã này bao gồm Mã truy cập (Access Token) có thời hạn hiệu lực ngắn (15 phút) nhằm giảm thiểu rủi ro khi bị đánh cắp, và Mã làm mới (Refresh Token) có thời hạn dài hơn (7 ngày) được lưu trữ an toàn trong cơ sở dữ liệu. Khi Access Token hết hạn, ứng dụng client có thể tự động sử dụng Refresh Token để xin cấp Access Token mới mà không làm gián đoạn trải nghiệm của người dùng. Hệ thống đồng thời hỗ trợ cơ chế thu hồi và xoay vòng Refresh Token khi người dùng chủ động đăng xuất hoặc khi phát hiện dấu hiệu bất thường.
3. **Phân quyền truy cập theo vai trò (Role-Based Access Control - RBAC)**: CineBook phân chia quyền hạn chặt chẽ thành hai vai trò chính:
   - *Khách hàng (`CUSTOMER`)*: Có quyền tra cứu thông tin phim, lịch chiếu, thực hiện đặt vé, thanh toán trực tuyến, xem lịch sử đơn hàng cá nhân và cập nhật thông tin hồ sơ của chính mình.
   - *Quản trị viên (`ADMIN`)*: Nắm toàn quyền quản trị hệ thống, bao gồm quản lý người dùng, duyệt danh mục phim, quản lý cụm rạp, phòng chiếu, thiết lập bảng giá vé, lập lịch chiếu, kiểm soát đơn đặt vé và theo dõi báo cáo doanh thu. Mọi yêu cầu truy cập vào các tuyến đường quản trị đều được kiểm tra phân quyền nghiêm ngặt ở tầng bảo mật máy chủ.
4. **Quản lý trạng thái và khôi phục tài khoản**: Quản trị viên có thẩm quyền khóa (`BLOCKED`) hoặc kích hoạt lại (`ACTIVE`) tài khoản người dùng khi phát hiện các vi phạm quy định sử dụng. Các tài khoản ở trạng thái bị khóa sẽ lập tức bị tước quyền đăng nhập và không thể thực hiện giao dịch đặt vé. Đối với người dùng quên mật khẩu, hệ thống cung cấp quy trình khôi phục an toàn thông qua mã xác thực một lần (OTP) hoặc liên kết xác thực có thời hạn gửi trực tiếp đến email đã đăng ký.

### 1.2.2 Hoạt động tra cứu phim và lịch chiếu

Phân hệ tra cứu được xây dựng nhằm cung cấp cho khán giả phương tiện tìm kiếm thông tin điện ảnh nhanh chóng, trực quan và chính xác:

1. **Tra cứu danh mục phim**:
   - Phim trong hệ thống được phân loại theo các trạng thái phát hành chuẩn mực: Đang chiếu (`NOW_SHOWING`), Sắp chiếu (`COMING_SOON`), Ngừng chiếu (`ENDED`) và Đã ẩn (`HIDDEN`). Trên giao diện công khai dành cho khán giả, hệ thống chỉ hiển thị các bộ phim ở trạng thái `NOW_SHOWING` và `COMING_SOON`.
   - Khách hàng có thể tìm kiếm phim theo tên, lọc theo thể loại (hành động, hoạt hình, tâm lý, kinh dị...), quốc gia sản xuất, độ tuổi quy định (P, C13, C16, C18) và định dạng chiếu (2D, 3D, IMAX).
   - Mỗi bộ phim có trang thông tin chi tiết hiển thị đầy đủ các dữ liệu điện ảnh phong phú: áp phích (poster), ảnh nền (backdrop), video trailer chính thức, thời lượng chiếu, bản tóm tắt nội dung, danh sách đạo diễn, diễn viên chính và ngày khởi chiếu toàn quốc.
2. **Tra cứu lịch chiếu linh hoạt**:
   - Khán giả có thể tra cứu lịch chiếu theo hai hướng tiếp cận thuận tiện: theo cụm rạp mong muốn (xem danh sách các phim và suất chiếu đang có tại rạp đó trong ngày) hoặc theo bộ phim đã chọn (xem toàn bộ các cụm rạp và khung giờ có chiếu bộ phim đó).
   - Lịch chiếu hỗ trợ lọc theo ngày xem (trong khoảng từ ngày hiện tại đến các ngày tiếp theo trong tuần) và theo định dạng phòng chiếu (2D lồng tiếng, 2D phụ đề, 3D, IMAX...).
3. **Ràng buộc nghiệp vụ hiển thị lịch chiếu**:
   - Nhằm bảo đảm tính chính xác và tránh gây hiểu lầm cho người xem, hệ thống chỉ hiển thị lên giao diện đặt vé các suất chiếu ở trạng thái sẵn sàng phục vụ (`SCHEDULED`) và có thời điểm bắt đầu lớn hơn thời gian thực tại của hệ thống (`startTime > now`).
   - Các suất chiếu đã diễn ra, đã kết thúc (`FINISHED`) hoặc đã bị quản trị viên hủy bỏ (`CANCELLED`) sẽ tự động được ẩn khỏi danh sách công khai, ngăn chặn triệt để tình trạng khách hàng đặt nhầm vào các suất chiếu trong quá khứ.

### 1.2.3 Hoạt động đặt vé xem phim

Đặt vé xem phim là hoạt động nghiệp vụ phức tạp nhất và có tính chất quyết định trong hệ thống thương mại điện tử CineBook. Bài toán cốt lõi đặt ra là làm thế nào để nhiều khách hàng có thể cùng lúc truy cập vào một suất chiếu, theo dõi sơ đồ ghế theo thời gian thực và đặt chỗ mà không xảy ra tình trạng trùng lặp ghế, đồng thời bảo đảm công bằng cho người mua và duy trì doanh thu tối đa cho cụm rạp.

#### 1. Phân tích và lựa chọn phương án giữ ghế

Trong quá trình thiết kế hệ thống, ba phương án xử lý tương tranh ghế ngồi đã được khảo sát và đánh giá kỹ lưỡng:

- **Phương án 1 — Kiểm tra ghế tại thời điểm thanh toán (Check-on-Payment)**:
  Khách hàng được tự do chọn ghế trên giao diện và hệ thống chỉ tiến hành kiểm tra xung đột ghế khi khách hàng nhấn nút gửi yêu cầu thanh toán cuối cùng.
  *Ưu điểm*: Đơn giản trong thiết kế cơ sở dữ liệu, không phát sinh chi phí quản lý trạng thái tạm thời của ghế.
  *Nhược điểm*: Trong các khung giờ cao điểm hoặc với các bộ phim bom tấn có lượng truy cập đồng thời lớn, xác suất hai hoặc nhiều người cùng chọn một ghế và cùng đi đến bước thanh toán là rất cao. Khi đó, những người thanh toán sau sẽ bị hệ thống từ chối do ghế đã bị người trước mua mất. Trải nghiệm này gây ức chế tột cùng cho khách hàng sau khi họ đã mất nhiều công sức lựa chọn chỗ ngồi và chuẩn bị tiền thanh toán, dẫn đến tỷ lệ khách hàng rời bỏ dịch vụ rất lớn.
- **Phương án 2 — Khóa ghế tức thời ngay khi nhấp chọn (Instant Locking on Click)**:
  Ngay khi khách hàng nhấp chuột vào một chiếc ghế trên sơ đồ, hệ thống lập tức khóa cứng ghế đó lại.
  *Ưu điểm*: Khách hàng có cảm giác được bảo vệ ghế tức thì trong quá trình thao tác.
  *Nhược điểm*: Dẫn đến vấn nạn nghiêm trọng về "ghế ma" (phantom seats). Người dùng có thể chỉ nhấp chọn thử nhiều ghế rồi thoát khỏi ứng dụng mà không tiếp tục quy trình mua vé. Những chiếc ghế bị khóa tạm thời này sẽ ngăn cản các khách hàng có nhu cầu mua thực sự, làm giảm tỷ lệ lấp đầy phòng chiếu và gây thất thoát doanh thu đáng kể cho rạp chiếu phim.
- **Phương án 3 — Tạm giữ ghế có giới hạn thời gian (Time-bounded Seat Hold)**:
  Sau khi khách hàng lựa chọn xong các vị trí ghế ngồi mong muốn (giới hạn tối đa 8 ghế cho một giao dịch nhằm chống đầu cơ vé) và xác nhận đặt vé, hệ thống sẽ khởi tạo một đơn đặt hàng ở trạng thái chờ thanh toán (`PENDING_PAYMENT`) và kích hoạt cơ chế tạm giữ danh sách ghế này trong một khoảng thời gian hữu hạn được hiển thị rõ ràng (5 phút). Trong suốt 5 phút này, các ghế đang giữ hoàn toàn bị khóa với tất cả những người dùng khác. Nếu khách hàng hoàn tất thanh toán trong thời gian quy định, ghế sẽ được chuyển giao thành vé chính thức; nếu quá 5 phút mà chưa thanh toán thành công, ghế tự động được giải phóng để trả về trạng thái trống cho các khách hàng khác tiếp tục đặt.

**Kết luận lựa chọn**: Hệ thống CineBook quyết định áp dụng **Phương án 3 (Tạm giữ ghế có giới hạn thời gian)**. Đây là phương án tiêu chuẩn được áp dụng tại các hệ thống bán vé chuyên nghiệp trên thế giới, đạt được sự cân bằng tối ưu giữa việc bảo vệ quyền lợi chọn chỗ của khách hàng và tối đa hóa hiệu suất kinh doanh phòng vé của doanh nghiệp.

#### 2. Mô hình biểu diễn ghế và kiểm soát đồng thời

Để hiện thực hóa phương án trên một cách an toàn và nhất quán, hệ thống xây dựng mô hình dữ liệu và cơ chế kiểm soát đồng thời đặc thù:

- **Suy diễn trạng thái ghế động theo suất chiếu**: Trạng thái ghế ngồi không được lưu tĩnh trong bảng `seats`. Bảng `seats` chỉ đại diện cho thuộc tính vật lý của ghế trong phòng chiếu (hàng ghế, số thứ tự, loại ghế và trạng thái phần cứng `ACTIVE` hoặc `BROKEN`). Tính khả dụng của một ghế trong một suất chiếu cụ thể được hệ thống suy diễn động từ hai nguồn dữ liệu thời gian thực:
  1. Các bản ghi tạm giữ ghế (`seat_holds`) còn hiệu lực (`expires_at > now`).
  2. Các bản ghi vé (`tickets`) đã được phát hành thành công thuộc về suất chiếu đó với trạng thái `VALID` hoặc `USED`.
- **Kiểm soát tương tranh cao độ (Concurrency Control)**: Để loại trừ hoàn toàn hiện tượng tranh chấp điều kiện (race condition) khi nhiều khách hàng cùng cố gắng giữ một ghế tại cùng một phần nghìn giây, hệ thống kết hợp hai tầng bảo vệ nghiêm ngặt:
  - *Ở tầng ứng dụng*: Sử dụng cơ chế Khóa bi quan (Pessimistic Write Lock) khi kiểm tra và khởi tạo đơn đặt chỗ, bảo đảm chỉ một tiến trình được phép xử lý giao dịch cho tập ghế đó tại một thời điểm.
  - *Ở tầng cơ sở dữ liệu*: Ràng buộc duy nhất kết hợp giữa suất chiếu và ghế ngồi (`uk_seat_holds_showtime_seat`) trong bảng `seat_holds` đóng vai trò là chốt chặn cuối cùng, từ chối mọi nỗ lực ghi đè dữ liệu trùng lặp.
- **Cơ chế giải phóng ghế quá hạn đa tầng**: Để tránh việc ghế bị treo khi người dùng không thanh toán, hệ thống triển khai hai cơ chế phối hợp:
  - *Tác vụ nền định kỳ (`BookingCleanupTask`)*: Định kỳ tự động quét và thu hồi các đơn hàng `PENDING_PAYMENT` có thời gian giữ ghế đã quá hạn.
  - *Cơ chế kiểm tra tức thời (Lazy Expiration on Access)*: Bất cứ khi nào khách hàng hoặc quản trị viên truy vấn chi tiết một đơn đặt vé hoặc mở lại suất chiếu, hệ thống sẽ chủ động kiểm tra thời điểm hết hạn của đơn; nếu phát hiện đã quá hạn, hệ thống lập tức thực thi việc giải phóng ghế và cập nhật trạng thái đơn hàng mà không cần chờ đợi chu kỳ quét tiếp theo của tác vụ nền.

#### 3. Cơ chế định giá vé động (Dynamic Ticket Pricing V1)

CineBook triển khai cơ chế định giá vé động linh hoạt, bảo đảm phản ánh đúng quy luật cung cầu của thị trường điện ảnh. Nhằm ngăn chặn các hành vi gian lận sửa đổi giá từ phía client, toàn bộ logic tính toán giá vé được đặt tập trung duy nhất tại tầng máy chủ (`PricingService`) theo công thức chuẩn:

$$\text{Giá vé một ghế} = \max\left(0, (\text{Giá gốc suất chiếu} \times \text{Sức chứa ghế}) + \text{Phụ thu loại ghế} + \text{Phụ thu ngày trong tuần} + \text{Phụ thu khung giờ}\right)$$

Trong đó:
- **Giá gốc suất chiếu (`showtimes.base_price`)**: Mức giá sàn áp dụng cho suất chiếu đó, do rạp thiết lập dựa trên độ "nóng" của phim hoặc định dạng phòng chiếu. Giá gốc được nhân với sức chứa của ghế (`capacity`, ví dụ Standard = 1, VIP = 1, Couple = 2).
- **Phụ thu loại ghế (`seat_types.price_modifier`)**: Giá trị phụ thu theo tiện ích của từng loại ghế. Ghế Tiêu chuẩn (Standard) có phụ thu bằng 0đ; ghế VIP ở khu vực trung tâm có mức phụ thu +20.000đ; ghế Đôi (Couple / Sweetbox) có mức phụ thu +40.000đ. Các khoản phụ thu loại ghế, phụ thu ngày và phụ thu khung giờ được cộng thêm cố định cho mỗi đơn vị ghế mà không nhân theo sức chứa.
- **Phụ thu theo ngày trong tuần (`day_pricing_rules`)**: Mức điều chỉnh giá theo từng thứ trong tuần (từ Thứ Hai đến Chủ Nhật). Cho phép rạp tăng giá vé vào các ngày cuối tuần (Thứ Bảy, Chủ Nhật) khi nhu cầu xem phim tăng vọt, hoặc giảm giá vé vào các ngày giữa tuần (Thứ Ba, Thứ Tư) để kích cầu người xem.
- **Phụ thu theo khung giờ chiếu (`time_slot_pricing_rules`)**: Hệ thống hỗ trợ cấu hình các khoảng thời gian $[startTime, endTime)$ trong ngày để điều chỉnh giá, bao gồm các suất chiếu sớm (Early Bird trước 12:00) với mức giá ưu đãi, khung giờ vàng (Prime Time từ 18:00 đến 22:00) với mức phụ thu cao điểm, và các suất chiếu khuya (Late Night sau 22:00).

Sau khi tổng hợp giá vé của toàn bộ các ghế đã chọn, đơn hàng có thể áp dụng mã khuyến mãi (Promotion):
$$\text{Tổng tiền thanh toán} = \max\left(0, \sum \text{Giá vé từng ghế} - \text{Số tiền giảm giá}\right)$$

**Bất biến đóng băng giá vé lịch sử (Historical Price Snapshot)**:
Một nguyên tắc kế toán tối quan trọng được hệ thống thực thi triệt để là tính bất biến của giá vé lịch sử. Khi đơn hàng được tạo và thanh toán thành công, giá vé của từng chiếc ghế được sao lưu vĩnh viễn vào thuộc tính `tickets.ticket_price` và tổng số tiền thanh toán được ghi nhận vào `bookings.total_amount`. Bất kỳ sự thay đổi nào về bảng giá vé, phụ thu ngày hay phụ thu giờ của quản trị viên trong tương lai đều chỉ có hiệu lực với các giao dịch mới, tuyệt đối không làm thay đổi hay tính toán lại giá trị của các vé và đơn hàng đã phát hành trong quá khứ. Các quy trình thanh toán và hoàn tiền luôn căn cứ vào số tiền đã lưu trữ để bảo đảm tính chính xác của sổ sách kế toán.

### 1.2.4 Hoạt động thanh toán

Thanh toán là khâu chuyển hóa từ trạng thái giữ chỗ tạm thời sang giao dịch mua vé chính thức. CineBook tích hợp với cổng thanh toán trực tuyến quốc gia VNPay (môi trường Sandbox) nhằm mang lại trải nghiệm thanh toán an toàn, không dùng tiền mặt cho người dùng:

1. **Khởi tạo và điều hướng thanh toán**:
   - Khi đơn đặt vé được tạo thành công ở trạng thái `PENDING_PAYMENT`, khách hàng được điều hướng sang cổng thanh toán VNPay kèm theo chuỗi tham số giao dịch đã được ký số bằng mã băm bí mật (HMAC-SHA512).
   - Khách hàng có thể lựa chọn đa dạng các phương thức: quét mã QR VNPAY-QR qua ứng dụng ngân hàng di động, thanh toán bằng thẻ ghi nợ/thẻ tín dụng nội địa hoặc thẻ thanh toán quốc tế (Visa, MasterCard).
2. **Cơ chế thử lại thanh toán (Retry Payment)**:
   - Trong trường hợp giao dịch bị gián đoạn do sự cố đường truyền mạng hoặc khách hàng chủ động hủy thanh toán tại trang của cổng thanh toán, khách hàng được phép quay trở lại hệ thống và nhấn nút thử lại thanh toán, miễn là thời hạn 5 phút giữ ghế của đơn hàng vẫn còn hiệu lực.
   - Để ngăn ngừa xung đột dữ liệu, hệ thống áp dụng quy tắc đơn phiên: Tại mỗi thời điểm, một đơn đặt vé chỉ được phép có duy nhất một bản ghi giao dịch thanh toán ở trạng thái chờ (`PENDING`). Mọi yêu cầu thử lại mới sẽ tự động hủy bỏ phiên thanh toán chờ trước đó.
3. **Xác thực giao dịch qua IPN (Instant Payment Notification)**:
   - Nhằm đối phó với tình huống khách hàng đóng trình duyệt ngay sau khi thanh toán tại ngân hàng khiến luồng phản hồi trên giao diện (Frontend Return) bị đứt đoạn, CineBook sử dụng cơ chế thông báo tức thời giữa hai máy chủ (Server-to-Server IPN) làm nguồn sự thật duy nhất để xác nhận kết quả giao dịch.
   - Máy chủ CineBook thực hiện kiểm tra nghiêm ngặt: Xác minh chữ ký số HMAC-SHA512 để bảo đảm thông tin không bị giả mạo trên đường truyền, kiểm tra mã phản hồi thành công (`vnp_ResponseCode = 00`) và đối chiếu số tiền thanh toán thực tế với tổng giá trị đơn hàng trong cơ sở dữ liệu.
4. **Xác nhận vé thành công**:
   - Khi giao dịch được xác thực thành công qua IPN, trạng thái đơn hàng chuyển sang `PAID`.
   - Hệ thống tự động xóa bỏ các bản ghi giữ chỗ trong `seat_holds` và tạo mới các bản ghi vé chính thức trong bảng `tickets` với trạng thái có hiệu lực (`VALID`), đồng thời gửi thư điện tử xác nhận kèm thông tin vé và biên lai điện tử đến hòm thư của khách hàng.
5. **Xử lý ngoại lệ thanh toán đến muộn (Late Payment)**:
   - Trong tình huống hy hữu khi giao dịch ngân hàng của khách hàng bị trễ do nghẽn mạng và thông báo IPN gửi tới máy chủ sau khi thời hạn giữ ghế của đơn đã kết thúc (đơn đã chuyển sang trạng thái `EXPIRED`), hệ thống sẽ không tự tiện phát hành vé (vì ghế có thể đã được người khác đặt). Thay vào đó, hệ thống vẫn ghi nhận bản ghi thanh toán thành công và chuyển đơn sang trạng thái chờ xử lý ngoại lệ, cho phép quản trị viên thực hiện hoàn tiền tự động (`adminRefund`) để hoàn trả tiền về tài khoản ngân hàng của khách hàng một cách minh bạch.

### 1.2.5 Hoạt động quản lý hệ thống

Hoạt động quản lý hệ thống do Quản trị viên (`ADMIN`) vận hành, bao gồm quản lý cơ sở vật chất, danh mục phim, cấu hình bảng giá và đặc biệt là bài toán lập lịch chiếu phim đa phòng:

#### 1. Quản trị cơ sở vật chất và bảng giá
- Quản lý mạng lưới các cụm rạp chiếu phim (tên rạp, địa chỉ, số hotline, giờ mở cửa, giờ đóng cửa).
- Quản lý các phòng chiếu thuộc cụm rạp: Cấu hình kích thước ma trận ghế (số hàng, số cột), định dạng phòng chiếu (2D, 3D, IMAX), trạng thái hoạt động (`ACTIVE` hoặc `MAINTENANCE`).
- Quản lý sơ đồ ghế: Tạo tự động sơ đồ ghế theo ma trận hàng và số thứ tự, gán loại ghế (Thường, VIP, Đôi) cho từng vị trí, đánh dấu các ghế bị hỏng vật lý (`BROKEN`) để ngừng phục vụ.
- Quản lý cấu hình bảng giá vé động: Quản trị mức phụ thu của từng loại ghế, thiết lập bảng giá điều chỉnh theo 7 ngày trong tuần và định nghĩa các khung giờ chiếu linh hoạt trong ngày.

#### 2. Nghiệp vụ lập lịch chiếu phim đa phòng (Multi-Movie Cinema Programming)
Đây là một trong những nghiệp vụ có độ phức tạp cao nhất của hệ thống quản lý rạp chiếu phim, đòi hỏi phải giải quyết bài toán phân bổ tài nguyên thời gian và không gian nhằm tối ưu hóa công suất vận hành:

- **Bản chất của bài toán lập lịch rạp chiếu**:
  Việc tạo lịch chiếu trong thực tế không đơn giản là thao tác gán thủ công từng bộ phim vào từng phòng chiếu. Một cụm rạp thường có nhiều bộ phim cùng lúc đang chiếu với các mức độ thu hút khán giả khác nhau, sở hữu nhiều phòng chiếu có sức chứa và định dạng khác nhau. Quản trị viên cần tạo ra một chương trình chiếu phim hợp lý cho cả ngày nhằm đáp ứng tối đa nhu cầu của các đối tượng khán giả, đồng thời tuân thủ tuyệt đối các ràng buộc vận hành nghiêm ngặt:
  1. *Giờ mở cửa và đóng cửa của cụm rạp (`openingTime`, `closingTime`)*: Tất cả các suất chiếu đều phải bắt đầu sau giờ mở cửa và kết thúc trọn vẹn trước giờ đóng cửa của rạp.
  2. *Thời lượng thực tế của bộ phim (`durationMinutes`)*: Thời lượng chiếu của một suất phim được cố định chính xác theo bản quyền bộ phim (ví dụ 120 phút, 148 phút); thời gian kết thúc của suất chiếu luôn được tính toán tự động bằng công thức: $\text{Thời gian kết thúc} = \text{Thời gian bắt đầu} + \text{Thời lượng phim}$, hoàn toàn không thể nhập tùy tiện.
  3. *Khoảng thời gian dọn dẹp và chuyển tiếp phòng chiếu (`turnaroundTimeMinutes`)*: Sau mỗi suất chiếu, phòng chiếu bắt buộc phải có một khoảng thời gian trống từ 10 đến 20 phút để nhân viên vệ sinh phòng, thu gom rác, xịt khử khuẩn và kiểm tra kỹ thuật máy chiếu trước khi đón lượng khán giả của suất tiếp theo.
  4. *Khoảng thời gian làm tròn thời điểm bắt đầu (Interval Snap)*: Nhằm tạo ra các mốc giờ chiếu quy củ, dễ nhớ cho khách hàng (ví dụ 18:00, 18:15, 18:30 thay vì các mốc giờ lẻ như 18:07 hay 18:23), thời điểm bắt đầu của mọi suất chiếu đều phải tuân thủ quy tắc làm tròn theo bước nhảy cấu hình (5, 10 hoặc 15 phút).
  5. *Ràng buộc chống chồng lấn suất chiếu*: Tuyệt đối không cho phép hai suất chiếu có khoảng thời gian diễn ra trùng lặp trong cùng một phòng chiếu.
  6. *Tính độc lập của các phòng chiếu*: Các phòng chiếu trong cùng một cụm rạp hoạt động hoàn toàn độc lập, không áp đặt độ trễ phòng nhân tạo, cho phép các phòng chiếu có thể khởi chiếu đồng thời vào các khung giờ vàng để phục vụ lượng khán giả đông đảo.

- **Cơ chế lập lịch tự động thông minh (Heuristic Multi-Movie Scheduling Assistant)**:
  Thay vì yêu cầu quản trị viên phải tính nhẩm và nhập liệu thủ công hàng chục suất chiếu dễ dẫn đến sai sót và xung đột thời gian, CineBook xây dựng bộ lập lịch thông minh theo phương pháp heuristic:
  - Quản trị viên chỉ cần khai báo "Chương trình chiếu mong muốn trong ngày", bao gồm: Danh sách các bộ phim cần chiếu, số lượng suất chiếu mục tiêu mong muốn cho từng bộ phim (`targetScreeningsPerDay`) và lựa chọn tập hợp các phòng chiếu tham gia phục vụ.
  - Thuật toán lập lịch sẽ tự động tính toán và tìm kiếm các vị trí thời gian trống thích hợp trên toàn bộ các phòng chiếu:
    * *Ưu tiên phân bổ đan xen (Interleaved Programming)*: Động cơ ưu tiên luân chuyển các bộ phim khác nhau giữa các phòng chiếu thay vì chiếu liên tục một bộ phim trong cùng một phòng suốt cả ngày. Cơ chế này giúp khán giả có nhiều sự lựa chọn về khung giờ cho mỗi bộ phim, đồng thời giảm thời gian phòng chiếu phải chờ đợi.
    * *Cân bằng hạn ngạch thiếu hụt (Quota Deficit Balancing)*: Thuật toán liên tục đánh giá mức độ thiếu hụt giữa số suất đã xếp và số suất mục tiêu của từng phim để ưu tiên xếp chỗ cho những bộ phim có nhu cầu lớn hoặc đang bị chậm tiến độ.
    * *Áp dụng điểm phạt mềm*: Thuật toán áp dụng các điểm phạt nếu có xu hướng xếp hai suất của cùng một phim liên tiếp trong một phòng hoặc phát sóng song song cùng một phim ở quá nhiều phòng khi chưa cần thiết.
  - *Nhận diện và phân loại thiếu hụt năng lực (Capacity Shortfall Classification)*: Khi tổng thời gian hoạt động của các phòng chiếu không đủ để đáp ứng toàn bộ số suất mong muốn, hệ thống không cố tình ép tạo lịch sai phạm mà tự động phân loại rõ nguyên nhân: do tổng số giờ mở cửa của các phòng không đủ (`INSUFFICIENT_AUDITORIUM_CAPACITY`), do vướng các suất chiếu đã có sẵn từ trước (`EXISTING_SCHEDULE_CONSTRAINT`), hay do thời lượng phim quá dài không kịp kết thúc trước giờ đóng cửa (`MOVIE_DURATION_CONSTRAINT`).
  - *Quy trình 3 bước chặt chẽ*: Thiết lập thông số $\rightarrow$ Xem trước trực quan lịch dự kiến (Preview) $\rightarrow$ Xác nhận sinh lịch chính thức vào cơ sở dữ liệu (Generate).
  - *Tính năng sao chép lịch chiếu (Copy Schedule)*: Cho phép sao chép nguyên vẹn lịch chiếu của một ngày chuẩn sang các ngày khác trong tuần mà không cần cấu hình lại từ đầu.
  - *Điều chỉnh lịch chiếu trực quan trên lịch biểu*: Quản trị viên có thể xem lịch trên giao diện lịch biểu (Calendar) và thực hiện điều chỉnh giờ hoặc đổi phòng chiếu. Hệ thống sẽ tự động kiểm tra lại toàn bộ các ràng buộc về xung đột phòng, giờ mở/đóng cửa và khoảng cách dọn dẹp trước khi lưu thay đổi; đặc biệt, hệ thống kiên quyết ngăn chặn việc di dời hoặc sửa đổi các suất chiếu đã phát sinh đơn đặt vé của khách hàng nhằm bảo vệ quyền lợi người mua vé.

### 1.2.6 Hoạt động quản lý đơn đặt vé

Hoạt động quản lý đơn đặt vé giúp nhân viên và quản lý rạp theo dõi chặt chẽ dòng tiền, quản lý trạng thái vé và kiểm soát luồng khán giả ra vào phòng chiếu:

1. **Giám sát đơn đặt vé tập trung**:
   - Quản trị viên có thể tra cứu toàn bộ danh sách đơn đặt vé trong hệ thống theo mã đơn hàng, thông tin khách hàng (tên, email, số điện thoại), tên phim, cụm rạp, phòng chiếu, trạng thái đơn hàng và khoảng thời gian giao dịch.
   - Xem chi tiết từng đơn hàng: Danh sách ghế ngồi, loại ghế, đơn giá từng ghế, tổng tiền gộp, số tiền giảm giá qua mã khuyến mãi, thời điểm thanh toán và thông tin giao dịch ngân hàng liên quan.
2. **Vé điện tử và cơ chế Soát vé một chạm (QR Code Check-in)**:
   - *Cơ chế mã soát vé duy nhất*: Để khắc phục sự bất tiện của các hệ thống cũ vốn tạo ra từng mã QR riêng lẻ cho mỗi ghế ngồi khiến nhân viên soát vé phải quét nhiều lần gây tắc nghẽn lối vào, CineBook sinh ra một mã soát vé duy nhất (`checkInCode`) gắn liền với đơn hàng và mã hóa thành một mã QR đại diện cho toàn bộ các vé trong đơn hàng đó.
   - *Quy trình soát vé tại cửa phòng chiếu*: Khán giả chỉ cần xuất trình một mã QR duy nhất trên điện thoại hoặc bản in vé. Nhân viên soát vé sử dụng thiết bị quét để xác thực. Hệ thống tức thì hiển thị thông tin suất chiếu, phòng chiếu và toàn bộ các vị trí ghế tương ứng.
   - *Kiểm soát đồng thời và chống soát vé trùng lặp*: Thao tác xác nhận check-in sẽ chuyển toàn bộ các vé thuộc đơn hàng sang trạng thái đã sử dụng (`USED`). Giao dịch check-in được bảo vệ bằng khóa bi quan để triệt tiêu nguy cơ hai nhân viên cùng quét một mã vé tại cùng một thời điểm. Hệ thống kiên quyết từ chối soát vé đối với các đơn hàng chưa thanh toán, đơn đã bị hủy, đơn đã hoàn tiền hoặc vé đã được quét vào rạp từ trước đó.
3. **Quản lý vòng đời đơn hàng, hủy bỏ và hoàn tiền**:
   - Vòng đời đơn đặt vé trải qua các trạng thái: Chờ thanh toán (`PENDING_PAYMENT`), Đã thanh toán (`PAID`), Đã hủy (`CANCELLED`), Hết hạn (`EXPIRED`) và Đã hoàn tiền (`REFUNDED`).
   - Khi một đơn hàng `PENDING_PAYMENT` bị khách hàng hủy bỏ hoặc tự động hết hạn sau 5 phút giữ ghế, hệ thống sẽ thực hiện đồng thời các tác vụ: Giải phóng ghế trong `seat_holds`, hủy bỏ phiên thanh toán chờ, hoàn trả lượt sử dụng của mã khuyến mãi (nếu có), đồng thời lưu vết các ghế đã giữ thành các bản ghi vé hủy (`CANCELLED`) để phục vụ công tác đối soát lịch sử mà không làm mất dữ liệu.
   - *Chính sách hoàn tiền có điều kiện*: Khách hàng hoặc quản trị viên có thể thực hiện hoàn tiền đối với các đơn hàng đã thanh toán thành công (`PAID`) với điều kiện bắt buộc: Suất chiếu chưa bắt đầu diễn ra và toàn bộ các vé trong đơn hàng chưa từng thực hiện check-in vào rạp. Khi hoàn tiền thành công, trạng thái đơn chuyển sang `REFUNDED`, toàn bộ vé chuyển sang trạng thái hủy và ghế được giải phóng để bán lại.

### 1.2.7 Hoạt động báo cáo và thống kê

Hoạt động báo cáo và thống kê cung cấp cho ban giám đốc và bộ phận quản lý rạp cái nhìn toàn diện, chuẩn xác và tức thời về hiệu quả kinh doanh và hiệu suất khai thác phòng chiếu:

1. **Phân định rõ ràng các chỉ số tài chính và vận hành**:
   Hệ thống phân tách mạch lạc giữa các khái niệm kế toán chuyên ngành, tránh nhầm lẫn giữa doanh thu lý thuyết và doanh thu thực nhận:
   - **Doanh thu gộp (Gross Revenue)**: Tổng số tiền thu được từ toàn bộ các đơn đặt vé đã thanh toán thành công ban đầu.
   - **Tiền hoàn trả (Refund Amount)**: Tổng số tiền mà rạp đã hoàn trả lại cho khách hàng từ các giao dịch hủy vé hợp lệ.
   - **Doanh thu thuần (Net Revenue)**: Doanh thu thực tế mà rạp nhận được sau khi khấu trừ các khoản hoàn trả:
     $$\text{Doanh thu thuần} = \text{Doanh thu gộp} - \text{Tiền hoàn trả}$$
   - **Tổng số vé bán ra (Gross Tickets Sold)**: Tổng số lượng vé đã phát hành từ các đơn hàng thanh toán thành công.
   - **Số vé hoàn trả (Refunded Tickets)**: Tổng số lượng vé đã bị hủy bỏ do khách hàng hoàn tiền.
   - **Số vé thực bán (Net Tickets Sold)**: Số lượng vé thực tế mang lại doanh thu:
     $$\text{Số vé thực bán} = \text{Tổng số vé bán ra} - \text{Số vé hoàn trả}$$
   - **Tỷ lệ lấp đầy phòng chiếu (Occupancy Rate)**: Chỉ số đo lường hiệu quả khai thác ghế của các suất chiếu đã diễn ra:
     $$\text{Tỷ lệ lấp đầy} = \frac{\text{Tổng số vé thực bán}}{\text{Tổng số ghế khả dụng của các suất chiếu}} \times 100\%$$
2. **Hệ thống báo cáo đa chiều**:
   - *Báo cáo theo chuỗi thời gian*: Theo dõi biến động doanh thu thuần và số lượng vé bán theo ngày, theo tháng hoặc theo năm thông qua biểu đồ tương tác trục kép (Dual-Axis Chart).
   - *Báo cáo hiệu suất phim*: Bảng xếp hạng các bộ phim ăn khách nhất theo doanh thu, số vé bán ra và tỷ lệ lấp đầy phòng chiếu trung bình, giúp ban quản lý nhanh chóng đưa ra quyết định tăng hoặc giảm số suất chiếu cho từng phim.
   - *Báo cáo doanh thu theo cụm rạp*: So sánh hiệu quả kinh doanh giữa các chi nhánh rạp trong hệ thống.
   - *Báo cáo cơ cấu trạng thái đơn hàng*: Thống kê tỷ lệ các đơn hàng thành công, đơn hết hạn, đơn bị hủy và đơn hoàn tiền nhằm đánh giá hành vi mua sắm của khách hàng.
   - *Xuất dữ liệu báo cáo*: Hỗ trợ xuất dữ liệu báo cáo chi tiết ra các định dạng chuẩn như Excel (.xlsx) và CSV phục vụ công tác kế toán và lưu trữ nội bộ.

---

## 1.3 Các yêu cầu chức năng

### 1.3.1 Quản lý tài khoản người dùng
- **Đăng ký tài khoản**: Cho phép khách hàng tạo tài khoản mới bằng họ tên, email, số điện thoại và mật khẩu; kiểm tra tính hợp lệ và tính duy nhất của email.
- **Đăng nhập hệ thống**: Xác thực thông tin đăng nhập của người dùng, cấp phát cặp mã thông báo JWT (Access Token và Refresh Token) bảo mật.
- **Tự động làm mới phiên làm việc**: Hỗ trợ cơ chế tự động gửi Refresh Token để làm mới Access Token khi token cũ hết hạn mà không gián đoạn thao tác người dùng.
- **Đăng xuất tài khoản**: Thu hồi phiên làm việc, xóa bỏ token trên client và vô hiệu hóa Refresh Token trong cơ sở dữ liệu.
- **Quản lý hồ sơ cá nhân**: Cho phép người dùng xem và cập nhật thông tin cá nhân (họ tên, số điện thoại, ảnh đại diện).
- **Đổi mật khẩu**: Cho phép người dùng đang đăng nhập đổi mật khẩu mới sau khi xác thực mật khẩu hiện tại.
- **Khôi phục mật khẩu**: Hỗ trợ quy trình gửi mã xác thực hoặc liên kết đặt lại mật khẩu qua thư điện tử khi người dùng quên mật khẩu.

### 1.3.2 Tra cứu phim và lịch chiếu
- **Xem danh sách phim**: Hiển thị danh mục phim đang chiếu (`NOW_SHOWING`) và phim sắp chiếu (`COMING_SOON`) kèm áp phích, tên phim, thời lượng, thể loại và giới hạn độ tuổi.
- **Tìm kiếm và lọc phim**: Hỗ trợ tìm kiếm phim theo từ khóa tên phim, lọc theo danh mục thể loại, định dạng chiếu và độ tuổi kiểm duyệt.
- **Xem chi tiết phim**: Cung cấp đầy đủ thông tin mô tả chi tiết của phim, bao gồm nội dung tóm tắt, đạo diễn, diễn viên, ngày khởi chiếu và liên kết xem video trailer chính thức.
- **Xem lịch chiếu theo cụm rạp**: Cho phép chọn một cụm rạp để xem toàn bộ danh sách các bộ phim và các khung giờ chiếu khả dụng trong ngày được chọn.
- **Xem lịch chiếu theo phim**: Cho phép chọn một bộ phim để xem toàn bộ các cụm rạp, phòng chiếu và khung giờ có chiếu bộ phim đó theo ngày.
- **Lọc lịch chiếu**: Lọc các suất chiếu theo ngày xem, theo định dạng phòng chiếu (2D, 3D, IMAX) và ngôn ngữ (phụ đề, lồng tiếng).

### 1.3.3 Đặt vé xem phim
- **Xem sơ đồ phòng chiếu theo thời gian thực**: Hiển thị trực quan ma trận ghế ngồi của phòng chiếu tương ứng với suất chiếu đã chọn, thể hiện rõ các loại ghế (Thường, VIP, Đôi) bằng màu sắc và biểu tượng đặc trưng.
- **Hiển thị trạng thái ghế chính xác**: Phân biệt rõ ràng giữa các ghế trống đang khả dụng, ghế đang được người khác tạm giữ, ghế đã được bán thành công và ghế đang được chính người dùng lựa chọn.
- **Lựa chọn và hủy chọn ghế**: Cho phép người dùng nhấp chọn ghế ngồi mong muốn (tối đa không quá 8 ghế cho một đơn hàng nhằm chống đầu cơ vé).
- **Tạm giữ ghế có giới hạn thời gian**: Khi khách hàng xác nhận danh sách ghế, hệ thống tạo đơn hàng `PENDING_PAYMENT` và tạm giữ các ghế tương ứng trong đúng 5 phút, kích hoạt đồng hồ đếm ngược hiển thị trên màn hình.
- **Kiểm soát đồng thời tuyệt đối**: Áp dụng khóa bi quan và ràng buộc duy nhất tại cơ sở dữ liệu để ngăn chặn hoàn toàn tình trạng hai khách hàng cùng giữ một ghế tại cùng một thời điểm.
- **Tính toán giá vé động tự động**: Tính toán giá vé chuẩn xác tại tầng máy chủ theo công thức động (Giá gốc suất chiếu + Phụ thu loại ghế + Phụ thu ngày trong tuần + Phụ thu khung giờ); hiển thị bảng bóc tách chi tiết các khoản phụ thu cho người dùng.
- **Áp dụng mã giảm giá (Promotion)**: Cho phép nhập mã khuyến mãi, tự động kiểm tra tính hợp lệ và trừ số tiền giảm giá trực tiếp vào tổng tiền thanh toán của đơn hàng.
- **Giải phóng ghế tự động**: Tự động hủy đơn hàng và trả lại ghế về trạng thái trống khi hết thời gian đếm ngược 5 phút mà khách hàng chưa hoàn tất thanh toán.

### 1.3.4 Thanh toán
- **Tích hợp cổng thanh toán VNPay**: Tạo đường dẫn thanh toán bảo mật điều hướng người dùng sang cổng VNPay Sandbox với đầy đủ chữ ký số HMAC-SHA512.
- **Xác thực giao dịch qua IPN**: Tiếp nhận và xác minh thông báo thanh toán tức thời từ máy chủ VNPay, kiểm tra tính toàn vẹn của chữ ký số và so khớp chính xác số tiền giao dịch với tổng tiền của đơn hàng.
- **Cho phép thử lại thanh toán (Retry Payment)**: Cho phép khách hàng khởi tạo lại giao dịch thanh toán nếu lần thanh toán trước bị gián đoạn hoặc thất bại, miễn là thời hạn giữ ghế 5 phút của đơn vẫn còn hiệu lực.
- **Xác nhận thanh toán và phát hành vé**: Chuyển trạng thái đơn hàng sang `PAID`, giải phóng bản ghi giữ chỗ tạm thời và khởi tạo các bản ghi vé chính thức (`VALID`) gắn với đơn hàng.
- **Gửi thư điện tử xác nhận**: Tự động gửi email chứa hóa đơn điện tử, thông tin chi tiết các vé và mã soát vé đến địa chỉ hòm thư của khách hàng ngay sau khi thanh toán thành công.

### 1.3.5 Xem lịch sử đặt vé
- **Xem danh sách đơn hàng cá nhân**: Cho phép khách hàng đã đăng nhập xem toàn bộ lịch sử các đơn đặt vé của mình, hiển thị phân loại theo trạng thái (Đã thanh toán, Chờ thanh toán, Đã hủy, Đã hoàn tiền).
- **Xem chi tiết vé điện tử**: Hiển thị thông tin chi tiết của từng đơn hàng: Mã đơn, tên phim, cụm rạp, phòng chiếu, ngày giờ chiếu, danh sách ghế, tổng tiền đã trả và thời điểm giao dịch.
- **Hiển thị mã QR soát vé**: Cung cấp mã QR đại diện cho toàn bộ đơn đặt vé để khách hàng xuất trình khi vào rạp xem phim.
- **Yêu cầu hủy vé và hoàn tiền**: Cho phép khách hàng gửi yêu cầu hoàn tiền đối với các đơn hàng đủ điều kiện (suất chiếu chưa diễn ra và chưa từng thực hiện check-in).

### 1.3.6 Quản lý phim
- **Quản lý thông tin phim (CRUD)**: Cho phép quản trị viên thêm mới, chỉnh sửa, ẩn hoặc xóa mềm các bộ phim trong hệ thống.
- **Đồng bộ thể loại từ TMDB**: Tự động kết nối với API của The Movie Database (TMDB) để cập nhật danh mục thể loại phim chuẩn quốc tế.
- **Nhập phim tự động từ TMDB**: Cho phép quản trị viên tìm kiếm và nhập thông tin phim từ TMDB (tiêu đề, thời lượng, tóm tắt, đạo diễn, diễn viên, áp phích, ảnh nền, trailer) chỉ với mã định danh TMDB ID, giúp tiết kiệm tối đa thời gian nhập liệu.
- **Bảo toàn dữ liệu nội bộ khi tái đồng bộ (Re-import)**: Khi cập nhật lại siêu dữ liệu phim từ TMDB, hệ thống bảo lưu nguyên vẹn mã định danh nội bộ (UUID), trạng thái phát hành, trạng thái xóa mềm và các ràng buộc dữ liệu đã có.

### 1.3.7 Quản lý rạp và lịch chiếu
- **Quản lý cụm rạp và phòng chiếu**: Tạo mới, chỉnh sửa thông tin rạp chiếu (giờ mở/đóng cửa, địa chỉ) và phòng chiếu (kích thước ma trận, loại phòng).
- **Quản lý sơ đồ ghế và loại ghế**: Thiết lập cấu hình loại ghế (Thường, VIP, Đôi), mức phụ thu của từng loại ghế, màu sắc, biểu tượng và đánh dấu trạng thái hỏng hóc của từng ghế.
- **Quản lý quy tắc giá vé theo ngày và khung giờ**: Cho phép quản trị viên thiết lập mức điều chỉnh giá vé cho từng ngày trong tuần và định nghĩa các khung giờ ưu đãi hoặc giờ vàng cao điểm.
- **Tạo lịch chiếu thủ công**: Thêm mới từng suất chiếu bằng cách chọn phim, phòng chiếu, thời gian bắt đầu và giá gốc suất chiếu; hệ thống tự động tính toán thời gian kết thúc dựa trên thời lượng phim và kiểm tra nghiêm ngặt chống chồng lấn thời gian.
- **Lập lịch tự động thông minh (Multi-Movie Scheduling)**:
  - Cho phép quản trị viên thiết lập một chương trình chiếu phim đa phim trong ngày, chỉ định số suất mục tiêu cho từng phim và lựa chọn danh sách các phòng chiếu tham gia.
  - Thuật toán heuristic tự động phân bổ các suất chiếu vào các phòng theo nguyên tắc đan xen, tôn trọng thời lượng phim, khoảng thời gian chuyển tiếp dọn dẹp phòng (`turnaroundTimeMinutes`), quy tắc làm tròn mốc giờ chiếu (interval snap) và giờ đóng/mở cửa của cụm rạp.
  - Tự động phát hiện và phân loại nguyên nhân thiếu hụt năng lực phục vụ nếu rạp không đủ thời gian đáp ứng số suất mong muốn.
  - Cung cấp giao diện xem trước (Preview) bảng lịch dự kiến trước khi bấm xác nhận tạo hàng loạt vào cơ sở dữ liệu.
- **Sao chép lịch chiếu (Copy Schedule)**: Hỗ trợ tính năng sao chép toàn bộ các suất chiếu từ một ngày đã có sang một hoặc nhiều ngày tiếp theo trong tuần.
- **Quản lý lịch chiếu trên lịch biểu (Calendar View)**: Trực quan hóa lịch chiếu của toàn bộ các phòng theo dạng thời khóa biểu; hỗ trợ xem chi tiết, điều chỉnh phòng/giờ và tự động khóa không cho phép sửa đổi đối với các suất chiếu đã phát sinh vé đặt.
- **Tự động dọn dẹp trạng thái suất chiếu (Lifecycle Cleanup)**: Tự động chuyển đổi trạng thái của các suất chiếu quá hạn từ `SCHEDULED` sang `FINISHED` mà không xóa vật lý bản ghi trong cơ sở dữ liệu.

### 1.3.8 Quản lý người dùng
- **Xem danh sách người dùng**: Hiển thị danh sách toàn bộ người dùng trong hệ thống với các thông tin cơ bản, vai trò và trạng thái tài khoản.
- **Tìm kiếm và phân loại người dùng**: Tìm kiếm theo tên hoặc email, lọc người dùng theo vai trò (`CUSTOMER`, `ADMIN`) và trạng thái (`ACTIVE`, `BLOCKED`).
- **Khóa và mở khóa tài khoản**: Cho phép quản trị viên chủ động khóa quyền truy cập của các tài khoản có hành vi vi phạm hoặc mở khóa phục hồi hoạt động cho người dùng.

### 1.3.9 Báo cáo và thống kê
- **Bảng điều khiển tổng quan (Dashboard)**: Cung cấp các thẻ chỉ số KPI tổng hợp nhanh về doanh thu hôm nay, tổng số vé bán ra, số đơn đặt vé và số lượng người dùng mới trong hệ thống.
- **Thống kê doanh thu và số vé theo thời gian**: Hiển thị biểu đồ trực quan hóa doanh thu thuần và số vé thực bán theo từng ngày, từng tháng hoặc từng năm với các bộ lọc khoảng thời gian linh hoạt (7 ngày qua, 30 ngày qua, toàn thời gian).
- **Phân tích hiệu suất kinh doanh phim**: Thống kê danh sách các bộ phim có doanh thu cao nhất, số lượng vé bán ra và tỷ lệ lấp đầy phòng chiếu của từng bộ phim.
- **Thống kê theo cụm rạp**: Đánh giá doanh thu và số lượng suất chiếu phục vụ của từng cụm rạp trong hệ thống.
- **Thống kê phân bổ trạng thái đơn hàng**: Biểu đồ tròn thể hiện tỷ lệ cơ cấu giữa các đơn hàng thành công, đơn hết hạn, đơn bị hủy và đơn đã hoàn tiền.
- **Xuất báo cáo tài chính**: Hỗ trợ xuất dữ liệu thống kê ra tệp bảng tính Excel (.xlsx) hoặc CSV phục vụ công tác đối soát và lưu trữ kế toán của doanh nghiệp.
