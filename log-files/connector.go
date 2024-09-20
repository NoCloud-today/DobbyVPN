package client

import (
	"net"
	"sync"
	"sync/atomic"
	"time"

	"github.com/cbeuw/Cloak/internal/common"

	mux "github.com/cbeuw/Cloak/internal/multiplex"
	log "github.com/sirupsen/logrus"
)

// On different invocations to MakeSession, authInfo.SessionId MUST be different
func MakeSession(connConfig RemoteConnConfig, authInfo AuthInfo, dialer common.Dialer) *mux.Session {
    // Логируем начало создания новой сессии
    log.Printf("Cloak/MakeSession: Start function MakeSession(connConfig RemoteConnConfig, authInfo AuthInfo, dialer common.Dialer) *mux.Session")
    log.Printf("Cloak/MakeSession: Starting the process to create a new session")
    log.Printf("Cloak/MakeSession: Configuration - RemoteAddr: %s, NumConn: %d, Singleplex: %v", connConfig.RemoteAddr, connConfig.NumConn, connConfig.Singleplex)

    // Создаем канал для хранения подключений
    connsCh := make(chan net.Conn, connConfig.NumConn)
    // Atomic.Value для хранения sessionKey, который нам потребуется позже
    var _sessionKey atomic.Value
    // Создаем WaitGroup для ожидания завершения всех горутин, отвечающих за создание подключений
    var wg sync.WaitGroup

    // Логируем количество соединений, которые мы собираемся установить
    log.Printf("Cloak/MakeSession: Attempting to establish %d connections to remote address: %s", connConfig.NumConn, connConfig.RemoteAddr)

    // Для каждого соединения запускаем отдельную горутину
    log.Printf("Cloak/MakeSession: Enter the cycle: for i := 0; i < connConfig.NumConn; i++ {")
    for i := 0; i < connConfig.NumConn; i++ {
        wg.Add(1)
        go func(connIndex int) {
            // Логируем начало создания нового соединения
            log.Printf("Cloak/MakeSession: Starting connection #%d", connIndex+1)
            log.Printf("Cloak/MakeSession: Enter the control point makeconn")
        makeconn:
            // Устанавливаем новое подключение к удаленному адресу
            log.Printf("Cloak/MakeSession: new Dialer: remoteConn, err := dialer.Dial(tcp, connConfig.RemoteAddr)")
            remoteConn, err := dialer.Dial("tcp", connConfig.RemoteAddr)
            if err != nil {
                // Логируем ошибку, если не удалось подключиться
                log.Printf("Cloak/MakeSession: Failed to establish connection #%d to remote: %v", connIndex+1, err)
                // Ждем 3 секунды перед повторной попыткой
                time.Sleep(time.Second * 3)
                // Повторяем попытку создания соединения
                goto makeconn
            }
            // Логируем успешное создание соединения
            log.Printf("Cloak/MakeSession: Connection #%d established to remote address", connIndex+1)

            // Создаем транспортное соединение, которое будет использоваться для передачи данных
            transportConn := connConfig.TransportMaker()
            log.Printf("Cloak/MakeSession: Create transport: transportConn := connConfig.TransportMaker()")
            // Проводим handshake для подготовки соединения
            log.Printf("Cloak/MakeSession: Enter the function Handshake: sk, err := transportConn.Handshake(remoteConn, authInfo)")
            sk, err := transportConn.Handshake(remoteConn, authInfo)
            if err != nil {
                // Логируем ошибку, если handshake не удался
                log.Printf("Cloak/MakeSession: Failed handshake for connection #%d: %v", connIndex+1, err)
                // Закрываем текущее соединение
                transportConn.Close()
                // Ждем 3 секунды перед повторной попыткой
                time.Sleep(time.Second * 3)
                // Повторяем создание соединения
                goto makeconn
            }
            // Логируем успешное завершение handshake
            log.Printf("Cloak/MakeSession: Handshake completed for connection #%d", connIndex+1)

            // Сохраняем sessionKey, который должен быть одинаковым для всех соединений
            _sessionKey.Store(sk)

            // Отправляем подготовленное транспортное соединение в канал
            connsCh <- transportConn

            // Логируем завершение подготовки соединения
            log.Printf("Cloak/MakeSession: Connection #%d is fully prepared and added to session", connIndex+1)

            // Уведомляем WaitGroup, что работа завершена
            wg.Done()
        }(i) // Передаем индекс соединения в горутину
    }

    // Ожидаем завершения всех горутин, которые создают подключения
    wg.Wait()
    log.Printf("Cloak/MakeSession: All connections have been successfully established")

    // Извлекаем sessionKey из atomic.Value
    sessionKey := _sessionKey.Load().([32]byte)
    log.Printf("Cloak/MakeSession: Session key successfully loaded")

    // Создаем обфускатор с использованием метода шифрования и sessionKey
    obfuscator, err := mux.MakeObfuscator(authInfo.EncryptionMethod, sessionKey)
    if err != nil {
        // Если произошла ошибка, логируем и завершаем выполнение
        log.Printf("Cloak/MakeSession: Failed to create obfuscator: %v", err)
    }

    // Логируем успешное создание обфускатора
    log.Printf("Cloak/MakeSession: Obfuscator created successfully")

    // Настраиваем параметры сессии
    seshConfig := mux.SessionConfig{
        Singleplex:         connConfig.Singleplex,
        Obfuscator:         obfuscator,
        Valve:              nil,
        Unordered:          authInfo.Unordered,
        MsgOnWireSizeLimit: appDataMaxLength,
    }
    // Логируем конфигурацию сессии
    log.Printf("Cloak/MakeSession: Session configuration - Singleplex: %v, Unordered: %v", connConfig.Singleplex, authInfo.Unordered)

    // Создаем новую сессию
    sesh := mux.MakeSession(authInfo.SessionId, seshConfig)
    log.Printf("Cloak/MakeSession: Session created with ID: %v", authInfo.SessionId)

    // Добавляем все соединения в сессию
    for i := 0; i < connConfig.NumConn; i++ {
        conn := <-connsCh
        sesh.AddConnection(conn)
        log.Printf("Cloak/MakeSession: Connection #%d added to session", i+1)
    }

    // Логируем успешное завершение создания сессии
    log.Printf("Cloak/MakeSession: Session %v established successfully", authInfo.SessionId)
    return sesh
}