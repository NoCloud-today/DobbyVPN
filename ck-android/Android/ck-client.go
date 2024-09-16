//go:build go1.11
// +build go1.11

package cloak_outline

import (
	"encoding/binary"
	"encoding/json"
	"net"

	"github.com/cbeuw/Cloak/internal/client"
	"github.com/cbeuw/Cloak/internal/common"
	mux "github.com/cbeuw/Cloak/internal/multiplex"
	log "github.com/sirupsen/logrus"
)

var connected bool
var currentSession *mux.Session

func StartCloakClient(localHost, localPort, config string, udp bool) {
	var UID string

	// Инициализация логирования
	log_init()

	log.SetFormatter(&log.TextFormatter{
		FullTimestamp: true,
	})
	log.SetLevel(log.InfoLevel)

	connected = true
	log.Printf("Cloak/ck-client.go: connected set to %v", connected)

	var rawConfig client.RawConfig
	err := json.Unmarshal([]byte(config), &rawConfig)
	if err != nil {
		log.Printf("Cloak/ck-client.go: Failed to unmarshal config - %v", err)
		return
	}
	log.Printf("Cloak/ck-client.go: rawConfig parsed successfully: %+v", rawConfig)

	rawConfig.LocalHost = localHost
	rawConfig.LocalPort = localPort
	rawConfig.UDP = udp
	log.Printf("Cloak/ck-client.go: rawConfig updated with LocalHost=%s, LocalPort=%s, UDP=%v", localHost, localPort, udp)

	UID = string((rawConfig.UID)[:])
	log.Printf("Cloak/ck-client.go: UID extracted: %s", UID)

	localConfig, remoteConfig, authInfo, err := rawConfig.ProcessRawConfig(common.RealWorldState)
	if err != nil {
		log.Fatal(err)
	}
	log.Printf("Cloak/ck-client.go: localConfig=%+v, remoteConfig=%+v, authInfo=%+v", localConfig, remoteConfig, authInfo)

	var adminUID []byte
	if UID != "" {
		adminUID = []byte(UID)
		log.Printf("Cloak/ck-client.go: adminUID set to %s", adminUID)
	}

	var seshMaker func() *mux.Session

	d := &net.Dialer{Control: protector, KeepAlive: remoteConfig.KeepAlive}
	log.Printf("Cloak/ck-client.go: Dialer created with KeepAlive=%v", remoteConfig.KeepAlive)

	if adminUID != nil {
		log.Infof("Cloak/ck-client.go: API base is %v", localConfig.LocalAddr)
		authInfo.UID = adminUID
		authInfo.SessionId = 0
		remoteConfig.NumConn = 1
		log.Printf("Cloak/ck-client.go: AuthInfo updated with adminUID, SessionId=%d", authInfo.SessionId)

		seshMaker = func() *mux.Session {
			if !connected {
				authInfo.UID = []byte("")
				authInfo.SessionId = 1
				log.Printf("Cloak/ck-client.go: Disconnected, resetting authInfo: SessionId=%d", authInfo.SessionId)
			}
			if connected {
				authInfo.UID = adminUID
				authInfo.SessionId = 0
				log.Printf("Cloak/ck-client.go: Connected, setting adminUID: SessionId=%d", authInfo.SessionId)
			}
			currentSession = client.MakeSession(remoteConfig, authInfo, d)
			log.Printf("Cloak/ck-client.go: Current session created: %+v", currentSession)
			return currentSession
		}
	} else {
		var network string
		if authInfo.Unordered {
			network = "UDP"
		} else {
			network = "TCP"
		}
		log.Infof("Cloak/ck-client.go: Listening on %v %v for %v client", network, localConfig.LocalAddr, authInfo.ProxyMethod)

		seshMaker = func() *mux.Session {
			authInfo := authInfo // копируем структуру, так как переписываем SessionId

			randByte := make([]byte, 1)
			common.RandRead(authInfo.WorldState.Rand, randByte)
			authInfo.MockDomain = localConfig.MockDomainList[int(randByte[0])%len(localConfig.MockDomainList)]
			log.Printf("Cloak/ck-client.go: authInfo updated with MockDomain=%s", authInfo.MockDomain)

			quad := make([]byte, 4)
			common.RandRead(authInfo.WorldState.Rand, quad)
			authInfo.SessionId = binary.BigEndian.Uint32(quad)
			log.Printf("Cloak/ck-client.go: SessionId generated: %d", authInfo.SessionId)

			currentSession = client.MakeSession(remoteConfig, authInfo, d)
			log.Printf("Cloak/ck-client.go: Current session created: %+v", currentSession)
			return currentSession
		}
	}

	if authInfo.Unordered {
		acceptor := func() (*net.UDPConn, error) {
			udpAddr, _ := net.ResolveUDPAddr("udp", localConfig.LocalAddr)
			return net.ListenUDP("udp", udpAddr)
		}

		client.RouteUDP(acceptor, localConfig.Timeout, remoteConfig.Singleplex, seshMaker)
		log.Printf("Cloak/ck-client.go: Routing UDP traffic with timeout %v", localConfig.Timeout)
	} else {
		listener, err := net.Listen("tcp", localConfig.LocalAddr)
		if err != nil {
			log.Fatal(err)
		}
		log.Printf("Cloak/ck-client.go: TCP listener created on %v", localConfig.LocalAddr)
		log.Printf("Cloak/ck-client.go: Enter the RouteTCP(listener, localConfig.Timeout, true, seshMaker)")
		client.RouteTCP(listener, localConfig.Timeout, remoteConfig.Singleplex, seshMaker)
		log.Printf("Cloak/ck-client.go: Routing TCP traffic with timeout %v", localConfig.Timeout)
	}
}

func StartAgain() {
	connected = true
	log.Printf("Cloak/ck-client.go: connected set to %v in StartAgain", connected)
}

func StopCloak() {
	if currentSession != nil {
		currentSession.Close()
		connected = false
		log.Printf("Cloak/ck-client.go: Session closed, connected set to %v", connected)
	}
}