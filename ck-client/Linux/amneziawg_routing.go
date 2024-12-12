//go:build linux
// +build linux

package main

import (
	"fmt"
	"net"
	"os"
	"strings"

	"github.com/amnezia-vpn/amneziawg-go/conn"
	"github.com/amnezia-vpn/amneziawg-go/device"
	"github.com/amnezia-vpn/amneziawg-go/ipc"
	"github.com/amnezia-vpn/amneziawg-go/tun"
	"github.com/vishvananda/netlink"
	"golang.org/x/sys/unix"

	sysctl "github.com/lorenzosaino/go-sysctl"
)

type AmneziaWGTunnel struct {
	configFilePath      string
	interfaceName       string
	amneziawgDevice     *device.Device
	amneziawgUAPISocket net.Listener
	config              *Config
}

var tunnels = make(map[string]*AmneziaWGTunnel)

func (amneziawgTunnel *AmneziaWGTunnel) Close() error {
	err1 := amneziawgTunnel.amneziawgUAPISocket.Close()
	amneziawgTunnel.amneziawgDevice.Close()

	if err1 != nil {
		return err1
	} else {
		return nil
	}
}

func (amneziawgTunnel *AmneziaWGTunnel) configureInterface() error {
	uapiString, err := amneziawgTunnel.config.ToUAPI()
	if err != nil {
		return err
	}

	reader := strings.NewReader(uapiString)

	return amneziawgTunnel.amneziawgDevice.IpcSetOperation(reader)
}

func (amneziawgTunnel *AmneziaWGTunnel) setUpInterface() error {
	link, err := netlink.LinkByName(amneziawgTunnel.interfaceName)
	if err != nil {
		return err
	}

	return netlink.LinkSetUp(link)
}

func (amneziawgTunnel *AmneziaWGTunnel) addAddresses() error {
	for _, address := range amneziawgTunnel.config.Interface.Addresses {
		if err := amneziawgTunnel.addAddress(address.String()); err != nil {
			return err
		}
	}

	return nil
}

func (amneziawgTunnel *AmneziaWGTunnel) addAddress(address string) error {
	// sudo ip -4 address add <address> dev <interfaceName>
	link, err := netlink.LinkByName(amneziawgTunnel.interfaceName)
	if err != nil {
		return err
	}

	addr, err := netlink.ParseAddr(address)
	if err != nil {
		return err
	}

	return netlink.AddrAdd(link, addr)
}

func (amneziawgTunnel *AmneziaWGTunnel) addRoutes() error {
	for _, peer := range amneziawgTunnel.config.Peers {
		for _, allowed_ip := range peer.AllowedIPs {
			if err := amneziawgTunnel.addRoute(allowed_ip.String()); err != nil {
				return err
			}
		}
	}

	return nil
}

func (amneziawgTunnel *AmneziaWGTunnel) addRoute(address string) error {
	// sudo ip rule add not fwmark <table> table <table>
	ruleNot := netlink.NewRule()
	ruleNot.Invert = true
	ruleNot.Mark = uint32(amneziawgTunnel.config.Interface.FwMark)
	ruleNot.Table = int(amneziawgTunnel.config.Interface.FwMark)
	if err := netlink.RuleAdd(ruleNot); err != nil {
		return err
	}

	// sudo ip rule add table main suppress_prefixlength 0
	ruleAdd := netlink.NewRule()
	ruleAdd.Table = unix.RT_TABLE_MAIN
	ruleAdd.SuppressPrefixlen = 0
	if err := netlink.RuleAdd(ruleAdd); err != nil {
		return err
	}

	// sudo ip route add <address> dev <interfaceName> table <table>
	link, err := netlink.LinkByName(amneziawgTunnel.interfaceName)
	if err != nil {
		return err
	}

	_, dst, err := net.ParseCIDR(address)
	if err != nil {
		return err
	}

	route := netlink.Route{LinkIndex: link.Attrs().Index, Dst: dst, Table: 51820}

	if err := netlink.RouteAdd(&route); err != nil {
		return err
	}

	// sudo sysctl -q net.ipv4.conf.all.src_valid_mark=1
	if err := sysctl.Set("net.ipv4.conf.all.src_valid_mark", "1"); err != nil {
		return err
	}

	return nil
}

