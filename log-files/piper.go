package client

import (
	"io"
	"net"
	"sync"
	"time"
        "os"
        "log"

	"github.com/cbeuw/Cloak/internal/common"

	mux "github.com/cbeuw/Cloak/internal/multiplex"
	//"logging"
)

var logging struct {
    Debug *log.Logger
    Info  *log.Logger
    Warn  *log.Logger
    Err   *log.Logger
}

func RouteUDP(bindFunc func() (*net.UDPConn, error), streamTimeout time.Duration, singleplex bool, newSeshFunc func() *mux.Session) {
	var sesh *mux.Session
	localConn, err := bindFunc()
	if err != nil {
		logging.Info.Printf("Error localConn")
	}

	streams := make(map[string]*mux.Stream)
	var streamsMutex sync.Mutex

	data := make([]byte, 8192)
	for {
		i, addr, err := localConn.ReadFrom(data)
		if err != nil {
			logging.Info.Printf("Failed to read first packet from proxy client: %v", err)
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
				logging.Info.Printf("Failed to open stream: %v", err)
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
						logging.Info.Printf("copying stream to proxy client: %v", err)
						break
					}
					_ = stream.SetReadDeadline(time.Now().Add(streamTimeout))

					_, err = localConn.WriteTo(buf[:n], proxyAddr)
					if err != nil {
						logging.Info.Printf("copying stream to proxy client: error")
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
			logging.Info.Printf("copying proxy client to stream: error")
			streamsMutex.Lock()
			delete(streams, addr.String())
			streamsMutex.Unlock()
			stream.Close()
			continue
		}
		_ = stream.SetReadDeadline(time.Now().Add(streamTimeout))
	}
}

func RouteTCP(listener net.Listener, streamTimeout time.Duration, singleplex bool, newSeshFunc func() *mux.Session, logging *struct {
	Debug, Info, Warn, Err *log.Logger
}) {
	var sesh *mux.Session
	logging.Info.Printf("Cloak/RouteTCP: Starting TCP route. Stream timeout: %v, Singleplex: %v", streamTimeout, singleplex)

	for {
		// Принимаем новое соединение
		localConn, err := listener.Accept()
		if err != nil {
			logging.Info.Printf("Cloak/RouteTCP: Failed to accept connection: %v", err)
			continue
		}
		logging.Info.Printf("Cloak/RouteTCP: Accepted new connection from %v", localConn.RemoteAddr())

		// Создаем новую сессию, если нужно
		if !singleplex && (sesh == nil || sesh.IsClosed()) {
			logging.Info.Printf("Cloak/RouteTCP: Creating new session")
			sesh = newSeshFunc()
		}

		// Обработка соединения в отдельной горутине
		go func(sesh *mux.Session, localConn net.Conn, timeout time.Duration) {
			// Если singleplex, создаем сессию для каждого соединения
			if singleplex {
				logging.Info.Printf("Cloak/RouteTCP: Singleplex mode: creating new session for this connection")
				sesh = newSeshFunc()
			}

			data := make([]byte, 10240)
			logging.Info.Printf("Cloak/RouteTCP: Setting read deadline for connection: %v", timeout)
			err := localConn.SetReadDeadline(time.Now().Add(streamTimeout))
			if err != nil {
				logging.Info.Printf("Cloak/RouteTCP: Failed to set read deadline: %v", err)
				localConn.Close()
				return
			}

			i, err := io.ReadAtLeast(localConn, data, 1)
			if err != nil {
				logging.Info.Printf("Cloak/RouteTCP: Failed to read first packet from proxy client: %v", err)
				localConn.Close()
				return
			}
			logging.Info.Printf("Cloak/RouteTCP: Read %d bytes from proxy client", i)

			var zeroTime time.Time
			logging.Info.Printf("Cloak/RouteTCP: Resetting read deadline")
			err = localConn.SetReadDeadline(zeroTime)
			if err != nil {
				logging.Info.Printf("Cloak/RouteTCP: Failed to reset read deadline: %v", err)
			}

			stream, err := sesh.OpenStream()
			if err != nil {
				logging.Info.Printf("Cloak/RouteTCP: Failed to open stream: %v", err)
				localConn.Close()
				if singleplex {
					sesh.Close()
				}
				return
			}
			logging.Info.Printf("Cloak/RouteTCP: Opened new stream for session %v", sesh)

			_, err = stream.Write(data[:i])
			if err != nil {
				logging.Info.Printf("Cloak/RouteTCP: Failed to write to stream: %v", err)
				localConn.Close()
				stream.Close()
				return
			}
			logging.Info.Printf("Cloak/RouteTCP: Successfully wrote %d bytes to stream", i)

			go func() {
				logging.Info.Printf("Cloak/RouteTCP: Starting to copy data from proxy client to stream")
				if _, err := common.Copy(localConn, stream); err != nil {
					logging.Info.Printf("Cloak/RouteTCP: Error copying data from proxy client to stream: %v", err)
				} else {
					logging.Info.Printf("Cloak/RouteTCP: Finished copying data from proxy client to stream")
				}
			}()

			logging.Info.Printf("Cloak/RouteTCP: Starting to copy data from stream to proxy client")
			if _, err = common.Copy(stream, localConn); err != nil {
				logging.Info.Printf("Cloak/RouteTCP: Error copying data from stream to proxy client: %v", err)
			} else {
				logging.Info.Printf("Cloak/RouteTCP: Finished copying data from stream to proxy client")
			}
		}(sesh, localConn, streamTimeout)
	}
}