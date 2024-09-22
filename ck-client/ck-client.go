package main

import (
	"fmt"
	"io"
	"log"
	"os"
	"context"
	"encoding/binary"
	"encoding/json"
	"errors"
	"io/ioutil"
	"net"
	"path/filepath"
	"sync"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/app"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/dialog"
	"fyne.io/fyne/v2/widget"

	"github.com/cbeuw/Cloak/internal/client"
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


const configFileName = "config.json"

const combinedConfigFileName = "combined_config.json"
const combinedKeyFileName = "combined_shadowsocks_key.txt"

func saveCombinedConfig(config string) error {
	configPath := filepath.Join(os.TempDir(), combinedConfigFileName)
	return ioutil.WriteFile(configPath, []byte(config), 0644)
}

func loadCombinedConfig() (string, error) {
	configPath := filepath.Join(os.TempDir(), combinedConfigFileName)
	data, err := ioutil.ReadFile(configPath)
	if err != nil {
		return "", err
	}
	return string(data), nil
}

func saveCombinedKey(key string) error {
	keyPath := filepath.Join(os.TempDir(), combinedKeyFileName)
	return ioutil.WriteFile(keyPath, []byte(key), 0644)
}

func loadCombinedKey() (string, error) {
	keyPath := filepath.Join(os.TempDir(), combinedKeyFileName)
	if _, err := os.Stat(keyPath); os.IsNotExist(err) {
		return "", nil
	}
	data, err := ioutil.ReadFile(keyPath)
	if err != nil {
		fmt.Println("Error reading combined key file:", err)
		return "", nil
	}
	return string(data), nil
}

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

func showMessage(message string) {
	Logging.Info.Printf(message)
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

const keyFileName = "shadowsocks_key.txt"

func loadKey() string {
    if _, err := os.Stat(keyFileName); os.IsNotExist(err) {  
        return ""
    }
    data, err := ioutil.ReadFile(keyFileName)
    if err != nil {
        fmt.Println("Error reading key file:", err)
        return ""
    }
    return string(data)
}

func saveKey(key string) error {
    return ioutil.WriteFile(keyFileName, []byte(key), 0644)
}

var cancelFunc context.CancelFunc
var cancelFunc1 context.CancelFunc

type LogWriter struct {
	Output *widget.Entry
}

func (w *LogWriter) Write(p []byte) (n int, err error) {
	w.Output.SetText(w.Output.Text + string(p))
	return len(p), nil
}

func main() {
	a := app.New()
	w := a.NewWindow("Cloak Client")

	logOutput := widget.NewMultiLineEntry()
	logOutput.SetMinRowsVisible(10)

	logWriter := &LogWriter{Output: logOutput}
	log.SetOutput(logWriter)
        Logging.Debug.SetOutput(logWriter)
	Logging.Info.SetOutput(logWriter)
	Logging.Warn.SetOutput(logWriter)
	Logging.Err.SetOutput(logWriter)

        tabs := container.NewAppTabs()

	var UID string
	var connected bool
        var flag bool
	var connectionLock sync.Mutex

	var currentSession *mux.Session
	var listener net.Listener
	var udpConn *net.UDPConn
	var stopChan chan struct{}
        var counter = 0

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
                defer func() {
                    if r := recover(); r != nil {
                        log.Printf("Recovered from panic in goroutine: %v", r)
                        showMessage("An error occurred while connecting")
                    }
                }()
		

		//if connected {
		//	showMessage("Error", "Already connected", w)
		//	return
		//}

		showMessage("Connect button clicked")

                stopChan = make(chan struct{})
		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()

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

		showMessage(UID)

		localConfig, remoteConfig, authInfo, err := rawConfig.ProcessRawConfig(common.RealWorldState)
		if err != nil {
			dialog.ShowError(err, w)
			return
		}

		var adminUID []byte
		if UID != "" {
			adminUID = []byte(UID)
		}

		showMessage(string(adminUID))


                log.Printf("Cloak starting")

		go func() {
			defer func() {
                                log.Printf("Cloak finish1")
                                if r := recover(); r != nil {
                                    log.Printf("Recovered from panic: %v", r)
                                    showMessage("Error: An unexpected error occurred.")
                                }
				//connected = false
				//statusLabel.SetText("Not connected")
				//showMessage("Disconnected", "You have been disconnected.", w)
			}()

			var seshMaker func() *mux.Session
			d := &net.Dialer{Control: protector, KeepAlive: remoteConfig.KeepAlive}

			statusLabel.SetText("Connecting...")
                        if flag && currentSession != nil {
                                currentSession.Close()
                        }

			if adminUID != nil {
				showMessage(localConfig.LocalAddr)
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
				showMessage("Listening on "+network+" "+localConfig.LocalAddr+" for "+authInfo.ProxyMethod+" client")
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

			showMessage("You are now connected.")

			if authInfo.Unordered {
				showMessage("UDP")
				acceptor := func() (*net.UDPConn, error) {
					udpAddr, _ := net.ResolveUDPAddr("udp", localConfig.LocalAddr)
					udpConn, err = net.ListenUDP("udp", udpAddr)
					return udpConn, err
				}

				client.RouteUDP(acceptor, localConfig.Timeout, false, seshMaker)
			} else {
				showMessage("TCP")
				listener, err = net.Listen("tcp", localConfig.LocalAddr)
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

                                log.Printf("Starting Cloak")
				client.RouteTCP(listener, localConfig.Timeout, true, seshMaker)
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
                log.Printf("Cloak finish2")

		//if !connected {
		//	showMessage("Error", "Not connected", w)
		//	return
		//}

		if flag && currentSession != nil {
                        currentSession.Close()
                }

		connected = false
		statusLabel.SetText("Not connected")
		showMessage("You have been disconnected.")
	})

	ckClientContent := container.NewVBox(
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

	ckClientTab := container.NewTabItem("ck-client", ckClientContent)
        tabs.Append(ckClientTab)

//---------------------------------------------------------------------Outline-Client-----------------------------------------------------------------------

        outlineKeyEntry := widget.NewEntry()
        outlineKeyEntry.SetPlaceHolder("Enter Shadowsocks Key")

        savedKey := loadKey()
        outlineKeyEntry.SetText(savedKey)

        outlineKeyEntry.OnChanged = func(key string) {
            if err := saveKey(key); err != nil {
                fmt.Println("Error saving key:", err)
            } else {
                fmt.Println("Key saved successfully")
            }
        }

        outlineStatusLabel := widget.NewLabel("Not connected")

        ctx, cancel := context.WithCancel(context.Background())
        cancelFunc = cancel

        outlineConnectButton := widget.NewButton("Connect", func() {
            key := outlineKeyEntry.Text
            if key == "" {
                showMessage("Please enter a valid Shadowsocks key")
                return
            }
 
            keyPtr := &key

            app := App{
		TransportConfig: keyPtr,
		RoutingConfig: &RoutingConfig{
			TunDeviceName:        "outline233",
			TunDeviceIP:          "10.233.233.1",
			TunDeviceMTU:         1500, // todo: read this from netlink
			TunGatewayCIDR:       "10.233.233.2/32",
			RoutingTableID:       233,
			RoutingTablePriority: 23333,
			DNSServerIP:          "9.9.9.9",
		},
	    }
  
            ctx, cancel = context.WithCancel(context.Background())
            cancelFunc = cancel

            go func() {
	        if err := app.Run(ctx); err != nil {
		        Logging.Err.Printf("%v\n", err)
	        }
            }()

            outlineStatusLabel.SetText("Connected")
            showMessage("Connected: You are now connected via Outline.")
        })

        outlineDisconnectButton := widget.NewButton("Disconnect", func() {
            if cancelFunc != nil {
                showMessage("Start cancel Outline")
                cancelFunc()
                showMessage("Finish cancel Outline")
            }

            outlineStatusLabel.SetText("Not connected")
            showMessage("Disconnected: You have been disconnected from Outline.")
        })

        outlineClientContent := container.NewVBox(
            widget.NewLabel("Shadowsocks Key:"),
            outlineKeyEntry,
            outlineConnectButton,
            outlineDisconnectButton,
            outlineStatusLabel,
        )
        outlineClientTab := container.NewTabItem("Outline-Client", outlineClientContent)
        tabs.Append(outlineClientTab)

//---------------------------------------------------------------------Outline-Cloak-----------------------------------------------------------------------
        combinedConfigEntry := widget.NewMultiLineEntry() 
        combinedConfigEntry.Wrapping = fyne.TextWrapWord
        combinedConfigEntry.SetMinRowsVisible(10)

        loadedCombinedConfig, err := loadCombinedConfig()
        if err != nil {
                loadedCombinedConfig = loadedConfig
        }
        combinedConfigEntry.SetText(loadedCombinedConfig)

        combinedKeyEntry := widget.NewEntry()
        combinedKeyEntry.SetPlaceHolder("Enter Shadowsocks Key")
        savedCombinedKey, err := loadCombinedKey()
        if err != nil {
        }
        combinedKeyEntry.SetText(savedCombinedKey)

        combinedKeyEntry.OnChanged = func(key string) {
                if err := saveCombinedKey(key); err != nil {
                        fmt.Println("Error saving combined key:", err)
                } else {
                        fmt.Println("Combined key saved successfully")
                }
        }

        combinedStatusLabel := widget.NewLabel("Not connected")

        ctx1, cancel1 := context.WithCancel(context.Background())
        cancelFunc1 = cancel1

        configText := combinedConfigEntry.Text
        key := combinedKeyEntry.Text

        if key == "" {
                showMessage("Error: Please enter a valid Shadowsocks key")
                return
        }

        err = saveConfig(configText)
        if err != nil {
                dialog.ShowError(errors.New("Failed to save config: "+err.Error()), w)
                return
        }

        keyPtr := &key
        app := App{
               TransportConfig: keyPtr,
                        RoutingConfig: &RoutingConfig{
                        TunDeviceName:        "outline233",
                        TunDeviceIP:          "10.233.233.1",
                        TunDeviceMTU:         1500, // todo: read this from netlink
                        TunGatewayCIDR:       "10.233.233.2/32",
                        RoutingTableID:       233,
                        RoutingTablePriority: 23333,
                        DNSServerIP:          "9.9.9.9",
                },
        }

        combinedConnectButton := widget.NewButton("Connect", func() {
                defer func() {
                        if r := recover(); r != nil {
                                log.Printf("DobbyVPN/ck-client: Recovered from panic in goroutine: %v", r)
                                showMessage("DobbyVPN/ck-client: An error occurred while connecting")
                        }
                }()

                ctx1, cancel1 = context.WithCancel(context.Background())
                cancelFunc1 = cancel1

                go func() {
                        if err := app.Run(ctx1); err != nil {
                                Logging.Err.Printf("%v\n", err)
                        }
                }()
        })
        
        combinedDisconnectButton := widget.NewButton("Disconnect", func() {
            if cancelFunc1 != nil {
                showMessage("Start cancel Combined")
                cancelFunc1()
                showMessage("Finish cancel Combined")
            }

            connected = false
            
            //if currentSession != nil {
            //        currentSession.Close()
            //}
        
            combinedStatusLabel.SetText("Not connected")
            showMessage("You have been disconnected.")
        })
        
        combinedClientContent := container.NewVBox(
            widget.NewLabel("Enter JSON-config:"),
            combinedConfigEntry,
            widget.NewLabel("Shadowsocks Key:"),
            combinedKeyEntry,
            combinedConnectButton,
            combinedDisconnectButton,
            combinedStatusLabel,
        )
        
        combinedClientTab := container.NewTabItem("Combined Client", combinedClientContent)
        tabs.Append(combinedClientTab)

        logTab := container.NewTabItem("Logs", logOutput)
        tabs.Append(logTab)

        w.SetContent(tabs)
	w.Resize(fyne.NewSize(600, 400))
	w.ShowAndRun()
}