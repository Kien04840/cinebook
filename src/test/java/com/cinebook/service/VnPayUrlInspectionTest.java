package com.cinebook.service;

import com.cinebook.config.VnPayConfig;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Payment;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.service.impl.VnPayServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

public class VnPayUrlInspectionTest {

    @Test
    void inspectVariations() throws Exception {
        // Test 1: Amount nhỏ (10,000 VND = 1,000,000)
        System.out.println("\n--- VAR 1: Small Amount 10,000 VND ---");
        testCustom("1000000", "127.0.0.1", "http://localhost:5173/payment/result", "Thanh toan don hang");

        // Test 2: IP công cộng (Public IP)
        System.out.println("\n--- VAR 2: Public IP 118.69.182.10 ---");
        testCustom("15000000", "118.69.182.10", "http://localhost:5173/payment/result", "Thanh toan don hang");

        // Test 3: HTTPS Return URL
        System.out.println("\n--- VAR 3: HTTPS Return URL ---");
        testCustom("15000000", "127.0.0.1", "https://cinebook.vn/payment/result", "Thanh toan don hang");

        // Test 4: OrderInfo đơn giản không dấu không ký tự đặc biệt
        System.out.println("\n--- VAR 4: Simple OrderInfo ---");
        testCustom("15000000", "127.0.0.1", "http://localhost:5173/payment/result", "ThanhToan");
    }

    void testCustom(String amount, String ip, String returnUrl, String orderInfo) throws Exception {
        VnPayConfig config = new VnPayConfig();
        config.setTmnCode("E872P58R");
        config.setHashSecret("PNYEKIB5B44FBFT7ZLVD543V1D4LCPRM");
        config.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        config.setReturnUrl(returnUrl);
        config.setVersion("2.1.0");
        config.setCommand("pay");
        config.setOrderType("other");

        Payment payment = new Payment();
        payment.setPaymentCode("PAY-" + System.currentTimeMillis());

        Map<String, String> vnpParams = new java.util.HashMap<>();
        vnpParams.put("vnp_Version", config.getVersion());
        vnpParams.put("vnp_Command", config.getCommand());
        vnpParams.put("vnp_TmnCode", config.getTmnCode());
        vnpParams.put("vnp_Amount", amount);
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", payment.getPaymentCode());
        vnpParams.put("vnp_OrderInfo", orderInfo);
        vnpParams.put("vnp_OrderType", config.getOrderType());
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", returnUrl);
        vnpParams.put("vnp_IpAddr", ip);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        vnpParams.put("vnp_CreateDate", now.format(formatter));
        vnpParams.put("vnp_ExpireDate", now.plusMinutes(15).format(formatter));

        java.util.List<String> fieldNames = new java.util.ArrayList<>(vnpParams.keySet());
        java.util.Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isBlank()) {
                String encodedKey = java.net.URLEncoder.encode(fieldName, java.nio.charset.StandardCharsets.UTF_8);
                String encodedValue = java.net.URLEncoder.encode(fieldValue, java.nio.charset.StandardCharsets.UTF_8);
                hashData.append(encodedKey).append('=').append(encodedValue).append('&');
                query.append(encodedKey).append('=').append(encodedValue).append('&');
            }
        }
        if (hashData.length() > 0) hashData.setLength(hashData.length() - 1);
        if (query.length() > 0) query.setLength(query.length() - 1);

        VnPayServiceImpl service = new VnPayServiceImpl(config);
        String secureHash = service.calculateHmacSha512(vnpParams, config.getHashSecret());
        query.append("&vnp_SecureHash=").append(secureHash);

        String paymentUrl = config.getPaymentUrl() + "?" + query;

        javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
        };
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        java.net.CookieManager cookieManager = new java.net.CookieManager();
        java.net.CookieHandler.setDefault(cookieManager);

        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(paymentUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Final URI: " + response.uri());
        String body = response.body();
        if (body != null) {
            for (String line : body.split("\n")) {
                if (line.contains("fz-h3") || line.contains("code")) {
                    if (line.trim().length() < 200) {
                        System.out.println("  " + line.trim());
                    }
                }
            }
        }
    }
}


