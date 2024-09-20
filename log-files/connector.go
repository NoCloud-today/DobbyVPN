package client

import (
	"net"
	"sync"
	"sync/atomic"
	"time"

	"github.com/cbeuw/Cloak/internal/common"
	mux "github.com/cbeuw/Cloak/internal/multiplex"
	//logging "github.com/sirupsen/logrus"
)

func MakeSession(connConfig RemoteConnConfig, authInfo AuthInfo, dialer common.Dialer) *mux.Session {
	logging.Info.Printf("Cloak/MakeSession: Start function MakeSession(connConfig RemoteConnConfig, authInfo AuthInfo, dialer common.Dialer) *mux.Session")
	logging.Info.Printf("Cloak/MakeSession: Starting the process to create a new session")
	logging.Info.Printf("Cloak/MakeSession: Configuration - RemoteAddr: %s, NumConn: %d, Singleplex: %v", connConfig.RemoteAddr, connConfig.NumConn, connConfig.Singleplex)

	connsCh := make(chan net.Conn, connConfig.NumConn)
	var _sessionKey atomic.Value
	var wg sync.WaitGroup

	logging.Info.Printf("Cloak/MakeSession: Attempting to establish %d connections to remote address: %s", connConfig.NumConn, connConfig.RemoteAddr)

	logging.Info.Printf("Cloak/MakeSession: Enter the cycle: for i := 0; i < connConfig.NumConn; i++ {")
	for i := 0; i < connConfig.NumConn; i++ {
		wg.Add(1)
		go func(connIndex int) {
			logging.Info.Printf("Cloak/MakeSession: Starting connection #%d", connIndex+1)
			logging.Info.Printf("Cloak/MakeSession: Enter the control point makeconn")
		makeconn:
			logging.Info.Printf("Cloak/MakeSession: new Dialer: remoteConn, err := dialer.Dial(tcp, connConfig.RemoteAddr)")
			remoteConn, err := dialer.Dial("tcp", connConfig.RemoteAddr)
			if err != nil {
				logging.Info.Printf("Cloak/MakeSession: Failed to establish connection #%d to remote: %v", connIndex+1, err)
				time.Sleep(time.Second * 3)
				goto makeconn
			}
			logging.Info.Printf("Cloak/MakeSession: Connection #%d established to remote address", connIndex+1)

			transportConn := connConfig.TransportMaker()
			logging.Info.Printf("Cloak/MakeSession: Create transport: transportConn := connConfig.TransportMaker()")
			sk, err := transportConn.Handshake(remoteConn, authInfo)
			if err != nil {
				logging.Info.Printf("Cloak/MakeSession: Failed handshake for connection #%d: %v", connIndex+1, err)
				transportConn.Close()
				time.Sleep(time.Second * 3)
				goto makeconn
			}
			logging.Info.Printf("Cloak/MakeSession: Handshake completed for connection #%d", connIndex+1)

			_sessionKey.Store(sk)
			connsCh <- transportConn
			logging.Info.Printf("Cloak/MakeSession: Connection #%d is fully prepared and added to session", connIndex+1)
			wg.Done()
		}(i)
	}

	wg.Wait()
	logging.Info.Printf("Cloak/MakeSession: All connections have been successfully established")

	sessionKey := _sessionKey.Load().([32]byte)
	logging.Info.Printf("Cloak/MakeSession: Session key successfully loaded")

	obfuscator, err := mux.MakeObfuscator(authInfo.EncryptionMethod, sessionKey)
	if err != nil {
		logging.Info.Printf("Cloak/MakeSession: Failed to create obfuscator: %v", err)
	}

	logging.Info.Printf("Cloak/MakeSession: Obfuscator created successfully")

	seshConfig := mux.SessionConfig{
		Singleplex:         connConfig.Singleplex,
		Obfuscator:         obfuscator,
		Valve:              nil,
		Unordered:          authInfo.Unordered,
		MsgOnWireSizeLimit: appDataMaxLength,
	}

	logging.Info.Printf("Cloak/MakeSession: Session configuration - Singleplex: %v, Unordered: %v", connConfig.Singleplex, authInfo.Unordered)

	sesh := mux.MakeSession(authInfo.SessionId, seshConfig)
	logging.Info.Printf("Cloak/MakeSession: Session created with ID: %v", authInfo.SessionId)

	for i := 0; i < connConfig.NumConn; i++ {
		conn := <-connsCh
		sesh.AddConnection(conn)
		logging.Info.Printf("Cloak/MakeSession: Connection #%d added to session", i+1)
	}

	logging.Info.Printf("Cloak/MakeSession: Session %v established successfully", authInfo.SessionId)
	return sesh
}
