package main

import (
	"context"
	"encoding/binary"
	"encoding/json"
	"errors"
	"io/ioutil"
	"net"
	"os"
	"path/filepath"
	"sync"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/app"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/dialog"
	"fyne.io/fyne/v2/widget"

	"github.com/cbeuw/Cloak/internal/common"

	"github.com/cbeuw/Cloak/internal/client"
	mux "github.com/cbeuw/Cloak/internal/multiplex"
)

const configFileName = "config.json"

func saveConfig(config string) error {
	configPath := filepath.Join(os.TempDir(), configFileName)
	return ioutil.WriteFile(configPath, []byte(config), 0644)
}

func loadConfig() (string, error) {
	configPath := filepath.Join(os.TempDir(), configFileName)
	data, err := ioutil.ReadFile(configPath)
	if err != nil {
		return "", err
	}
	return string(data), nil
}

func showMessage(title, message string, w fyne.Window) {
	dialog.ShowInformation(title, message, w)
}

type Server struct {
	listener net.Listener
	quit     chan interface{}
	wg       sync.WaitGroup
}

func (s *Server) Stop() {
	close(s.quit)
	s.listener.Close()
	s.wg.Wait()
}

func main() {
	a := app.New()
	w := a.NewWindow("Cloak Client")

	var UID string
	var connected bool
        var flag bool
	var connectionLock sync.Mutex

	var currentSession *mux.Session
	//var listener net.Listener
	var udpConn *net.UDPConn
	var stopChan chan struct{}

	s := &Server{
		quit: make(chan interface{}),
	}
        
        flag = false

	localHostEntry := widget.NewEntry()
	localHostEntry.SetText("127.0.0.1") 

	localPortEntry := widget.NewEntry()
	localPortEntry.SetText("1984") 

	udpEntry := widget.NewCheck("Enable UDP", func(checked bool) {})
	udpEntry.SetChecked(false) 

	configEntry := widget.NewMultiLineEntry()
	configEntry.Wrapping = fyne.TextWrapWord
	configEntry.SetMinRowsVisible(10)

	loadedConfig, err := loadConfig()
	if err != nil {
		loadedConfig = `{
            "Transport": "direct",
            "ProxyMethod": "shadowsocks",
            "EncryptionMethod": "plain",
            "UID": "YWJjMTIzIT8kKiYoKSctPUB+",
            "PublicKey": "wHpXaRMi87TYMZUsavLclgRf/lENV2jt4mJ2SJkCk1w=",
            "ServerName": "www.bing.com",
            "NumConn": 4,
            "BrowserSig": "chrome",
            "StreamTimeout": 300,
            "RemoteHost": "shadowsocks.example.com",
            "RemotePort": "443"
        }`
	}
	configEntry.SetText(loadedConfig)

	statusLabel := widget.NewLabel("Not connected")

	connectButton := widget.NewButton("Connect", func() {
		connectionLock.Lock()
		defer connectionLock.Unlock()

		//if connected {
		//	showMessage("Error", "Already connected", w)
		//	return
		//}

		showMessage("Info", "Connect button clicked", w)
		configText := configEntry.Text

		err := saveConfig(configText)
		if err != nil {
			dialog.ShowError(errors.New("Failed to save config: "+err.Error()), w)
			return
		}

		var rawConfig client.RawConfig
		err = json.Unmarshal([]byte(configText), &rawConfig)
		if err != nil {
			dialog.ShowError(errors.New("Invalid JSON input: "+err.Error()), w)
			return
		}

		rawConfig.LocalHost = localHostEntry.Text
		rawConfig.LocalPort = localPortEntry.Text
		rawConfig.UDP = udpEntry.Checked

		UID = string((rawConfig.UID)[:])

		showMessage("UID", "strUID: "+UID, w)

		localConfig, remoteConfig, authInfo, err := rawConfig.ProcessRawConfig(common.RealWorldState)
		if err != nil {
			dialog.ShowError(err, w)
			return
		}

		var adminUID []byte
		if UID != "" {
			adminUID = []byte(UID)
		}

		showMessage("AdminUID", "AdminUID: "+string(adminUID), w)

		stopChan = make(chan struct{})
		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()

		go func() {
			defer func() {
				connectionLock.Lock()
				defer connectionLock.Unlock()
				//connected = false
				//statusLabel.SetText("Not connected")
				//showMessage("Disconnected", "You have been disconnected.", w)
			}()

			var seshMaker func() *mux.Session
			d := &net.Dialer{Control: protector, KeepAlive: remoteConfig.KeepAlive}

			statusLabel.SetText("Connecting...")
                        if flag {
                                currentSession.Close()
                        }

			if adminUID != nil {
				showMessage("API Base", "API base is "+localConfig.LocalAddr, w)
				authInfo.UID = adminUID
				authInfo.SessionId = 0
				remoteConfig.NumConn = 1

				seshMaker = func() *mux.Session {
	                                if !connected {
		                                authInfo.UID = []byte("")
                                                authInfo.SessionId = 1
	                                }
                                        if connected {
                                                authInfo.UID = adminUID
                                                authInfo.SessionId = 0
                                        }
                                        currentSession = client.MakeSession(remoteConfig, authInfo, d)
					return currentSession
				}
			} else {
				var network string
				if authInfo.Unordered {
					network = "UDP"
				} else {
					network = "TCP"
				}
				showMessage("Listening", "Listening on "+network+" "+localConfig.LocalAddr+" for "+authInfo.ProxyMethod+" client", w)
				seshMaker = func() *mux.Session {
					authInfo := authInfo

					randByte := make([]byte, 1)
					common.RandRead(authInfo.WorldState.Rand, randByte)
					authInfo.MockDomain = localConfig.MockDomainList[int(randByte[0])%len(localConfig.MockDomainList)]

					quad := make([]byte, 4)
					common.RandRead(authInfo.WorldState.Rand, quad)
					authInfo.SessionId = binary.BigEndian.Uint32(quad)
					currentSession = client.MakeSession(remoteConfig, authInfo, d)
					return currentSession
				}
			}

			connectionLock.Lock()
			connected = true
                        flag = true
			statusLabel.SetText("Connected")
			connectionLock.Unlock()

			showMessage("Connected", "You are now connected.", w)

			if authInfo.Unordered {
				showMessage("UDP", "UDP", w)
				acceptor := func() (*net.UDPConn, error) {
					udpAddr, _ := net.ResolveUDPAddr("udp", localConfig.LocalAddr)
					udpConn, err = net.ListenUDP("udp", udpAddr)
					return udpConn, err
				}

				client.RouteUDP(acceptor, localConfig.Timeout, true, seshMaker)
			} else {
				showMessage("TCP", "TCP", w)
				s.listener, err = net.Listen("tcp", localConfig.LocalAddr)
				if err != nil {
					dialog.ShowError(err, w)
					return
				}
				s.wg.Add(1)

				go func() {
					select {
					case <-ctx.Done():
						return
					}
				}()

				client.RouteTCP(s.listener, localConfig.Timeout, true, seshMaker)
			}

			select {
			case <-stopChan:
				return
			case <-ctx.Done():
				return
			}
		}()
	})

	disconnectButton := widget.NewButton("Disconnect", func() {
		connectionLock.Lock()
		defer connectionLock.Unlock()

		//if !connected {
		//	showMessage("Error", "Not connected", w)
		//	return
		//}

		currentSession.Close()

		connected = false
		statusLabel.SetText("Not connected")
		showMessage("Disconnected", "You have been disconnected.", w)
	})

	form := container.NewVBox(
		widget.NewLabel("Enter JSON-config:"),
		configEntry,
		widget.NewLabel("Local Host:"),
		localHostEntry,
		widget.NewLabel("Local Port:"),
		localPortEntry,
		udpEntry,
		connectButton,        
		disconnectButton,    
		statusLabel,
	)

	w.SetContent(form)
	w.Resize(fyne.NewSize(800, 600))
	w.ShowAndRun()
}