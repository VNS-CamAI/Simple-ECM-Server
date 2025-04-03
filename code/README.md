# Hướng dẫn cài đặt
## Build service:
`mvn clean install`

## Cài đặt
1. Copy các file jar, application.yml, logback-spring.xml vào cùng một thư mục
2. Chỉnh sửa lại đừng dẫn các file trên cho đúng trong file ecm.service
3. Chỉnh sửa lại cấu hình database, thư mục uploads, và các cấu hình khác trong file application.yml theo tài liệu mô tả
4. Chạy lệnh sql trong file `resources/db/migration/V1__Create_FileECM_Table.sql`
5. Copy file ecm.service vào thư mục `/etc/systemd/system`
6. Chạy các lệnh sau:
   `systemctl daemon-reload`
   `systemctl enable ecm.service`
   `systemctl restart ecm.service`
7. Kiểm tra file logs/application.log trong thư mục lưu file jar để xem service đã chạy hay chưa
