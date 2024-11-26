```
Для запуска VPN нужно собрать две библиотеки:

Outline-sdk:
cd ck-client-desktop-kotlin/Client/outline-sdk/x/examples/outline-cli

for Linux:
GOOS=linux GOARCH=amd64 go build -buildmode=c-shared -o liboutline_linux.so main.go app.go app_linux.go routing_linux.go tun_device_linux.go outline_packet_proxy.go outline_device.go dns_linux.go ipv6_linux.go

for Windows:
$env:GOOS="windows"
$env:GOARCH="amd64"
go build -buildmode=c-shared -o liboutline_windows.dll main.go app.go app_windows.go routing_windows.go tun_device_windows.go outline_packet_proxy.go outline_device.go





cd ck-client-desktop-kotlin/Client/Cloak/cmd/ck-client

for Linux:
GOOS=linux GOARCH=amd64 go build -buildmode=c-shared -o libcloak_linux.so ck-client.go log.go protector.go

for Windows:
$env:GOOS="windows"
$env:GOARCH="amd64"
go build -buildmode=c-shared -o libcloak_windows.dll ck-client.go log.go protector.go


потом получившиеся файлы .dll/.so (в зависимости от ОС) скопировать в ck-client-desktop-kotlin/Client/libs 

Возможные проблемы:
Если ругается на -buildmode=c-shared, то нужен компилятор для C, например, MinGW.
```