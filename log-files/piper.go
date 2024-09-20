package client

import (
	"io"
	"net"
	"sync"
	"time"

	"github.com/cbeuw/Cloak/internal/common"

	mux "github.com/cbeuw/Cloak/internal/multiplex"
	log "github.com/sirupsen/logrus"
)

func RouteUDP(bindFunc func() (*net.UDPConn, error), streamTimeout time.Duration, singleplex bool, newSeshFunc func() *mux.Session) {
	var sesh *mux.Session
	localConn, err := bindFunc()
	if err != nil {
		log.Fatal(err)
	}

	streams := make(map[string]*mux.Stream)
	var streamsMutex sync.Mutex

	data := make([]byte, 8192)
	for {
		i, addr, err := localConn.ReadFrom(data)
		if err != nil {
			log.Errorf("Failed to read first packet from proxy client: %v", err)
			continue
		}

		if !singleplex && (sesh == nil || sesh.IsClosed()) {
			sesh = newSeshFunc()
		}

		streamsMutex.Lock()
		stream, ok := streams[addr.String()]
		if !ok {
			if singleplex {
				sesh = newSeshFunc()
			}

			stream, err = sesh.OpenStream()
			if err != nil {
				if singleplex {
					sesh.Close()
				}
				log.Errorf("Failed to open stream: %v", err)
				streamsMutex.Unlock()
				continue
			}
			streams[addr.String()] = stream
			streamsMutex.Unlock()

			_ = stream.SetReadDeadline(time.Now().Add(streamTimeout))

			proxyAddr := addr
			go func(stream *mux.Stream, localConn *net.UDPConn) {
				buf := make([]byte, 8192)
				for {
					n, err := stream.Read(buf)
					if err != nil {
						log.Tracef("copying stream to proxy client: %v", err)
						break
					}
					_ = stream.SetReadDeadline(time.Now().Add(streamTimeout))

					_, err = localConn.WriteTo(buf[:n], proxyAddr)
					if err != nil {
						log.Tracef("copying stream to proxy client: %v", err)
						break
					}
				}
				streamsMutex.Lock()
				delete(streams, addr.String())
				streamsMutex.Unlock()
				stream.Close()
				return
			}(stream, localConn)
		} else {
			streamsMutex.Unlock()
		}

		_, err = stream.Write(data[:i])
		if err != nil {
			log.Tracef("copying proxy client to stream: %v", err)
			streamsMutex.Lock()
			delete(streams, addr.String())
			streamsMutex.Unlock()
			stream.Close()
			continue
		}
		_ = stream.SetReadDeadline(time.Now().Add(streamTimeout))
	}
}

func RouteTCP(listener net.Listener, streamTimeout time.Duration, singleplex bool, newSeshFunc func() *mux.Session) {
	var sesh *mux.Session
	log.Printf("Cloak/RouteTCP: Starting TCP route. Stream timeout: %v, Singleplex: %v", streamTimeout, singleplex)

	for {
		// Принимаем новое соединение
		localConn, err := listener.Accept()
		if err != nil {
			log.Printf("Cloak/RouteTCP: Failed to accept connection: %v", err)
			continue
		}
		log.Printf("Cloak/RouteTCP: Accepted new connection from %v", localConn.RemoteAddr())

		// Создаем новую сессию, если нужно
		if !singleplex && (sesh == nil || sesh.IsClosed()) {
			log.Printf("Cloak/RouteTCP: Creating new session")
			log.Printf("Cloak/RouteTCP: sesh = newSeshFunc()")
			log.Printf("Cloak/RouteTCP: Enter the function MakeSession()")
			sesh = newSeshFunc()
		}

		// Обработка соединения в отдельной горутине
		go func(sesh *mux.Session, localConn net.Conn, timeout time.Duration) {
			// Если singleplex, создаем сессию для каждого соединения
			if singleplex {
				log.Printf("Cloak/RouteTCP: Singleplex mode: creating new session for this connection")
				log.Printf("Cloak/RouteTCP: sesh = newSeshFunc()")
				log.Printf("Cloak/RouteTCP: Enter the function MakeSession()")
				sesh = newSeshFunc()
			}

			// Логируем строковую команду для чтения данных
			data := make([]byte, 10240)
			log.Printf("Cloak/RouteTCP: Setting read deadline for connection: %v", timeout)
			err := localConn.SetReadDeadline(time.Now().Add(streamTimeout))
			if err != nil {
				log.Printf("Cloak/RouteTCP: Failed to set read deadline: %v", err)
				localConn.Close()
				return
			}

			// Чтение данных от клиента
			i, err := io.ReadAtLeast(localConn, data, 1)
			if err != nil {
				log.Printf("Cloak/RouteTCP: Failed to read first packet from proxy client: %v", err)
				localConn.Close()
				return
			}
			log.Printf("Cloak/RouteTCP: Read %d bytes from proxy client", i)

			// Сброс времени ожидания чтения
			var zeroTime time.Time
			log.Printf("Cloak/RouteTCP: Resetting read deadline")
			err = localConn.SetReadDeadline(zeroTime)
			if err != nil {
				log.Printf("Cloak/RouteTCP: Failed to reset read deadline: %v", err)
			}

			// Открытие нового потока
			stream, err := sesh.OpenStream()
			if err != nil {
				log.Printf("Cloak/RouteTCP: Failed to open stream: %v", err)
				localConn.Close()
				if singleplex {
					sesh.Close()
				}
				return
			}
			log.Printf("Cloak/RouteTCP: Opened new stream for session %v", sesh)

			// Отправка данных в поток
			_, err = stream.Write(data[:i])
			if err != nil {
				log.Printf("Cloak/RouteTCP: Failed to write to stream: %v", err)
				localConn.Close()
				stream.Close()
				return
			}
			log.Printf("Cloak/RouteTCP: Successfully wrote %d bytes to stream", i)

			// Создаем горутину для копирования данных от клиента в поток
			go func() {
				log.Printf("Cloak/RouteTCP: Starting to copy data from proxy client to stream")
				if _, err := common.Copy(localConn, stream); err != nil {
					log.Printf("Cloak/RouteTCP: Error copying data from proxy client to stream: %v", err)
				} else {
					log.Printf("Cloak/RouteTCP: Finished copying data from proxy client to stream")
				}
			}()

			// Копируем данные от потока к клиенту
			log.Printf("Cloak/RouteTCP: Starting to copy data from stream to proxy client")
			if _, err = common.Copy(stream, localConn); err != nil {
				log.Printf("Cloak/RouteTCP: Error copying data from stream to proxy client: %v", err)
			} else {
				log.Printf("Cloak/RouteTCP: Finished copying data from stream to proxy client")
			}
		}(sesh, localConn, streamTimeout)
	}
}