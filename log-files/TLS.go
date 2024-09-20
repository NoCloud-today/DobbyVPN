package client

import (
	"github.com/cbeuw/Cloak/internal/common"
	utls "github.com/refraction-networking/utls"
	"net"
	"strings"
)

const appDataMaxLength = 16401

type clientHelloFields struct {
	random         []byte
	sessionId      []byte
	x25519KeyShare []byte
	serverName     string
}

type browser int

const (
	chrome = iota
	firefox
	safari
)

type DirectTLS struct {
	*common.TLSConn
	browser browser
}

var topLevelDomains = []string{"com", "net", "org", "it", "fr", "me", "ru", "cn", "es", "tr", "top", "xyz", "info"}

func randomServerName() string {
	charNum := int('z') - int('a') + 1
	size := 3 + common.RandInt(10)
	name := make([]byte, size)
	for i := range name {
		name[i] = byte(int('a') + common.RandInt(charNum))
	}
	return string(name) + "." + common.RandItem(topLevelDomains)
}

func buildClientHello(browser browser, fields clientHelloFields) ([]byte, error) {
	fakeConn := net.TCPConn{}
	var helloID utls.ClientHelloID
	switch browser {
	case chrome:
		helloID = utls.HelloChrome_Auto
	case firefox:
		helloID = utls.HelloFirefox_Auto
	case safari:
		helloID = utls.HelloSafari_Auto
	}

	uclient := utls.UClient(&fakeConn, &utls.Config{ServerName: fields.serverName}, helloID)
	if err := uclient.BuildHandshakeState(); err != nil {
		return []byte{}, err
	}
	if err := uclient.SetClientRandom(fields.random); err != nil {
		return []byte{}, err
	}

	uclient.HandshakeState.Hello.SessionId = make([]byte, 32)
	copy(uclient.HandshakeState.Hello.SessionId, fields.sessionId)

	var extIndex int
	var keyShareIndex int
	for i, ext := range uclient.Extensions {
		ext, ok := ext.(*utls.KeyShareExtension)
		if ok {
			extIndex = i
			for j, keyShare := range ext.KeyShares {
				if keyShare.Group == utls.X25519 {
					keyShareIndex = j
				}
			}
		}
	}
	copy(uclient.Extensions[extIndex].(*utls.KeyShareExtension).KeyShares[keyShareIndex].Data, fields.x25519KeyShare)

	if err := uclient.BuildHandshakeState(); err != nil {
		return []byte{}, err
	}
	return uclient.HandshakeState.Hello.Raw, nil
}

func (tls *DirectTLS) Handshake(rawConn net.Conn, authInfo AuthInfo, Logging *struct {
	Debug, Info, Warn, Err *log.Logger
}) (sessionKey [32]byte, err error) {
	Logging.Info.Printf("Cloak/Handshake: Starting TLS handshake with server %s", authInfo.MockDomain)

	payload, sharedSecret := makeAuthenticationPayload(authInfo)
	Logging.Info.Printf("Cloak/Handshake: Authentication payload generated with public key and shared secret")

	fields := clientHelloFields{
		random:         payload.randPubKey[:],
		sessionId:      payload.ciphertextWithTag[0:32],
		x25519KeyShare: payload.ciphertextWithTag[32:64],
		serverName:     authInfo.MockDomain,
	}

	if strings.EqualFold(fields.serverName, "random") {
		fields.serverName = randomServerName()
		Logging.Info.Printf("Cloak/Handshake: Using random server name: %s", fields.serverName)
	} else {
		Logging.Info.Printf("Cloak/Handshake: Using provided server name: %s", fields.serverName)
	}

	var ch []byte
	ch, err = buildClientHello(tls.browser, fields)
	if err != nil {
		Logging.Info.Printf("Cloak/Handshake: Failed to build ClientHello: %v", err)
		return
	}
	Logging.Info.Printf("Cloak/Handshake: ClientHello built successfully")

	chWithRecordLayer := common.AddRecordLayer(ch, common.Handshake, common.VersionTLS11)
	Logging.Info.Printf("Cloak/Handshake: ClientHello with record layer created")

	_, err = rawConn.Write(chWithRecordLayer)
	if err != nil {
		Logging.Info.Printf("Cloak/Handshake: Failed to send ClientHello: %v", err)
		return
	}
	Logging.Info.Printf("Cloak/Handshake: ClientHello sent successfully")

	tls.TLSConn = common.NewTLSConn(rawConn)
	Logging.Info.Printf("Cloak/Handshake: TLS connection object created")

	buf := make([]byte, 1024)
	Logging.Info.Printf("Cloak/Handshake: Waiting for ServerHello")

	_, err = tls.Read(buf)
	if err != nil {
		logging.Info.Printf("Cloak/Handshake: Failed to read ServerHello: %v", err)
		return
	}
	Logging.Info.Printf("Cloak/Handshake: ServerHello received")

	encrypted := append(buf[6:38], buf[84:116]...)
	nonce := encrypted[0:12]
	ciphertextWithTag := encrypted[12:60]
	Logging.Info.Printf("Cloak/Handshake: Encrypted data extracted from ServerHello")

	sessionKeySlice, err := common.AESGCMDecrypt(nonce, sharedSecret[:], ciphertextWithTag)
	if err != nil {
		Logging.Info.Printf("Cloak/Handshake: Failed to decrypt session key: %v", err)
		return
	}
	Logging.Info.Printf("Cloak/Handshake: Session key decrypted successfully")

	copy(sessionKey[:], sessionKeySlice)
	Logging.Info.Printf("Cloak/Handshake: Session key stored")

	for i := 0; i < 2; i++ {
		Logging.Info.Printf("Cloak/Handshake: Waiting for ChangeCipherSpec or EncryptedCert message")
		_, err = tls.Read(buf)
		if err != nil {
			Logging.Info.Printf("Cloak/Handshake: Failed to read message: %v", err)
			return
		}
		Logging.Info.Printf("Cloak/Handshake: Message received")
	}

	Logging.Info.Printf("Cloak/Handshake: TLS handshake completed successfully")
	return sessionKey, nil
}