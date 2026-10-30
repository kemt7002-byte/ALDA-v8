# Chạy Demucs miễn phí bằng Google Colab

1. Mở notebook `ALAD_Demucs_Free_Colab.ipynb` bằng Google Colab: https://colab.research.google.com/
2. Chọn Runtime → Change runtime type → Hardware accelerator: **CPU** (miễn phí). Nếu tài khoản được cấp GPU miễn phí, có thể chọn GPU nhưng không bắt buộc.
3. Chạy lần lượt 3 ô code từ trên xuống dưới. Cho phép Colab tải và cài thư viện.
4. Ở ô cuối, chờ URL dạng `https://....trycloudflare.com`.
5. Mở URL + `/health`; chờ kết quả JSON `status: ok`.
6. Trong ALAD-Mobile → Cài đặt → Máy chủ tách giọng Demucs, dán URL gốc. URL tự lưu.
7. Giữ tab Colab mở trong suốt thời gian lồng tiếng. Nếu phiên bị ngắt, chạy lại ô 3, lấy URL mới và cập nhật trong ứng dụng.

**Không trả phí**: Colab Free và Cloudflare Quick Tunnel không yêu cầu thuê máy chủ. Tuy nhiên Google có thể giới hạn/ngắt phiên; đây không phải dịch vụ chạy 24/7. Xử lý CPU có thể chậm. Không chia sẻ URL tunnel vì endpoint không có đăng nhập.