func NewAmneziaWGTunnel(configFilePath, interfaceName string) (*AmneziaWGTunnel, error) {
	// open TUN device (or use supplied fd)
	tunnelDevice, err := tun.CreateTUN(interfaceName, 1360)

	if err == nil {
		realInterfaceName, err2 := tunnelDevice.Name()
		if err2 == nil {
			interfaceName = realInterfaceName
		}
	} else {
		return nil, fmt.Errorf("Error creating tunnel device: %s", err)
	}

	// open UAPI file
	fileUAPI, err := ipc.UAPIOpen(interfaceName)

	if err != nil {
		return nil, fmt.Errorf("Error opening uapi for the tunnel: %s", err)
	}

	// Start device:
	// Logger definition:
	logLevel := device.LogLevelVerbose
	logger := device.NewLogger(logLevel, fmt.Sprintf("(%s) ", interfaceName))
	device := device.NewDevice(tunnelDevice, conn.NewDefaultBind(), logger)

	errs := make(chan error)
	uapi, err := ipc.UAPIListen(interfaceName, fileUAPI)
	if err != nil {
		device.Close()
		return nil, err
	}

	go func() {
		// Extra ipc configuration (for awg(8) util support):
		for {
			conn, err := uapi.Accept()
			if err != nil {
				errs <- err
				return
			}
			go device.IpcHandle(conn)
		}
	}()

	configData, err := os.ReadFile(configFilePath)
	if err != nil {
		device.Close()
		uapi.Close()
		return nil, fmt.Errorf("Failed to read config file: %s", err)
	}

	config, err := FromWgQuickWithUnknownEncoding(string(configData), interfaceName)
	if err != nil {
		device.Close()
		uapi.Close()
		return nil, fmt.Errorf("Failed to parse config file: %s", err)
	}

	return &AmneziaWGTunnel{
		configFilePath:      configFilePath,
		interfaceName:       interfaceName,
		amneziawgDevice:     device,
		amneziawgUAPISocket: uapi,
		config:              config,
	}, nil
}

func closeTunnel(interfaceName string) error {
	tun := tunnels[interfaceName]
	if tun != nil {
		delete(tunnels, interfaceName)
		return tun.Close()
	}

	return nil
}

func installTunnel(configFilePath, interfaceName string) error {
	amneziawgTunnel, err := NewAmneziaWGTunnel(configFilePath, interfaceName)
	if err != nil {
		return err
	} else {
		Logging.Info.Printf("Tunnel initialisation success")
	}

	tunnels[interfaceName] = amneziawgTunnel

	if err := amneziawgTunnel.configureInterface(); err != nil {
		closeTunnel(interfaceName)
		return err
	} else {
		Logging.Info.Printf("Config set success")
	}

	if amneziawgTunnel.setUpInterface(); err != nil {
		closeTunnel(interfaceName)
		return err
	} else {
		Logging.Info.Printf("Interface set up success")
	}

	if amneziawgTunnel.addAddresses(); err != nil {
		closeTunnel(interfaceName)
		return err
	} else {
		Logging.Info.Printf("Interface addresses addition success")
	}

	if amneziawgTunnel.addRoutes(); err != nil {
		closeTunnel(interfaceName)
		return err
	} else {
		Logging.Info.Printf("Interface routing initialisation success")
	}

	return nil
}

func uninstallTunnel(interfaceName string) error {
	link, err := netlink.LinkByName(interfaceName)
	if err != nil {
		err = netlink.LinkDel(link)
	}

	if err != nil {
		Logging.Info.Printf("Tunnel is already stopped")
	} else {
		Logging.Info.Printf("Stop tunnel: %s", interfaceName)
	}

	if err := closeTunnel(interfaceName); err != nil {
		Logging.Info.Printf("Error during interface close: %s", err)
	}

	Logging.Info.Printf("Interface closed")

	return err
}
