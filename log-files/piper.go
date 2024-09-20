package client

import (
	"net"
	"sync"
	"time"
	"io"
        "os"
	"log"

	"github.com/cbeuw/Cloak/internal/common"
	mux "github.com/cbeuw/Cloak/internal/multiplex"
)

var Logging = &struct {
	Debug, Info, Warn, Err *log.Logger
}{
	Debug: log.New(io.Discard, "[DEBUG] ", log.LstdFlags),
	Info:  log.New(os.Stdout, "[INFO] ", log.LstdFlags),
	Warn:  log.New(os.Stderr, "[WARN] ", log.LstdFlags),
	Err:   log.New(os.Stderr, "[ERROR] ", log.LstdFlags),
}

func RouteUDP(bindFunc func() (*net.UDPConn, error), streamTimeout time.Duration, singleplex bool, newSeshFunc func() *mux.Session) {
	var sesh *mux.Session
	localConn, err := bindFunc()
	if err != nil {
		Logging.Err.Printf("Error: %v", err)
	}

	streams := make(map[string]*mux.Stream)
	var streamsMutex sync.Mutex

	data := make([]byte, 8192)
	for {
		i, addr, err := localConn.ReadFrom(data)
		if err != nil {
			Logging.Err.Printf("Failed to read first packet from proxy client: %v", err)
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
				Logging.Err.Printf("Failed to open stream: %v", err)
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
						Logging.Err.Printf("copying stream to proxy client: %v", err)
						break
					}
					_ = stream.SetReadDeadline(time.Now().Add(streamTimeout))

					_, err = localConn.WriteTo(buf[:n], proxyAddr)
					if err != nil {
						Logging.Err.Printf("copying stream to proxy client: %v", err)
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
			Logging.Err.Printf("copying proxy client to stream: %v", err)
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
	Logging.Info.Printf("Cloak/RouteTCP: Starting TCP route. Stream timeout: %v, Singleplex: %v", streamTimeout, singleplex)

	for {
		localConn, err := listener.Accept()
		if err != nil {
			Logging.Err.Printf("Cloak/RouteTCP: Failed to accept connection: %v", err)
			continue
		}
		Logging.Info.Printf("Cloak/RouteTCP: Accepted new connection from %v", localConn.RemoteAddr())

		if !singleplex && (sesh == nil || sesh.IsClosed()) {
			Logging.Info.Printf("Cloak/RouteTCP: Creating new session")
			sesh = newSeshFunc()
		}

		go func(sesh *mux.Session, localConn net.Conn, timeout time.Duration) {
			if singleplex {
				Logging.Info.Printf("Cloak/RouteTCP: Singleplex mode: creating new session for this connection")
				sesh = newSeshFunc()
			}

			data := make([]byte, 10240)
			Logging.Info.Printf("Cloak/RouteTCP: Setting read deadline for connection: %v", timeout)
			err := localConn.SetReadDeadline(time.Now().Add(streamTimeout))
			if err != nil {
				Logging.Err.Printf("Cloak/RouteTCP: Failed to set read deadline: %v", err)
				localConn.Close()
				return
			}

			i, err := io.ReadAtLeast(localConn, data, 1)
			if err != nil {
				Logging.Err.Printf("Cloak/RouteTCP: Failed to read first packet from proxy client: %v", err)
				localConn.Close()
				return
			}
			Logging.Info.Printf("Cloak/RouteTCP: Read %d bytes from proxy client", i)

			var zeroTime time.Time
			Logging.Info.Printf("Cloak/RouteTCP: Resetting read deadline")
			err = localConn.SetReadDeadline(zeroTime)
			if err != nil {
				Logging.Err.Printf("Cloak/RouteTCP: Failed to reset read deadline: %v", err)
			}

			stream, err := sesh.OpenStream()
			if err != nil {
				Logging.Err.Printf("Cloak/RouteTCP: Failed to open stream: %v", err)
				localConn.Close()
				if singleplex {
					sesh.Close()
				}
				return
			}
			Logging.Info.Printf("Cloak/RouteTCP: Opened new stream for session %v", sesh)

			_, err = stream.Write(data[:i])
			if err != nil {
				Logging.Err.Printf("Cloak/RouteTCP: Failed to write to stream: %v", err)
				localConn.Close()
				stream.Close()
				return
			}
			Logging.Info.Printf("Cloak/RouteTCP: Successfully wrote %d bytes to stream", i)

			go func() {
				Logging.Info.Printf("Cloak/RouteTCP: Starting to copy data from proxy client to stream")
				if _, err := common.Copy(localConn, stream); err != nil {
					Logging.Err.Printf("Cloak/RouteTCP: Error copying data from proxy client to stream: %v", err)
				} else {
					Logging.Info.Printf("Cloak/RouteTCP: Finished copying data from proxy client to stream")
				}
			}()

			Logging.Info.Printf("Cloak/RouteTCP: Starting to copy data from stream to proxy client")
			if _, err = common.Copy(stream, localConn); err != nil {
				Logging.Err.Printf("Cloak/RouteTCP: Error copying data from stream to proxy client: %v", err)
			} else {
				Logging.Info.Printf("Cloak/RouteTCP: Finished copying data from stream to proxy client")
			}
		}(sesh, localConn, streamTimeout)
	}
}